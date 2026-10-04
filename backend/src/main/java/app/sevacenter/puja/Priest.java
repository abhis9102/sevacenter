package app.sevacenter.puja;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A temple priest (pujari) who performs sankalps (ADR 0027). Deactivated, never deleted. RLS. */
@Entity
@Table(name = "priest")
public class Priest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String phone;

    private String specialties;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Priest() { }

    Priest(long tenantId) {
        this.tenantId = tenantId;
    }

    void edit(String name, String phone, String specialties, boolean active, long staffId, OffsetDateTime now) {
        this.name = name;
        this.phone = phone;
        this.specialties = specialties;
        this.active = active;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getSpecialties() { return specialties; }
    public boolean isActive() { return active; }
}
