package app.sevacenter.event;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant: a pass code only resolves on its own host. */
public interface EventPassRepository extends JpaRepository<EventPass, Long> {

    @Query("select coalesce(sum(p.attendeeCount), 0) from EventPass p where p.eventId = ?1 and p.status = 'ACTIVE'")
    long seatsTaken(Long eventId);

    /** Locked: two gate volunteers scanning the same pass check it in once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EventPass p where p.passCode = ?1")
    Optional<EventPass> lockByCode(String passCode);

    List<EventPass> findByEventIdOrderByCreatedAtAsc(Long eventId);

    boolean existsByPassCode(String passCode);
}
