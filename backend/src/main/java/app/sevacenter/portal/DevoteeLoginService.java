package app.sevacenter.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Devotee login with one-time codes (ADR 0018). Anyone may ask for a code for any contact: the
 * account exists only once a code sent to that contact comes back, so there is nothing to
 * enumerate. What an attacker could abuse is the sending itself (SMS costs money, and floods a
 * stranger's phone), hence limits per contact, per IP (controller) and per trust per day.
 */
@Service
public class DevoteeLoginService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int CODES_PER_CONTACT_15_MIN = 3;
    static final int CODES_PER_CONTACT_DAY = 10;
    static final int SMS_PER_TRUST_DAY = 500;
    static final int EMAIL_PER_TRUST_DAY = 2_000;
    public static final Duration SESSION_TTL = Duration.ofDays(7);

    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final Logger log = LoggerFactory.getLogger(DevoteeLoginService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DevoteeOtpRepository codes;
    private final DevoteeAccountRepository accounts;
    private final DevoteeSessionRepository sessions;
    private final TenantRepository tenants;
    private final OtpMac mac;
    private final List<OtpSender> senders;
    private final Clock clock = Clock.systemUTC();

    public DevoteeLoginService(DevoteeOtpRepository codes, DevoteeAccountRepository accounts,
                               DevoteeSessionRepository sessions, TenantRepository tenants, OtpMac mac,
                               List<OtpSender> senders) {
        this.codes = codes;
        this.accounts = accounts;
        this.sessions = sessions;
        this.tenants = tenants;
        this.mac = mac;
        this.senders = senders;
    }

    public boolean offered(OtpChannel channel) {
        return sender(channel).isPresent();
    }

    /**
     * Sends a fresh code. Only the newest code for a contact is ever checked, so this replaces any
     * earlier one. The code is never returned or logged.
     */
    @Transactional
    public void sendCode(OtpChannel channel, String rawContact) {
        long tenantId = currentTenant();
        String contact = normalise(channel, rawContact);
        OtpSender sender = sender(channel).orElseThrow(ChannelUnavailableException::new);
        String contactMac = mac.contact(tenantId, channel, contact);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (codes.countByContactMacAndCreatedAtAfter(contactMac, now.minusMinutes(15)) >= CODES_PER_CONTACT_15_MIN
                || codes.countByContactMacAndCreatedAtAfter(contactMac, now.minusDays(1)) >= CODES_PER_CONTACT_DAY) {
            throw new LoginThrottle.TooManyAttemptsException();
        }
        int dailyCap = channel == OtpChannel.SMS ? SMS_PER_TRUST_DAY : EMAIL_PER_TRUST_DAY;
        if (codes.countByChannelAndCreatedAtAfter(channel, now.minusDays(1)) >= dailyCap) {
            log.warn("devotee login codes: daily {} cap reached for tenant {}", channel, tenantId);
            throw new LoginThrottle.TooManyAttemptsException();
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        DevoteeOtp saved = codes.save(new DevoteeOtp(tenantId, channel, contactMac, mac.code(tenantId, contactMac, code),
                now.plus(CODE_TTL)));
        String trustName = tenants.findById(tenantId).map(t -> t.getName()).orElseThrow();
        // Inside the transaction on purpose: if the provider refuses, no code row is left behind.
        try {
            sender.send(contact, code, trustName);
        } catch (RuntimeException e) {
            log.error("devotee login code delivery failed: tenant={} channel={} error={}", tenantId, channel,
                    e.getClass().getSimpleName());
            throw new DeliveryFailedException();
        }
        audit.info("event=devotee_code_sent tenant={} channel={} code_id={}", tenantId, channel, saved.getId());
    }

    /**
     * A new session token if {@code code} is the open code for this contact. Every failure is the
     * same {@link InvalidCodeException}; a wrong guess still counts (no rollback on that exception).
     */
    @Transactional(noRollbackFor = InvalidCodeException.class)
    public String verify(OtpChannel channel, String rawContact, String rawCode) {
        long tenantId = currentTenant();
        String contact = normalise(channel, rawContact);
        String code = rawCode == null ? "" : rawCode.strip();
        if (!code.matches("\\d{6}")) {
            throw new InvalidCodeException();
        }
        String contactMac = mac.contact(tenantId, channel, contact);
        OffsetDateTime now = OffsetDateTime.now(clock);
        DevoteeOtp otp = codes.lockLatest(contactMac, PageRequest.of(0, 1)).stream().findFirst()
                .filter(o -> o.open(now)).orElseThrow(InvalidCodeException::new);
        otp.countAttempt();
        if (!MessageDigest.isEqual(mac.code(tenantId, contactMac, code).getBytes(StandardCharsets.US_ASCII),
                otp.codeMac().getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidCodeException();
        }
        otp.use(now);
        DevoteeAccount account = accounts.findByChannelAndContact(channel, contact)
                .orElseGet(() -> accounts.save(new DevoteeAccount(tenantId, channel, contact, now)));
        account.loggedIn(now);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessions.save(new DevoteeSession(tenantId, account.getId(), sha256(token), now.plus(SESSION_TTL)));
        audit.info("event=devotee_login tenant={} account={}", tenantId, account.getId());
        return token;
    }

    /** The logged-in account for a session cookie on this trust's host, if the session is live. */
    @Transactional(readOnly = true)
    public Optional<DevoteeAccount> resolve(String token) {
        if (TenantContext.get() == null || token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return sessions.findByTokenHash(sha256(token)).filter(s -> s.live(now))
                .flatMap(s -> accounts.findById(s.accountId()));
    }

    @Transactional
    public void logout(String token) {
        if (TenantContext.get() == null || token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        sessions.findByTokenHash(sha256(token)).filter(s -> s.live(now)).ifPresent(s -> {
            s.revoke(now);
            audit.info("event=devotee_logout tenant={} account={}", TenantContext.get(), s.accountId());
        });
    }

    static String normalise(OtpChannel channel, String raw) {
        if (channel == null) {
            throw new InvalidFieldException("channel", "channel must be EMAIL or SMS");
        }
        if (raw == null || raw.isBlank() || raw.length() > 254 || raw.indexOf('\0') >= 0) {
            throw new InvalidFieldException("contact", "contact is required");
        }
        if (channel == OtpChannel.EMAIL) {
            String email = raw.strip().toLowerCase(Locale.ROOT);
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw new InvalidFieldException("contact", "email is not valid");
            }
            return email;
        }
        String phone = DevoteeService.phone(raw);
        // DLT-registered Indian SMS only: no international numbers, so no premium-rate pumping abroad.
        if (!phone.matches("\\+91[6-9]\\d{9}")) {
            throw new InvalidFieldException("contact", "SMS codes go to Indian mobile numbers only");
        }
        return phone;
    }

    private Optional<OtpSender> sender(OtpChannel channel) {
        return senders.stream().filter(s -> s.channel() == channel && s.available()).findFirst();
    }

    private static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new NotOnATrustHostException();
        }
        return tenantId;
    }

    /** 400: wrong, expired, used, superseded or over-guessed; never says which. */
    public static class InvalidCodeException extends RuntimeException { }

    /** 400: that channel isn't set up on this platform. */
    public static class ChannelUnavailableException extends RuntimeException { }

    /** 503: the email/SMS provider refused or timed out; the code row is rolled back. */
    public static class DeliveryFailedException extends RuntimeException { }

    /** 404: devotee login exists only on a trust's own host. */
    public static class NotOnATrustHostException extends RuntimeException { }
}
