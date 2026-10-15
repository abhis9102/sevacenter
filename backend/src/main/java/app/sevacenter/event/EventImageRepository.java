package app.sevacenter.event;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS: only the current tenant's event photos. */
public interface EventImageRepository extends JpaRepository<EventImage, Long> {

    /** The version only, for event payloads, without loading the bytes. */
    @Query("select i.version from EventImage i where i.eventId = ?1")
    Optional<String> versionOf(long eventId);
}
