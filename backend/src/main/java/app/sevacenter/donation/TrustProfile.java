package app.sevacenter.donation;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The trust's 80G details, printed on every receipt (ADR 0012). One per tenant; RLS-scoped. */
@Entity
@Table(name = "trust_profile")
public class TrustProfile {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String pan;

    @Column(name = "registration_80g", nullable = false)
    private String registration80g;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TrustProfile() { }

    TrustProfile(long tenantId) {
        this.tenantId = tenantId;
    }

    void update(String legalName, String address, String pan, String registration80g, LocalDate validFrom,
                LocalDate validTo, long staffId, OffsetDateTime now) {
        this.legalName = legalName;
        this.address = address;
        this.pan = pan;
        this.registration80g = registration80g;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    boolean validOn(LocalDate day) {
        return !day.isBefore(validFrom) && !day.isAfter(validTo);
    }

    public String getLegalName() { return legalName; }
    public String getAddress() { return address; }
    public String getPan() { return pan; }
    public String getRegistration80g() { return registration80g; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
