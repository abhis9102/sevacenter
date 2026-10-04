package app.sevacenter.sevak;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A seva team / volunteer activity (ADR 0028). Deleting sets deleted_at; the row stays. RLS. */
@Entity
@Table(name = "seva_team")
public class SevaTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "target_count")
    private Integer targetCount;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SevaTeam() { }

    SevaTeam(long tenantId) {
        this.tenantId = tenantId;
    }

    void edit(String name, String description, Integer targetCount, long staffId, OffsetDateTime now) {
        this.name = name;
        this.description = description;
        this.targetCount = targetCount;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    void delete(long staffId, OffsetDateTime now) {
        this.deletedAt = now;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Integer getTargetCount() { return targetCount; }
    public boolean isDeleted() { return deletedAt != null; }
}
