package app.sevacenter.donation;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import app.sevacenter.donation.PaymentGateway.Credentials;
import app.sevacenter.donation.PaymentGateway.GatewayException;
import app.sevacenter.donation.PaymentGateway.Payment;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Online donations into the trust's own Razorpay account (ADR 0013). A donation reaches the
 * ledger only after the payment is verified twice: the checkout signature (HMAC with the trust's
 * secret) and the gateway's own record (captured, same order, exact amount, INR).
 */
@Service
public class OnlineDonationService {

    static final long MIN_PAISE = 100;            // Rs 1
    static final long MAX_PAISE = 10_00_000_00L;  // Rs 10,00,000
    private static final Duration RECONCILE_AFTER = Duration.ofMinutes(2);
    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final PaymentSettingsRepository settings;
    private final PaymentIntentRepository intents;
    private final DonationRepository donations;
    private final PaymentGateway gateway;
    private final SecretBox secrets;
    private final PujaSettlement pujas;
    private final Clock clock = Clock.system(DonationService.IST);

    public OnlineDonationService(PaymentSettingsRepository settings, PaymentIntentRepository intents,
                                 DonationRepository donations, PaymentGateway gateway, SecretBox secrets,
                                 @org.springframework.context.annotation.Lazy PujaSettlement pujas) {
        this.pujas = pujas;
        this.settings = settings;
        this.intents = intents;
        this.donations = donations;
        this.gateway = gateway;
        this.secrets = secrets;
    }

    // --- settings (TRUST_ADMIN) ---------------------------------------------------------------

