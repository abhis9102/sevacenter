package app.sevacenter.temple;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant; the tenant filter on delete is defence in depth. */
public interface TempleAartiRepository extends JpaRepository<TempleAarti, Long> {

    @Query("select a from TempleAarti a order by a.at, a.name")
    List<TempleAarti> timetable();

    @Modifying
    @Query("delete from TempleAarti a where a.tenantId = ?1")
    void deleteForTenant(long tenantId);
}
