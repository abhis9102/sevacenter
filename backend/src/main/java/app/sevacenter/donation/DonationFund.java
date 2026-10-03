package app.sevacenter.donation;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An earmarked fund donations can be given to (ADR 0022). Deactivated, never deleted. RLS-scoped. */
@Entity
@Table(name = "donation_fund")
public class DonationFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DonationFund() { }

    DonationFund(long tenantId) {
        this.tenantId = tenantId;
    }

    void edit(String name, boolean active, long staffId, OffsetDateTime now) {
        this.name = name;
        this.active = active;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
