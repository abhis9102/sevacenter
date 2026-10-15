package app.sevacenter.group;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.portal.DevoteeAccount;
import app.sevacenter.portal.DevoteeLoginService;
import app.sevacenter.portal.OtpChannel;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups (ADR 0037). Staff create and run them; devotees join listed (public, active) ones from
 * MandirCenter: OPEN groups straight away, APPROVAL groups as a request a leader approves.
 * Private groups are never listed and only staff add members. The capacity check runs under a
 * lock on the group, so two devotees joining at once can't overfill it.
 */
@Service
public class GroupService {

    private final CommunityGroupRepository groups;
    private final GroupMemberRepository members;
    private final DevoteeLoginService devotees;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.systemUTC();

    public GroupService(CommunityGroupRepository groups, GroupMemberRepository members, DevoteeLoginService devotees,
                        AuditTrail auditTrail) {
        this.groups = groups;
        this.members = members;
        this.devotees = devotees;
        this.auditTrail = auditTrail;
    }

    public record Details(String name, String description, CommunityGroup.Category category, String meets,
                          String location, Integer capacity, CommunityGroup.Visibility visibility,
                          CommunityGroup.JoinPolicy joinPolicy, boolean active) { }

    // --- staff ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CommunityGroup> all() {
        return groups.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> activeCounts() {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : members.activeCounts()) {
            out.put((Long) row[0], (Long) row[1]);
        }
        return out;
    }

    @Transactional
    public CommunityGroup create(Details d, long staffId) {
        CommunityGroup g = new CommunityGroup(currentTenant(), staffId, now());
        apply(g, d);
        g = groups.save(g);
        auditTrail.record(AuditAction.GROUP_SAVED, "community_group", g.getId(), null);
        return g;
    }

    @Transactional
    public CommunityGroup update(long id, Details d) {
        CommunityGroup g = groups.lockById(id).orElseThrow(NotFoundException::new);
        apply(g, d);
        auditTrail.record(AuditAction.GROUP_SAVED, "community_group", g.getId(), null);
        return g;
    }

    @Transactional
    public void delete(long id) {
        CommunityGroup g = groups.findById(id).orElseThrow(NotFoundException::new);
        groups.delete(g); // memberships cascade
        auditTrail.record(AuditAction.GROUP_DELETED, "community_group", id, null);
    }

    public record MemberView(GroupMember member, DevoteeAccount account) { }

    @Transactional(readOnly = true)
    public List<MemberView> members(long groupId) {
        groups.findById(groupId).orElseThrow(NotFoundException::new);
        return members.findAllByGroupIdOrderByRequestedAtAsc(groupId).stream()
                .map(m -> new MemberView(m, devotees.account(m.getAccountId()).orElse(null))).toList();
    }

    /** Staff add someone who has signed in to MandirCenter at least once (any group, incl. private). */
    @Transactional
    public GroupMember add(long groupId, OtpChannel channel, String contact, long staffId) {
        CommunityGroup g = groups.lockById(groupId).orElseThrow(NotFoundException::new);
        DevoteeAccount account = devotees.accountFor(channel, contact)
                .orElseThrow(() -> new InvalidFieldException("contact",
                        "No devotee has signed in to your Mandir Center with this yet: ask them to sign in once"));
        GroupMember m = members.findByGroupIdAndAccountId(groupId, account.getId())
                .orElseGet(() -> new GroupMember(currentTenant(), groupId, account.getId(), now()));
        if (m.getStatus() != GroupMember.Status.ACTIVE) {
            ensureRoom(g);
            m.activate(staffId, now());
        }
        m = members.save(m);
        auditTrail.record(AuditAction.GROUP_MEMBER_ADDED, "group_member", m.getId(), "group " + groupId);
        return m;
    }

    @Transactional
    public GroupMember approve(long groupId, long memberId, long staffId) {
        CommunityGroup g = groups.lockById(groupId).orElseThrow(NotFoundException::new);
        GroupMember m = memberOf(groupId, memberId);
        if (m.getStatus() == GroupMember.Status.PENDING) {
            ensureRoom(g);
            m.activate(staffId, now());
            auditTrail.record(AuditAction.GROUP_MEMBER_APPROVED, "group_member", m.getId(), "group " + groupId);
        }
        return m;
    }

    /** Declines a request or removes a member: either way the row goes. */
    @Transactional
    public void remove(long groupId, long memberId) {
        GroupMember m = memberOf(groupId, memberId);
        members.delete(m);
        auditTrail.record(AuditAction.GROUP_MEMBER_REMOVED, "group_member", memberId, "group " + groupId);
    }

    // --- public + devotee -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CommunityGroup> listed() {
        return groups.listed();
    }

    /** A devotee joins a listed group: active at once (OPEN) or a request (APPROVAL). Idempotent. */
    @Transactional
    public GroupMember join(long groupId, DevoteeAccount account) {
        CommunityGroup g = groups.lockById(groupId).filter(CommunityGroup::isListed).orElseThrow(NotFoundException::new);
        Optional<GroupMember> existing = members.findByGroupIdAndAccountId(groupId, account.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        GroupMember m = new GroupMember(currentTenant(), groupId, account.getId(), now());
        if (g.getJoinPolicy() == CommunityGroup.JoinPolicy.OPEN) {
            ensureRoom(g);
            m.activate(null, now());
        }
        return members.save(m);
    }

    @Transactional
    public void leave(long groupId, DevoteeAccount account) {
        members.findByGroupIdAndAccountId(groupId, account.getId()).ifPresent(members::delete);
    }

    public record Membership(CommunityGroup group, GroupMember member) { }

    @Transactional(readOnly = true)
    public List<Membership> mine(DevoteeAccount account) {
        return members.findAllByAccountId(account.getId()).stream()
                .flatMap(m -> groups.findById(m.getGroupId()).filter(CommunityGroup::isActive)
                        .map(g -> new Membership(g, m)).stream())
                .toList();
    }

    // --- helpers ----------------------------------------------------------------------------

    private void ensureRoom(CommunityGroup g) {
        if (g.getCapacity() != null
                && members.countByGroupIdAndStatus(g.getId(), GroupMember.Status.ACTIVE.name()) >= g.getCapacity()) {
            throw new GroupFullException();
        }
    }

    private GroupMember memberOf(long groupId, long memberId) {
        return members.findById(memberId).filter(m -> m.getGroupId() == groupId).orElseThrow(NotFoundException::new);
    }

    private void apply(CommunityGroup g, Details d) {
        if (d.capacity() != null && (d.capacity() < 1 || d.capacity() > 10_000)) {
            throw new InvalidFieldException("capacity", "capacity must be between 1 and 10000");
        }
        g.edit(clean(d.name()), clean(d.description()), d.category(), clean(d.meets()), clean(d.location()),
                d.capacity(), d.visibility(), d.joinPolicy(), d.active(), now());
        if (g.getName() == null) {
            throw new InvalidFieldException("name", "name is required");
        }
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.indexOf('\0') >= 0) {
            throw new InvalidFieldException("text", "text contains a NUL character");
        }
        return s.strip();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static long currentTenant() {
        Long t = TenantContext.get();
        if (t == null) {
            throw new IllegalStateException("groups need a tenant");
        }
        return t;
    }

    /** 404: no such group (or member) on this trust, or not joinable. */
    public static class NotFoundException extends RuntimeException { }

    /** 409: the group has reached its capacity. */
    public static class GroupFullException extends RuntimeException { }
}
