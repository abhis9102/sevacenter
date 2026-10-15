package app.sevacenter.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Routing registry (ADR 0036): a verified, active hostname and the trust it serves. Readable by
 * everyone (it's read before any tenant is known, and its data is public: it's in DNS); RLS lets a
 * trust add or remove only its own routes. Written by CustomDomainService after DNS proof.
 */
@Entity
@Table(name = "tenant_domain")
public class TenantDomain {

    @Id
    private String hostname;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TenantDomain() { }

    TenantDomain(String hostname, long tenantId, OffsetDateTime now) {
        this.hostname = hostname;
        this.tenantId = tenantId;
        this.createdAt = now;
    }

    public String getHostname() { return hostname; }
    public Long getTenantId() { return tenantId; }
}
