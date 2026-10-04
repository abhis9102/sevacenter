package app.sevacenter.sevak;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
public interface SevaTeamRepository extends JpaRepository<SevaTeam, Long> {

    @Query("select t from SevaTeam t where t.deletedAt is null order by lower(t.name)")
    List<SevaTeam> live();

    @Query("select count(t) > 0 from SevaTeam t where t.deletedAt is null and lower(t.name) = lower(?1) and (?2 is null or t.id <> ?2)")
    boolean nameTaken(String name, Long exceptId);

    @Query("select s from SevaShift s order by s.startsAt")
    List<SevaShift> shifts();

    @Modifying
    @Query("delete from SevaShift s where s.teamId = ?1 and s.tenantId = ?2")
    void deleteShifts(long teamId, long tenantId);
}
