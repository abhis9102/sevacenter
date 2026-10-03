package app.sevacenter.devotee;

import java.time.LocalDate;
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
 * A devotee of one temple (ADR 0010). Tenant-scoped by RLS like every other table: queries
 * never filter by tenant themselves. The consent record is set once, by the server.
 */
@Entity
@Table(name = "devotee")
public class Devotee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;
    private String email;

    @Column(name = "address_line")
    private String addressLine;

    private String city;
    private String state;
    private String pincode;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_source", nullable = false, updatable = false)
    private ConsentSource consentSource;

    @Column(name = "consent_given_at", nullable = false, updatable = false)
    private OffsetDateTime consentGivenAt;

    @Column(name = "consent_recorded_by", nullable = false, updatable = false)
    private Long consentRecordedBy;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "erased_at")
    private OffsetDateTime erasedAt;

    protected Devotee() { }

    /**
     * Erasure for a devotee the ledger references (ADR 0011): every personal field goes; the row
     * stays so donations still point somewhere. The ledger keeps its own donor-name snapshot
     * (books of account, retained by law).
     */
    public void anonymise(Long staffId, OffsetDateTime now) {
        update(new Details("Erased devotee", null, null, null, null, null, null, null), staffId, now);
        this.erasedAt = now;
    }

    public Devotee(Long tenantId, Details details, ConsentSource consentSource, Long staffId, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.consentSource = consentSource;
        this.consentGivenAt = now;
        this.consentRecordedBy = staffId;
        this.createdBy = staffId;
        update(details, staffId, now);
    }

    /** Replaces the editable fields. Tenant, consent and creation data never change. */
    public void update(Details d, Long staffId, OffsetDateTime now) {
        this.fullName = d.fullName();
        this.phone = d.phone();
        this.email = d.email();
        this.addressLine = d.addressLine();
        this.city = d.city();
        this.state = d.state();
        this.pincode = d.pincode();
        this.dateOfBirth = d.dateOfBirth();
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    /** The editable personal fields, already validated and normalised. */
    public record Details(String fullName, String phone, String email, String addressLine, String city,
                          String state, String pincode, LocalDate dateOfBirth) { }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddressLine() { return addressLine; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPincode() { return pincode; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public ConsentSource getConsentSource() { return consentSource; }
    public OffsetDateTime getConsentGivenAt() { return consentGivenAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
