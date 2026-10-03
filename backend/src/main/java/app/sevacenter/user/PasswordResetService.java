package app.sevacenter.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time password reset links (SHA-256 of the token stored, 1 h, single use, RLS-scoped so a
 * link only works on its own trust's host). Links are issued by a trust admin, never to whoever
 * asks: self-service "forgot password" needs email delivery first.
 */
@Service
public class PasswordResetService {

    public static final Duration RESET_TTL = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final AppUserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordResetService(AppUserRepository users, PasswordResetTokenRepository resetTokens,
                                PasswordEncoder passwordEncoder) {
        this(users, resetTokens, passwordEncoder, Clock.systemUTC());
    }

    PasswordResetService(AppUserRepository users, PasswordResetTokenRepository resetTokens,
                         PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * A fresh one-time reset token for an ACTIVE staff member; earlier ones stop working. Issued by
     * a trust admin (UserManagementService#issueResetLink) and handed over like a setup link: there
     * is no email delivery yet, and a token must never be returned to whoever merely asks.
     */
    @Transactional
    public String issueFor(AppUser user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("reset links are only for active staff");
        }
        resetTokens.invalidateAllForUser(user.getId());
        String rawToken = generateToken();
        resetTokens.save(new PasswordResetToken(user.getTenantId(), user.getId(), hashToken(rawToken),
                OffsetDateTime.now(clock).plus(RESET_TTL)));
        audit.info("event=password_reset_issued tenant={} user={}", user.getTenantId(), user.getId());
        return rawToken;
    }

    /** Called when staff are deactivated or deleted: their outstanding reset links die. */
    @Transactional
    public void invalidateFor(long userId) {
        resetTokens.invalidateAllForUser(userId);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidResetTokenException();
        }
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 200) {
            throw new InvalidFieldException("newPassword", "Password must be at least 12 characters");
        }

        String hash = hashToken(rawToken.trim());
        PasswordResetToken token = resetTokens.findByTokenHash(hash)
                .orElseThrow(InvalidResetTokenException::new);

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!token.isRedeemable(now)) {
            throw new InvalidResetTokenException();
        }

        // Only someone who can still sign in: not a deactivated or deleted (tombstoned) account.
        AppUser user = users.findById(token.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(InvalidResetTokenException::new);

        user.updatePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        audit.info("event=password_reset_completed tenant={} user={}", user.getTenantId(), user.getId());
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class InvalidResetTokenException extends RuntimeException { }
}
