package app.sevacenter.event;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A temple event (utsav, darshan, satsang) with optional capacity (ADR 0014). RLS-scoped. */
@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    /** Null means no limit. */
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "registration_open", nullable = false)
    private boolean registrationOpen = true;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Event() { }

    Event(long tenantId, long staffId, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.createdBy = staffId;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    void edit(String title, String description, OffsetDateTime startsAt, OffsetDateTime endsAt, Integer capacity,
              boolean registrationOpen, long staffId, OffsetDateTime now) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.registrationOpen = registrationOpen;
        touch(staffId, now);
    }

    void changeStatus(EventStatus status, long staffId, OffsetDateTime now) {
        this.status = status;
        touch(staffId, now);
    }

    /** Public registration is possible: published, open, and not yet started. */
    boolean takesRegistrations(OffsetDateTime now) {
        return status == EventStatus.PUBLISHED && registrationOpen && now.isBefore(startsAt);
    }

    private void touch(long staffId, OffsetDateTime now) {
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public Integer getCapacity() { return capacity; }
    public EventStatus getStatus() { return status; }
    public boolean isRegistrationOpen() { return registrationOpen; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
