package app.sevacenter.household;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS: only the current tenant's invites. */
public interface HouseholdInviteRepository extends JpaRepository<HouseholdInvite, Long> {

    @Query("select i from HouseholdInvite i where i.contact in ?1 and i.status = 'PENDING' and i.expiresAt > ?2 order by i.id")
    List<HouseholdInvite> openFor(List<String> contacts, OffsetDateTime now);

    @Query("select i from HouseholdInvite i where i.householdId = ?1 and i.status = 'PENDING' and i.expiresAt > ?2 order by i.id")
    List<HouseholdInvite> openOf(long householdId, OffsetDateTime now);

    long countByHouseholdIdAndCreatedAtAfter(long householdId, OffsetDateTime since);
}
