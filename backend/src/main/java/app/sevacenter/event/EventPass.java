package app.sevacenter.event;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One registration: a pass for 1-10 people, shown at the gate by its random code (ADR 0014).
 * Contact details are PII: staff LEADER+ only; gate volunteers see name and head count.
 */
@Entity
@Table(name = "event_pass")
public class EventPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "pass_code", nullable = false, updatable = false)
    private String passCode;

    @Column(name = "attendee_name", nullable = false, updatable = false)
    private String attendeeName;

    @Column(name = "attendee_count", nullable = false, updatable = false)
    private int attendeeCount;

    @Column(updatable = false)
    private String phone;

    @Column(updatable = false)
    private String email;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "checked_in_by")
    private Long checkedInBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "issued_by", updatable = false)
    private Long issuedBy;

    protected EventPass() { }

    EventPass(long tenantId, long eventId, String passCode, String attendeeName, int attendeeCount, String phone,
              String email) {
        this.tenantId = tenantId;
        this.eventId = eventId;
        this.passCode = passCode;
        this.attendeeName = attendeeName;
        this.attendeeCount = attendeeCount;
        this.phone = phone;
        this.email = email;
    }

    void issuedBy(long staffId) {
        this.issuedBy = staffId;
    }

    void checkIn(long staffId, OffsetDateTime now) {
        this.checkedInAt = now;
        this.checkedInBy = staffId;
    }

    void cancel() {
        this.status = "CANCELLED";
    }

    boolean isActive() { return "ACTIVE".equals(status); }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getPassCode() { return passCode; }
    public String getAttendeeName() { return attendeeName; }
    public int getAttendeeCount() { return attendeeCount; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public OffsetDateTime getCheckedInAt() { return checkedInAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
