package app.sevacenter.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles one-time password reset flows with enumeration defence and SHA-256 token hashing.
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

    @Transactional
    public Optional<String> requestReset(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> userOpt = users.findByEmail(cleanEmail);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE || user.getPasswordHash() == null) {
            return Optional.empty();
        }

        resetTokens.invalidateAllForUser(user.getId());

        String rawToken = generateToken();
        String hash = hashToken(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(RESET_TTL);

        resetTokens.save(new PasswordResetToken(user.getTenantId(), user.getId(), hash, expiresAt));
        audit.info("event=password_reset_requested tenant={} user={}", user.getTenantId(), user.getId());
        return Optional.of(rawToken);
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

        AppUser user = users.findById(token.getUserId())
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
