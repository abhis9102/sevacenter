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
 * Festivals and utsavs created and managed by temple staff in SevaCenter.
 */
@Entity
@Table(name = "mandir_event")
public class MandirEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "time_range", nullable = false)
    private String timeRange;

    @Column(name = "description")
    private String description;

    @Column(name = "highlights")
    private String highlights;

    @Column(name = "registration_open", nullable = false)
    private Boolean registrationOpen = true;

    @Column(name = "max_capacity")
    private Integer maxCapacity = 500;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    protected MandirEventEntity() { }

    public MandirEventEntity(Long tenantId, String code, String name, LocalDate eventDate,
                             String timeRange, String description, String highlights,
                             Boolean registrationOpen, Integer maxCapacity, Boolean active) {
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.eventDate = eventDate;
        this.timeRange = timeRange;
        this.description = description;
        this.highlights = highlights;
        this.registrationOpen = registrationOpen != null ? registrationOpen : true;
        this.maxCapacity = maxCapacity != null ? maxCapacity : 500;
        this.active = active != null ? active : true;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDate getEventDate() { return eventDate; }
    public String getTimeRange() { return timeRange; }
    public String getDescription() { return description; }
    public String getHighlights() { return highlights; }
    public Boolean getRegistrationOpen() { return registrationOpen; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public Boolean getActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, LocalDate eventDate, String timeRange, String description,
                       String highlights, Boolean registrationOpen, Integer maxCapacity, Boolean active) {
        if (name != null && !name.isBlank()) this.name = name;
        if (eventDate != null) this.eventDate = eventDate;
        if (timeRange != null && !timeRange.isBlank()) this.timeRange = timeRange;
        this.description = description;
        this.highlights = highlights;
        if (registrationOpen != null) this.registrationOpen = registrationOpen;
        if (maxCapacity != null) this.maxCapacity = maxCapacity;
        if (active != null) this.active = active;
        this.updatedAt = Instant.now();
    }
}
