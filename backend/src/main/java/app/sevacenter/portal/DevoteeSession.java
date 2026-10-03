package app.sevacenter.portal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Active authenticated session for a devotee on this temple's portal.
 */
@Entity
@Table(name = "devotee_session")
public class DevoteeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "devotee_id", nullable = false)
    private Long devoteeId;

    @Column(name = "session_token_hash", nullable = false, unique = true)
    private String sessionTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DevoteeSession() { }

    public DevoteeSession(Long tenantId, Long devoteeId, String sessionTokenHash, Instant expiresAt) {
        this.tenantId = tenantId;
        this.devoteeId = devoteeId;
        this.sessionTokenHash = sessionTokenHash;
        this.expiresAt = expiresAt;
        this.lastSeenAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getDevoteeId() {
        return devoteeId;
    }

    public String getSessionTokenHash() {
        return sessionTokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
