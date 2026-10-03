package app.sevacenter.portal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Darshan E-Token / Festival Pass for fast entry and crowd check-in at the temple.
 */
@Entity
@Table(name = "darshan_pass")
public class DarshanPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "devotee_id")
    private Long devoteeId;

    @Column(name = "pass_number", nullable = false, unique = true)
    private String passNumber;

    @Column(name = "event_code", nullable = false)
    private String eventCode;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "time_slot", nullable = false)
    private String timeSlot;

    @Column(name = "primary_devotee_name", nullable = false)
    private String primaryDevoteeName;

    @Column(name = "attendee_count", nullable = false)
    private Integer attendeeCount;

    @Column(name = "contact", nullable = false)
    private String contact;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DarshanPass() { }

    public DarshanPass(Long tenantId, Long devoteeId, String passNumber, String eventCode, String eventName,
                       LocalDate visitDate, String timeSlot, String primaryDevoteeName, Integer attendeeCount,
                       String contact) {
        this.tenantId = tenantId;
        this.devoteeId = devoteeId;
        this.passNumber = passNumber;
        this.eventCode = eventCode;
        this.eventName = eventName;
        this.visitDate = visitDate;
        this.timeSlot = timeSlot;
        this.primaryDevoteeName = primaryDevoteeName;
        this.attendeeCount = attendeeCount != null ? attendeeCount : 1;
        this.contact = contact;
        this.status = "ACTIVE";
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

    public String getPassNumber() {
        return passNumber;
    }

    public String getEventCode() {
        return eventCode;
    }

    public String getEventName() {
        return eventName;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public String getPrimaryDevoteeName() {
        return primaryDevoteeName;
    }

    public Integer getAttendeeCount() {
        return attendeeCount;
    }

    public String getContact() {
        return contact;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public void markCheckedIn(Instant now) {
        this.checkedInAt = now;
        this.status = "COMPLETED";
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
