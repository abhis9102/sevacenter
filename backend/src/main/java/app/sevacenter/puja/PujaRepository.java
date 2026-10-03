package app.sevacenter.puja;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
public interface PujaRepository extends JpaRepository<Puja, Long> {

    @Query("select p from Puja p order by p.displayOrder, p.name")
    List<Puja> catalog();

    @Query("select p from Puja p where p.active = true order by p.displayOrder, p.name")
    List<Puja> activeCatalog();
}
