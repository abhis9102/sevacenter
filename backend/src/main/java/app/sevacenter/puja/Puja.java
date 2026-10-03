package app.sevacenter.puja;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A puja / sankalp the temple offers, with its dakshina (0 = free). ADR 0016. RLS-scoped. */
@Entity
@Table(name = "puja")
public class Puja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String deity;
    private String description;

    @Column(name = "dakshina_paise", nullable = false)
    private long dakshinaPaise;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Puja() { }

    Puja(long tenantId) {
        this.tenantId = tenantId;
    }

    void edit(String name, String deity, String description, long dakshinaPaise, boolean active, int displayOrder,
              long staffId, OffsetDateTime now) {
        this.name = name;
        this.deity = deity;
        this.description = description;
        this.dakshinaPaise = dakshinaPaise;
        this.active = active;
        this.displayOrder = displayOrder;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDeity() { return deity; }
    public String getDescription() { return description; }
    public long getDakshinaPaise() { return dakshinaPaise; }
    public boolean isActive() { return active; }
    public int getDisplayOrder() { return displayOrder; }
}
