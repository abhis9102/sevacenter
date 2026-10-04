package app.sevacenter.sevak;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One shift of a seva team (ADR 0028); a team's shifts are replaced as a whole. RLS. */
@Entity
@Table(name = "seva_shift")
public class SevaShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalTime endsAt;

    protected SevaShift() { }

    SevaShift(long tenantId, long teamId, String name, LocalTime startsAt, LocalTime endsAt) {
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.name = name;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Long getTeamId() { return teamId; }
    public String getName() { return name; }
    public LocalTime getStartsAt() { return startsAt; }
    public LocalTime getEndsAt() { return endsAt; }
}
