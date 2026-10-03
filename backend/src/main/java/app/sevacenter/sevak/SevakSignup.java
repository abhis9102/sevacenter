package app.sevacenter.sevak;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A volunteer's offer to serve (ADR 0015). Contact details are PII: LEADER+ only. RLS-scoped. */
@Entity
@Table(name = "sevak_signup")
public class SevakSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "full_name", nullable = false, updatable = false)
    private String fullName;

    @Column(updatable = false)
    private String phone;

    @Column(updatable = false)
    private String email;

    @Column(name = "seva_areas", nullable = false, updatable = false)
    private String sevaAreas;

    @Column(updatable = false)
    private String availability;

    @Column(updatable = false)
    private String notes;

    @Column(nullable = false)
    private String status = "NEW";

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SevakSignup() { }

    SevakSignup(long tenantId, String fullName, String phone, String email, String sevaAreas, String availability,
                String notes) {
        this.tenantId = tenantId;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.sevaAreas = sevaAreas;
        this.availability = availability;
        this.notes = notes;
    }

    void review(boolean approve, long staffId, OffsetDateTime now) {
        this.status = approve ? "APPROVED" : "DECLINED";
        this.reviewedBy = staffId;
        this.reviewedAt = now;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getSevaAreas() { return sevaAreas; }
    public String getAvailability() { return availability; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
