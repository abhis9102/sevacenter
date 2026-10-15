package app.sevacenter.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS: only the current tenant's memberships. */
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findAllByGroupIdOrderByRequestedAtAsc(long groupId);

    Optional<GroupMember> findByGroupIdAndAccountId(long groupId, long accountId);

    List<GroupMember> findAllByAccountId(long accountId);

    long countByGroupIdAndStatus(long groupId, String status);

    @Query("select m.groupId, count(m) from GroupMember m where m.status = 'ACTIVE' group by m.groupId")
    List<Object[]> activeCounts();
}
