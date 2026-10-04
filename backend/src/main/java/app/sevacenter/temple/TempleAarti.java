package app.sevacenter.temple;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the temple's daily aarti timetable (ADR 0024). Replaced as a whole on save; RLS. */
@Entity
@Table(name = "temple_aarti")
public class TempleAarti {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "at_time", nullable = false)
    private LocalTime at;

    private String description;

    protected TempleAarti() { }

    TempleAarti(long tenantId, String name, LocalTime at, String description) {
        this.tenantId = tenantId;
        this.name = name;
        this.at = at;
        this.description = description;
    }

    public String getName() { return name; }
    public LocalTime getAt() { return at; }
    public String getDescription() { return description; }
}