    @Transactional
    public PaymentSettings saveSettings(String keyId, String keySecret, long staffId) {
        long tenantId = currentTenant();
        String id = keyId == null ? "" : keyId.strip();
        if (!id.matches("rzp_(test|live)_[A-Za-z0-9]{8,32}")) {
            throw new InvalidFieldException("keyId", "keyId must be a Razorpay key id (rzp_test_... or rzp_live_...)");
        }
        String secret = keySecret == null ? "" : keySecret.strip();
        if (secret.length() < 8 || secret.length() > 64) {
            throw new InvalidFieldException("keySecret", "keySecret is required");
        }
        try {
            gateway.verifyCredentials(new Credentials(id, secret));
        } catch (GatewayException e) {
            throw new InvalidFieldException("keySecret", "Razorpay did not accept this key id and secret");
        }
        PaymentSettings s = settings.findById(tenantId).orElseGet(() -> new PaymentSettings(tenantId));
        s.replace(id, secrets.seal(tenantId, PaymentSettings.SECRET_PURPOSE, secret), staffId, OffsetDateTime.now(clock));
        audit.info("event=payment_settings_saved tenant={} user={} keyId={}", tenantId, staffId, id);
        return settings.save(s);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentSettings> settings() {
        return settings.findById(currentTenant());
    }

    // --- public: order + confirm --------------------------------------------------------------

    @Transactional
    public CreatedOrder createOrder(long amountPaise, String donorName, String purpose) {
        if (amountPaise < MIN_PAISE || amountPaise > MAX_PAISE) {
            throw new InvalidFieldException("amount", "amount must be between Rs 1 and Rs 10,00,000");
        }
        String name = donorName == null ? "" : donorName.strip();
        if (name.isEmpty() || name.length() > 120) {
            throw new InvalidFieldException("donorName", "donorName is required");
        }
        long tenantId = currentTenant();
        Credentials creds = credentials(tenantId);
        String orderId;
        try {
            orderId = gateway.createOrder(creds, amountPaise, "t" + tenantId + "-" + System.nanoTime());
        } catch (GatewayException e) {
            throw new GatewayUnavailableException();
        }
        intents.save(new PaymentIntent(tenantId, orderId, amountPaise, name, blankToNull(purpose)));
        return new CreatedOrder(orderId, creds.keyId(), amountPaise);
    }

    /**
     * Settles a payment the browser reports. Idempotent: confirming the same payment twice returns
     * the same donation. Any mismatch is the same generic error, so it can't be used to probe.
     */
    @Transactional
    public Settled confirm(String orderId, String paymentId, String signature) {
        long tenantId = currentTenant();
        PaymentIntent intent = intents.lockByOrderId(orderId).orElseThrow(PaymentNotVerifiedException::new);
        if (intent.isPaid()) {
            if (intent.getRazorpayPaymentId().equals(paymentId)) {
                return settledAgain(intent);
            }
            throw new PaymentNotVerifiedException();
        }
        Credentials creds = credentials(tenantId);
        if (!signatureValid(orderId, paymentId, signature, creds.keySecret())) {
            audit.warn("event=payment_signature_invalid tenant={} order={}", tenantId, orderId);
            throw new PaymentNotVerifiedException();
        }
        Payment payment;
        try {
            payment = gateway.fetchPayment(creds, paymentId);
        } catch (GatewayException e) {
            throw new GatewayUnavailableException();
        }
        return settle(intent, payment);
    }

    // --- reconciliation (TRUST_ADMIN) ---------------------------------------------------------

    /** Recovers payments whose browser closed before confirming: asks the gateway per pending order. */
    @Transactional
    public int reconcile() {
        long tenantId = currentTenant();
        Credentials creds = credentials(tenantId);
        int settled = 0;
        for (PaymentIntent pending : intents.pendingBefore(OffsetDateTime.now(clock).minus(RECONCILE_AFTER),
                PageRequest.of(0, 50))) {
            PaymentIntent intent = intents.lockByOrderId(pending.getRazorpayOrderId()).orElseThrow();
            if (intent.isPaid()) {
                continue;
            }
            List<Payment> payments;
            try {
                payments = gateway.paymentsOfOrder(creds, intent.getRazorpayOrderId());
            } catch (GatewayException e) {
                throw new GatewayUnavailableException();
            }
            for (Payment p : payments) {
                if ("captured".equals(p.status())) {
                    try {
                        settle(intent, p);
                        settled++;
                    } catch (PaymentNotVerifiedException e) {
                        // mismatched (logged in settle); leave it for a human, keep reconciling the rest
                    }
                    break;
                }
            }
        }
        audit.info("event=payments_reconciled tenant={} settled={}", tenantId, settled);
        return settled;
    }

    /**
     * Creates the gateway order for a puja booking's dakshina (ADR 0016). Same verification on
     * confirm as a donation; settling confirms the booking instead of writing the ledger.
     */
    @Transactional
    public CreatedOrder createPujaOrder(long bookingId, long amountPaise, String name, String purpose) {
        long tenantId = currentTenant();
        Credentials creds = credentials(tenantId);
        String orderId;
        try {
            orderId = gateway.createOrder(creds, amountPaise, "p" + tenantId + "-" + bookingId);
        } catch (GatewayException e) {
            throw new GatewayUnavailableException();
        }
        intents.save(PaymentIntent.forPuja(tenantId, orderId, amountPaise, name, purpose, bookingId));
        return new CreatedOrder(orderId, creds.keyId(), amountPaise);
    }

    private Settled settledAgain(PaymentIntent intent) {
        if (intent.isPuja()) {
            return Settled.puja(intent, pujas.bookingCode(intent.getPujaBookingId()));
        }
        return Settled.donation(donations.findById(intent.getDonationId()).orElseThrow());
    }

    private Settled settle(PaymentIntent intent, Payment p) {
        boolean matches = "captured".equals(p.status())
                && intent.getRazorpayOrderId().equals(p.orderId())
                && p.amountPaise() == intent.getAmountPaise()
                && "INR".equals(p.currency());
        if (!matches) {
            audit.warn("event=payment_mismatch tenant={} order={} payment={} status={}", intent.getTenantId(),
                    intent.getRazorpayOrderId(), p.id(), p.status());
            throw new PaymentNotVerifiedException();
        }
        if (intent.isPuja()) {
            String code = pujas.confirmPaid(intent.getPujaBookingId(), p.id());
            intent.markPaid(p.id(), null, OffsetDateTime.now(clock));
            audit.info("event=puja_paid tenant={} booking={} paise={} payment={}", intent.getTenantId(),
                    intent.getPujaBookingId(), intent.getAmountPaise(), p.id());
            return Settled.puja(intent, code);
        }
        Donation donation = donations.saveAndFlush(Donation.online(intent.getTenantId(), intent.getDonorName(),
                intent.getAmountPaise(), mode(p.method()), intent.getPurpose(), LocalDate.now(clock), p.id()));
        intent.markPaid(p.id(), donation.getId(), OffsetDateTime.now(clock));
        audit.info("event=online_donation tenant={} donation={} paise={} payment={}", intent.getTenantId(),
                donation.getId(), intent.getAmountPaise(), p.id());
        return Settled.donation(donation);
    }

    static boolean signatureValid(String orderId, String paymentId, String signature, String secret) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(
                    mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
            // Constant time: a timing difference must not reveal how much of a forged signature was right.
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static DonationMode mode(String method) {
        return switch (method == null ? "" : method) {
            case "upi" -> DonationMode.UPI;
            case "card" -> DonationMode.CARD;
            case "netbanking" -> DonationMode.BANK_TRANSFER;
            default -> DonationMode.WALLET;
        };
    }

    private Credentials credentials(long tenantId) {
        PaymentSettings s = settings.findById(tenantId).orElseThrow(PaymentsNotConfiguredException::new);
        return new Credentials(s.getKeyId(), secrets.open(tenantId, PaymentSettings.SECRET_PURPOSE, s.getKeySecretEnc()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    public record CreatedOrder(String orderId, String keyId, long amountPaise) { }

    /** What a verified payment settled: a ledger donation, or a puja booking. */
    public record Settled(String kind, Long donationId, String bookingCode, long amountPaise, String name, LocalDate date) {
        static Settled donation(Donation d) {
            return new Settled("DONATION", d.getId(), null, d.getAmountPaise(), d.getDonorName(), d.getReceivedOn());
        }

        static Settled puja(PaymentIntent i, String bookingCode) {
            return new Settled("PUJA", null, bookingCode, i.getAmountPaise(), i.getDonorName(),
                    LocalDate.now(DonationService.IST));
        }
    }

    /** 400, deliberately unspecific: forged, mismatched, unknown or foreign-tenant payments alike. */
    public static class PaymentNotVerifiedException extends RuntimeException { }

    /** 409: this trust hasn't connected a gateway. */
    public static class PaymentsNotConfiguredException extends RuntimeException { }

    /** 503: the gateway didn't answer; nothing was recorded. */
    public static class GatewayUnavailableException extends RuntimeException { }
}
