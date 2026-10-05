package app.sevacenter.puja;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. Deleted pujas (ADR 0029) are never read. */
public interface PujaRepository extends JpaRepository<Puja, Long> {

    @Query("select p from Puja p where p.deletedAt is null order by p.displayOrder, p.name")
    List<Puja> catalog();

    @Query("select p from Puja p where p.active = true and p.deletedAt is null order by p.displayOrder, p.name")
    List<Puja> activeCatalog();

    @Query("select p from Puja p where p.id = ?1 and p.deletedAt is null")
    Optional<Puja> live(Long id);

    /** Shared lock while a booking is made, so a concurrent delete waits for it (and then sees it). */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Puja p where p.id = ?1 and p.deletedAt is null")
    Optional<Puja> shareLock(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Puja p where p.id = ?1 and p.deletedAt is null")
    Optional<Puja> lockById(Long id);
}
