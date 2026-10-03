package app.sevacenter.portal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Puja and ritual items configured in the temple catalog by staff.
 */
@Entity
@Table(name = "puja_catalog")
public class PujaCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "deity", nullable = false)
    private String deity;

    @Column(name = "duration", nullable = false)
    private String duration;

    @Column(name = "dakshina_rupees", nullable = false)
    private Long dakshinaRupees;

    @Column(name = "description")
    private String description;

    @Column(name = "prasad_included", nullable = false)
    private Boolean prasadIncluded = true;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    protected PujaCatalogItem() { }

    public PujaCatalogItem(Long tenantId, String code, String name, String deity, String duration,
                           Long dakshinaRupees, String description, Boolean prasadIncluded,
                           Boolean active, Integer displayOrder) {
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.deity = deity;
        this.duration = duration;
        this.dakshinaRupees = dakshinaRupees != null ? dakshinaRupees : 0L;
        this.description = description;
        this.prasadIncluded = prasadIncluded != null ? prasadIncluded : true;
        this.active = active != null ? active : true;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDeity() { return deity; }
    public String getDuration() { return duration; }
    public Long getDakshinaRupees() { return dakshinaRupees; }
    public String getDescription() { return description; }
    public Boolean getPrasadIncluded() { return prasadIncluded; }
    public Boolean getActive() { return active; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String deity, String duration, Long dakshinaRupees,
                       String description, Boolean prasadIncluded, Boolean active, Integer displayOrder) {
        if (name != null && !name.isBlank()) this.name = name;
        if (deity != null && !deity.isBlank()) this.deity = deity;
        if (duration != null && !duration.isBlank()) this.duration = duration;
        if (dakshinaRupees != null) this.dakshinaRupees = dakshinaRupees;
        this.description = description;
        if (prasadIncluded != null) this.prasadIncluded = prasadIncluded;
        if (active != null) this.active = active;
        if (displayOrder != null) this.displayOrder = displayOrder;
        this.updatedAt = Instant.now();
    }
}
