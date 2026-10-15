package app.sevacenter.group;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** RLS: only the current tenant's groups. */
public interface CommunityGroupRepository extends JpaRepository<CommunityGroup, Long> {

    List<CommunityGroup> findAllByOrderByNameAsc();

    @Query("select g from CommunityGroup g where g.active = true and g.visibility = 'PUBLIC' order by g.name")
    List<CommunityGroup> listed();

    /** Joins and approvals of one group run one at a time, so the capacity check holds. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CommunityGroup g where g.id = ?1")
    Optional<CommunityGroup> lockById(long id);
}
