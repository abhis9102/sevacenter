package app.sevacenter.portal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Devotee volunteer registration for temple seva teams during festivals and regular events.
 */
@Entity
@Table(name = "sevak_signup")
public class SevakSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "devotee_id")
    private Long devoteeId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "contact", nullable = false)
    private String contact;

    @Column(name = "seva_area", nullable = false)
    private String sevaArea;

    @Column(name = "available_days", nullable = false)
    private String availableDays;

    @Column(name = "shift_preference", nullable = false)
    private String shiftPreference;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected SevakSignup() { }

    public SevakSignup(Long tenantId, Long devoteeId, String fullName, String contact, String sevaArea,
                       String availableDays, String shiftPreference, String notes) {
        this.tenantId = tenantId;
        this.devoteeId = devoteeId;
        this.fullName = fullName;
        this.contact = contact;
        this.sevaArea = sevaArea;
        this.availableDays = availableDays;
        this.shiftPreference = shiftPreference;
        this.notes = notes;
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

    public String getFullName() {
        return fullName;
    }

    public String getContact() {
        return contact;
    }

    public String getSevaArea() {
        return sevaArea;
    }

    public String getAvailableDays() {
        return availableDays;
    }

    public String getShiftPreference() {
        return shiftPreference;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
