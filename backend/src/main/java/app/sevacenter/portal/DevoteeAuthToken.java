package app.sevacenter.portal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 6-digit OTP or magic link authentication token for devotee portal login.
 * Row-Level Security isolates this per tenant.
 */
@Entity
@Table(name = "devotee_auth_token")
public class DevoteeAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "target_type", nullable = false)
    private String targetType; // PHONE or EMAIL

    @Column(name = "target_value", nullable = false)
    private String targetValue;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DevoteeAuthToken() { }

    public DevoteeAuthToken(Long tenantId, String targetType, String targetValue, String tokenHash, Instant expiresAt) {
        this.tenantId = tenantId;
        this.targetType = targetType;
        this.targetValue = targetValue;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void markConsumed(Instant now) {
        this.consumedAt = now;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
