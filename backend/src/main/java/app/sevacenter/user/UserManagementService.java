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

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.PublicUrls;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final AppUserRepository users;
    private final SetupTokenRepository setupTokens;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final UserAvatarRepository avatars;
    private final LoginThrottle throttle;
    private final PasswordResetService resets;
    private final AuditTrail auditTrail;
    private final PublicUrls publicUrls;
    private final Clock clock;

    public UserManagementService(AppUserRepository users, SetupTokenRepository setupTokens,
                                 TenantRepository tenants, PasswordEncoder passwordEncoder,
                                 UserAvatarRepository avatars, LoginThrottle throttle,
                                 PasswordResetService resets, AuditTrail auditTrail, PublicUrls publicUrls) {
        this.auditTrail = auditTrail;
        this.publicUrls = publicUrls;
        this.users = users;
        this.setupTokens = setupTokens;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.avatars = avatars;
        this.throttle = throttle;
        this.resets = resets;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public AppUser getProfile(long userId) {
        return find(userId);
    }

    @Transactional
    public AppUser updateProfile(long userId, String displayName, boolean notifyDevotees, boolean notifyDonations,
                                 boolean notifySecurity) {
        AppUser user = find(userId);
        if (displayName != null && !displayName.isBlank()) {
            user.updateDisplayName(displayName.trim());
        }
        user.updatePreferences(notifyDevotees, notifyDonations, notifySecurity);
        audit.info("event=profile_update tenant={} user={}", user.getTenantId(), user.getId());
        return user;
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        AppUser user = find(userId);
        // Same per-account lockout as login, so a stolen session can't guess the password here
        // instead (guesses here count toward the login lockout, and vice versa).
        String key = LoginThrottle.accountKey(user.getTenantId(), user.getEmail());
        if (throttle.isLocked(key)) {
            throw new LoginThrottle.TooManyAttemptsException();
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throttle.recordFailure(key);
            throw new InvalidFieldException("currentPassword", "Current password does not match");
        }
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 200) {
            throw new InvalidFieldException("newPassword", "Password must be at least 12 characters");
        }
        if (currentPassword.equals(newPassword)) {
            throw new InvalidFieldException("newPassword", "New password must be different from current password");
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        throttle.reset(key);
        audit.info("event=password_change tenant={} user={}", user.getTenantId(), user.getId());
    }

    @Transactional
    public void updateAvatar(long userId, byte[] bytes) {
        String contentType = AvatarProtection.detectContentType(bytes);
        AppUser user = find(userId);
        avatarOf(user).replace(bytes, contentType);
        audit.info("event=avatar_update tenant={} user={} bytes={} contentType={}",
                user.getTenantId(), user.getId(), bytes.length, contentType);
    }

    @Transactional(readOnly = true)
    public AvatarRecord getAvatar(long userId) {
        UserAvatar avatar = avatarOf(find(userId));
        if (avatar.getContentType() == null) {
            throw new AvatarNotFoundException();
        }
        return new AvatarRecord(avatar.getData(), avatar.getContentType());
    }

    @Transactional
    public void removeAvatar(long userId) {
        AppUser user = find(userId);
        avatarOf(user).clear();
        audit.info("event=avatar_remove tenant={} user={}", user.getTenantId(), user.getId());
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAllByDeletedAtIsNullOrderByCreatedAtAsc();
    }

    /** Creates a PENDING user and returns their one-time setup link (shown to the admin once). */
    @Transactional
    public CreatedUser create(String email, String displayName, Role role) {
        long tenantId = currentTenant();
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalized)) {
            throw new UserConflictException("email_taken");
        }
        AppUser user;
        try {
            user = users.saveAndFlush(AppUser.pending(tenantId, normalized, displayName.trim(), role));
        } catch (DataIntegrityViolationException e) {
            // Two invites of one email at once both pass the check; the unique index lets one win.
            throw new UserConflictException("email_taken");
        }
        auditTrail.record(AuditAction.USER_INVITED, "user", user.getId(), role.name());
        return new CreatedUser(user, issueSetupLink(user));
    }

    /**
     * A one-time password reset link for an ACTIVE staff member, handed over by the admin like a
     * setup link (no email yet). Not for yourself: use change-password, which needs your current one.
     */
    @Transactional
    public String issueResetLink(long userId, long callerId) {
        if (userId == callerId) {
            throw new UserConflictException("use_change_password");
        }
        AppUser user = findLocked(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserConflictException("not_active");
        }
        String token = resets.issueFor(user);
        auditTrail.record(AuditAction.USER_RESET_LINK_ISSUED, "user", user.getId(), null);
        String slug = tenants.findById(user.getTenantId()).orElseThrow().getSlug();
        // In the fragment, like setup links: never sent to a server, never in logs or Referer.
        return publicUrls.staff(slug) + "/reset-password#token=" + token;
    }

    /** A fresh link for a PENDING user; any earlier link stops working. */
    @Transactional
    public String reissueSetupLink(long userId) {
        AppUser user = findLocked(userId);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new UserConflictException("not_pending");
        }
        auditTrail.record(AuditAction.USER_SETUP_LINK_REISSUED, "user", user.getId(), null);
        return issueSetupLink(user);
    }

    @Transactional
    public AppUser changeRole(long userId, Role role) {
        AppUser user = find(userId);
        if (user.getRole() == Role.TRUST_ADMIN && role != Role.TRUST_ADMIN) {
            requireAnotherActiveAdmin(user);
        }
        Role before = user.getRole();
        user.changeRole(role);
        auditTrail.record(AuditAction.USER_ROLE_CHANGED, "user", user.getId(), before.name() + " -> " + role.name());
        return user;
    }

    /**
     * Narrows what a LEADER or MEMBER can reach (ADR 0021). Limits only ever take away; a
     * TRUST_ADMIN is never limited, so the trust can't lock itself out of its own admin.
     */
    @Transactional
    public AppUser changeModuleAccess(long userId, java.util.Map<StaffModule, ModuleAccess> limits) {
        AppUser user = find(userId);
        if (user.getRole() == Role.TRUST_ADMIN) {
            throw new UserConflictException("admins_not_limited");
        }
        user.limitModules(limits);
        String summary = ModuleAccess.format(limits);
        auditTrail.record(AuditAction.USER_ACCESS_CHANGED, "user", user.getId(),
                summary == null ? "no limits" : summary.length() > 80 ? summary.substring(0, 77) + "..." : summary);
        return user;
    }

    @Transactional
    public AppUser deactivate(long userId) {
        AppUser user = find(userId);
        if (user.getRole() == Role.TRUST_ADMIN) {
            requireAnotherActiveAdmin(user);
        }
        user.disable();
        auditTrail.record(AuditAction.USER_DEACTIVATED, "user", user.getId(), null);
        setupTokens.deleteAllForUser(user.getId());
        resets.invalidateFor(user.getId());
        return user;
    }

    /**
     * Deletes a staff member. An invitation never set up (PENDING) is removed outright: they
     * can't have acted. Anyone else is tombstoned ({@link AppUser#tombstone}): their session ends
     * on its next request (StaffSessionFilter re-reads the user) and they can never log in again.
     */
    @Transactional
    public void delete(long userId, long callerId) {
        if (userId == callerId) {
            throw new UserConflictException("cannot_delete_self");
        }
        AppUser user = findLocked(userId);
        if (user.getRole() == Role.TRUST_ADMIN && user.getStatus() == UserStatus.ACTIVE) {
            requireAnotherActiveAdmin(user);
        }
        setupTokens.deleteAllForUser(user.getId());
        resets.invalidateFor(user.getId());
        auditTrail.record(AuditAction.USER_DELETED, "user", user.getId(),
                user.getStatus() == UserStatus.PENDING ? "invitation withdrawn" : null);
        if (user.getStatus() == UserStatus.PENDING) {
            users.delete(user);
        } else {
            user.tombstone(OffsetDateTime.now(clock));
        }
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
        return publicUrls.staff(slug) + "/setup#token=" + token;
    }

    private void requireAnotherActiveAdmin(AppUser leaving) {
        boolean another = users.lockActiveAdmins().stream().anyMatch(a -> !a.getId().equals(leaving.getId()));
        if (!another) {
            throw new UserConflictException("last_admin");
        }
    }

    /**
     * Row-locked: reissuing a link, issuing a reset link and deleting the same user serialize, so a
     * link is never inserted for a user deleted a moment earlier (a foreign-key 500, found by DAST).
     */
    private AppUser findLocked(long userId) {
        return users.findLockedByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);
    }

    private AppUser find(long userId) {
        return users.findByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);
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

    /** The image columns of this (already tenant- and deletion-checked) user; RLS applies too. */
    private UserAvatar avatarOf(AppUser user) {
        return avatars.findById(user.getId()).orElseThrow(UserNotFoundException::new);
    }

    public record AvatarRecord(byte[] data, String contentType) { }

    /** 404: user has no avatar uploaded. */
    public static class AvatarNotFoundException extends RuntimeException { }
}
