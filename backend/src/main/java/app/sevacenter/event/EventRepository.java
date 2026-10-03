package app.sevacenter.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
public interface EventRepository extends JpaRepository<Event, Long> {

    /** Locked while a registration counts seats: concurrent registrations can't oversell. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = ?1")
    Optional<Event> lockById(Long id);

    @Query("select e from Event e where e.status = app.sevacenter.event.EventStatus.PUBLISHED and e.endsAt > ?1 "
            + "order by e.startsAt")
    List<Event> upcomingPublished(OffsetDateTime now, Pageable page);

    @Query("select e from Event e order by e.startsAt desc")
    List<Event> newestFirst(Pageable page);
}
