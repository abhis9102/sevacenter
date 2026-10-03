package app.sevacenter.user;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A user belonging to exactly one tenant. The {@code tenant_id} column is governed by the
 * RLS policy — the application never filters by tenant in its own queries; the database
 * enforces it. Email is unique per tenant.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String email;

    /** Null while PENDING (V4 enforces: an ACTIVE user always has one). */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "notify_devotees", nullable = false)
    private boolean notifyDevotees = true;

    @Column(name = "notify_donations", nullable = false)
    private boolean notifyDonations = true;

    @Column(name = "notify_security", nullable = false)
    private boolean notifySecurity = true;

    /**
     * Read-only here: {@link UserAvatar} owns the image columns, so the bytes (up to 2 MB) never
     * load with the user, which StaffSessionFilter re-reads on every request.
     */
    @Column(name = "avatar_content_type", insertable = false, updatable = false)
    private String avatarContentType;

    protected AppUser() { }

    public AppUser(Long tenantId, String email, String passwordHash, String displayName, Role role) {
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    /** A user an admin has created; they set their own password via a one-time setup link. */
    public static AppUser pending(Long tenantId, String email, String displayName, Role role) {
        AppUser user = new AppUser(tenantId, email, null, displayName, role);
        user.status = UserStatus.PENDING;
        return user;
    }

    public void activate(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.status = UserStatus.ACTIVE;
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateDisplayName(String newDisplayName) {
        this.displayName = newDisplayName;
    }

    public void updatePreferences(boolean notifyDevotees, boolean notifyDonations, boolean notifySecurity) {
        this.notifyDevotees = notifyDevotees;
        this.notifyDonations = notifyDonations;
        this.notifySecurity = notifySecurity;
    }

    public boolean hasAvatar() {
        return this.avatarContentType != null;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    /**
     * Deletes someone who may have acted: their login identity goes for good (the email is freed
     * for a new invitation, no password, DISABLED), the display name stays for the audit trail.
     */
    public void tombstone(OffsetDateTime now) {
        this.email = "deleted-" + id + "@users.invalid";
        this.passwordHash = null;
        this.status = UserStatus.DISABLED;
        this.deletedAt = now;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public boolean isNotifyDevotees() { return notifyDevotees; }
    public boolean isNotifyDonations() { return notifyDonations; }
    public boolean isNotifySecurity() { return notifySecurity; }
}
