package app.sevacenter.portal;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A logged-in devotee (ADR 0018): the SHA-256 of the cookie's token, never the token. RLS-scoped. */
@Entity
@Table(name = "devotee_session")
class DevoteeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected DevoteeSession() { }

    DevoteeSession(long tenantId, long accountId, String tokenHash, OffsetDateTime expiresAt) {
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    boolean live(OffsetDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    void revoke(OffsetDateTime now) {
        revokedAt = now;
    }

    Long accountId() { return accountId; }
}
