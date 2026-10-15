package app.sevacenter.household;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS: only the current tenant's memberships. */
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    List<HouseholdMember> findAllByHouseholdIdOrderByIdAsc(long householdId);

    Optional<HouseholdMember> findByDevoteeId(long devoteeId);
}
