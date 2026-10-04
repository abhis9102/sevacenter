package app.sevacenter.puja;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
public interface PriestRepository extends JpaRepository<Priest, Long> {

    @Query("select p from Priest p order by p.active desc, lower(p.name)")
    List<Priest> listed();

    @Query("select count(p) > 0 from Priest p where lower(p.name) = lower(?1) and (?2 is null or p.id <> ?2)")
    boolean nameTaken(String name, Long exceptId);
}
