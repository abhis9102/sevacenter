package app.sevacenter.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A trust's claim on its own domain (ADR 0036): active only once DNS proves ownership. RLS. */
@Entity
@Table(name = "custom_domain")
public class CustomDomain {

    public enum Status { PENDING, ACTIVE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private String token;

    @Column(nullable = false)
    private String status = Status.PENDING.name();

    @Column(name = "points_here", nullable = false)
    private boolean pointsHere;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    protected CustomDomain() { }

    CustomDomain(long tenantId, String hostname, String token, long staffId, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.hostname = hostname;
        this.token = token;
        this.createdBy = staffId;
        this.createdAt = now;
    }

    void checked(boolean pointsHere, OffsetDateTime now) {
        this.pointsHere = pointsHere;
        this.lastCheckedAt = now;
    }

    void activate(OffsetDateTime now) {
        this.status = Status.ACTIVE.name();
        this.lastError = null;
        if (this.verifiedAt == null) {
            this.verifiedAt = now;
        }
    }

    void fail(String reason) {
        this.status = Status.FAILED.name();
        this.lastError = reason.length() > 200 ? reason.substring(0, 200) : reason;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getHostname() { return hostname; }
    public String getToken() { return token; }
    public Status getStatus() { return Status.valueOf(status); }
    public boolean isPointsHere() { return pointsHere; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public OffsetDateTime getLastCheckedAt() { return lastCheckedAt; }
}
