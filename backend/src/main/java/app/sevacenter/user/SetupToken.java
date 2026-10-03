package app.sevacenter.user;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A one-time setup link, stored only as the SHA-256 of the token (V4 migration). RLS-scoped. */
@Entity
@Table(name = "user_setup_token")
public class SetupToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    protected SetupToken() { }

    public SetupToken(Long tenantId, Long userId, String tokenHash, OffsetDateTime expiresAt) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isRedeemable(OffsetDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(OffsetDateTime now) {
        this.usedAt = now;
    }

    public Long getUserId() { return userId; }
}
