package app.sevacenter.user;

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

import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff lifecycle within the current tenant (ADR 0009). Who may call what is enforced with
 * {@code @PreAuthorize} on the controller; the business rules live here:
 * <ul>
 *   <li>new users are PENDING and get a one-time setup link (72 h, single use, stored hashed);</li>
 *   <li>the trust can never be left without an active admin, even under concurrent changes;</li>
 *   <li>every lookup is RLS-scoped, so another tenant's user id is simply "not found".</li>
 * </ul>
 */
@Service
public class UserManagementService {

    static final Duration SETUP_LINK_TTL = Duration.ofHours(72);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final SetupTokenRepository setupTokens;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserManagementService(AppUserRepository users, SetupTokenRepository setupTokens,
                                 TenantRepository tenants, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.setupTokens = setupTokens;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAllByOrderByCreatedAtAsc();
    }

    /** Creates a PENDING user and returns their one-time setup link (shown to the admin once). */
    @Transactional
    public CreatedUser create(String email, String displayName, Role role) {
        long tenantId = currentTenant();
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalized)) {
            throw new UserConflictException("email_taken");
        }
        AppUser user = users.saveAndFlush(AppUser.pending(tenantId, normalized, displayName.trim(), role));
        return new CreatedUser(user, issueSetupLink(user));
    }

    /** A fresh link for a PENDING user; any earlier link stops working. */
    @Transactional
    public String reissueSetupLink(long userId) {
        AppUser user = find(userId);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new UserConflictException("not_pending");
        }
        return issueSetupLink(user);
    }

    @Transactional
    public AppUser changeRole(long userId, Role role) {
        AppUser user = find(userId);
        if (user.getRole() == Role.TRUST_ADMIN && role != Role.TRUST_ADMIN) {
            requireAnotherActiveAdmin(user);
        }
        user.changeRole(role);
        return user;
    }

    @Transactional
    public AppUser deactivate(long userId) {
        AppUser user = find(userId);
        if (user.getRole() == Role.TRUST_ADMIN) {
            requireAnotherActiveAdmin(user);
        }
        user.disable();
        setupTokens.deleteAllForUser(user.getId());
        return user;
    }

    /**
     * Redeems a setup link on the tenant's host: sets the password and activates the user.
     * Unknown, used, expired or other-tenant tokens all fail the same way.
     */
    @Transactional
    public void completeSetup(String token, String rawPassword) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        SetupToken setup = setupTokens.findByTokenHash(sha256(token))
                .filter(t -> t.isRedeemable(now))
                .orElseThrow(InvalidSetupTokenException::new);
        AppUser user = users.findById(setup.getUserId())
                .filter(u -> u.getStatus() == UserStatus.PENDING)
                .orElseThrow(InvalidSetupTokenException::new);
        user.activate(passwordEncoder.encode(rawPassword));
        setup.markUsed(now);
    }

    private String issueSetupLink(AppUser user) {
        setupTokens.deleteAllForUser(user.getId());
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        setupTokens.save(new SetupToken(user.getTenantId(), user.getId(), sha256(token),
                OffsetDateTime.now(clock).plus(SETUP_LINK_TTL)));
        String slug = tenants.findById(user.getTenantId()).orElseThrow().getSlug();
        // In the fragment: browsers never send it to the server, so it stays out of access logs
        // and Referer headers. The setup page reads it and POSTs it to /api/v1/auth/setup.
        return "https://" + slug + ".sevacenter.app/setup#token=" + token;
    }

    private void requireAnotherActiveAdmin(AppUser leaving) {
        boolean another = users.lockActiveAdmins().stream().anyMatch(a -> !a.getId().equals(leaving.getId()));
        if (!another) {
            throw new UserConflictException("last_admin");
        }
    }

    private AppUser find(long userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record CreatedUser(AppUser user, String setupUrl) { }

    /** 404: unknown here, including another tenant's user (RLS hides it). */
    public static class UserNotFoundException extends RuntimeException { }

    /** 409 with a machine-readable reason: email_taken, last_admin, not_pending. */
    public static class UserConflictException extends RuntimeException {
        public UserConflictException(String reason) {
            super(reason);
        }
    }

    /** 400: one answer for unknown, used, expired and other-tenant tokens. */
    public static class InvalidSetupTokenException extends RuntimeException { }
}
