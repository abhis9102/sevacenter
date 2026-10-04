package app.sevacenter.temple;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** RLS: at most the current tenant's row is ever visible. */
public interface TempleProfileRepository extends JpaRepository<TempleProfile, Long> {

    /** Creates the empty row if this temple has none yet; a no-op if another save got there first. */
    @Modifying
    @Query(value = "insert into temple_profile (tenant_id, updated_by, updated_at) values (?1, ?2, now()) "
            + "on conflict (tenant_id) do nothing", nativeQuery = true)
    void ensureRow(long tenantId, long staffId);

    /** Saves of one temple run one at a time (the timetable is replaced as a whole). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TempleProfile p where p.tenantId = ?1")
    Optional<TempleProfile> lockById(long tenantId);
}
