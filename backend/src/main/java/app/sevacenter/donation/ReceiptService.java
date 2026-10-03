package app.sevacenter.donation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import app.sevacenter.donation.DonationService.FinancialYear;
import app.sevacenter.donation.DonationService.LedgerConflictException;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 80G receipts (ADR 0012). Authorization is on the controller; tenancy is RLS. */
@Service
public class ReceiptService {

    /** Section 80G(5D): cash donations above Rs 2,000 aren't deductible. */
    static final long CASH_LIMIT_PAISE = 2_000_00;
    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final DonationRepository donations;
    private final ReceiptRepository receipts;
    private final ReceiptCancellationRepository cancellations;
    private final TrustProfileRepository profiles;
    private final PanProtection pans;
    private final Clock clock = Clock.system(DonationService.IST);

    public ReceiptService(DonationRepository donations, ReceiptRepository receipts,
                          ReceiptCancellationRepository cancellations, TrustProfileRepository profiles,
                          PanProtection pans) {
        this.donations = donations;
        this.receipts = receipts;
        this.cancellations = cancellations;
        this.profiles = profiles;
        this.pans = pans;
    }

    @Transactional
    public Receipt issue(long donationId, String donorPan, String donorAddress, long staffId) {
        long tenantId = currentTenant();
        Donation donation = donations.findById(donationId).orElseThrow(DonationService.DonationNotFoundException::new);
        if (donation.isReversal()) {
            throw new LedgerConflictException("not_a_donation");
        }
        if (donations.existsByReversesId(donationId)) {
            throw new LedgerConflictException("donation_reversed");
        }
        if (receipts.findByDonationId(donationId).isPresent()) {
            throw new LedgerConflictException("already_receipted");
        }
        if (donation.getMode() == DonationMode.CASH && donation.getAmountPaise() > CASH_LIMIT_PAISE) {
            throw new LedgerConflictException("cash_over_2000_not_eligible");
        }
        TrustProfile trust = profiles.findById(tenantId)
                .orElseThrow(() -> new LedgerConflictException("trust_profile_missing"));
        if (!trust.validOn(donation.getReceivedOn())) {
            throw new LedgerConflictException("registration_not_valid_on_donation_date");
        }
        String pan = PanProtection.normalise(donorPan);
        String address = donorAddress == null ? "" : donorAddress.strip();
        if (address.isEmpty() || address.length() > 400 || address.indexOf('\0') >= 0) {
            throw new InvalidFieldException("donorAddress", "donorAddress is required (at most 400 characters)");
        }

        int fy = FinancialYear.containing(donation.getReceivedOn()).startYear();
        try {
            int seq = receipts.takeNumber(tenantId, fy);
            Receipt receipt = receipts.saveAndFlush(Receipt.issue(tenantId, donation, fy, seq, LocalDate.now(clock),
                    address, pans.encrypt(tenantId, pan), PanProtection.last4(pan), pans.index(tenantId, pan), trust,
                    staffId));
            audit.info("event=receipt_issued tenant={} user={} donation={} receipt={}", tenantId, staffId,
                    donationId, receipt.number());
            return receipt;
        } catch (DataIntegrityViolationException e) {
            // Two issuers at once for the same donation: UNIQUE (donation_id) lets one win, and this
            // transaction (with the number it took) rolls back.
            throw new LedgerConflictException("already_receipted");
        }
    }

    /** Called when a donation is reversed, in the same transaction: its receipt is cancelled. */
    @Transactional
    public void cancelForDonation(long donationId, String reason, long staffId) {
        receipts.findByDonationId(donationId).ifPresent(receipt -> {
            cancellations.save(new ReceiptCancellation(receipt.getId(), receipt.getTenantId(),
                    "Donation reversed: " + reason, staffId));
            audit.info("event=receipt_cancelled tenant={} user={} receipt={}", receipt.getTenantId(), staffId,
                    receipt.number());
        });
    }

    @Transactional(readOnly = true)
    public Receipt get(long id) {
        return receipts.findById(id).orElseThrow(ReceiptNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Optional<ReceiptCancellation> cancellation(long receiptId) {
        return cancellations.findById(receiptId);
    }

    /** The full PAN, for printing the receipt; never logged, never in lists. */
    public String donorPan(Receipt receipt) {
        return pans.decrypt(receipt.getTenantId(), receipt.getDonorPanEnc());
    }

    @Transactional(readOnly = true)
    public Page<Receipt> list(int fyStart, int page, int size) {
        return receipts.findByFyStart(fyStart, PageRequest.of(Math.clamp(page, 0, 10_000), Math.clamp(size, 1, 100),
                Sort.by("seq")));
    }

    @Transactional(readOnly = true)
    public Optional<TrustProfile> profile() {
        return profiles.findById(currentTenant());
    }

    @Transactional
    public TrustProfile saveProfile(String legalName, String address, String pan, String registration80g,
                                    LocalDate validFrom, LocalDate validTo, long staffId) {
        if (validTo.isBefore(validFrom)) {
            throw new InvalidFieldException("validTo", "validTo must be on or after validFrom");
        }
        long tenantId = currentTenant();
        TrustProfile profile = profiles.findById(tenantId).orElseGet(() -> new TrustProfile(tenantId));
        profile.update(legalName.strip(), address.strip(), trustPan(pan), registration80g.strip(), validFrom, validTo,
                staffId, OffsetDateTime.now(clock));
        return profiles.save(profile);
    }

    private static String trustPan(String pan) {
        String p = pan == null ? "" : pan.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
        if (!p.matches("[A-Z]{3}[ABCFGHJLPT][A-Z][0-9]{4}[A-Z]")) {
            throw new InvalidFieldException("pan", "pan must be the trust's valid PAN (e.g. AAATS1234F)");
        }
        return p;
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    public static class ReceiptNotFoundException extends RuntimeException { }
}
