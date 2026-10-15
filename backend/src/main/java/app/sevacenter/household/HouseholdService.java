package app.sevacenter.household;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.devotee.Devotee;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Households (ADR 0038): a family as a unit. A devotee belongs to at most one household and a
 * household has at most one head (both also enforced by the database). Removing someone from a
 * household never touches their devotee record.
 */
@Service
public class HouseholdService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final HouseholdRepository households;
    private final HouseholdMemberRepository members;
    private final DevoteeService devotees;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.system(IST);

    public HouseholdService(HouseholdRepository households, HouseholdMemberRepository members, DevoteeService devotees,
                            AuditTrail auditTrail) {
        this.households = households;
        this.members = members;
        this.devotees = devotees;
        this.auditTrail = auditTrail;
    }

    public record Details(String name, String gotra, String addressLine, String city, String state, String pincode,
                          String notes) { }

    public record MemberView(HouseholdMember member, Devotee devotee) { }

    @Transactional(readOnly = true)
    public List<Household> search(String q) {
        return households.search(q == null ? "" : q.strip(), PageRequest.of(0, 20));
    }

    @Transactional(readOnly = true)
    public Household get(long id) {
        return households.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(long householdId) {
        return members.findAllByHouseholdIdOrderByIdAsc(householdId).stream()
                .map(m -> new MemberView(m, devotees.get(m.getDevoteeId()))).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Long> householdOf(long devoteeId) {
        devotees.get(devoteeId); // 404 for a devotee that isn't on this trust
        return members.findByDevoteeId(devoteeId).map(HouseholdMember::getHouseholdId);
    }

    /**
     * A signed-in devotee's own household, found through their verified contacts (ADR 0038): the
     * first devotee record of theirs that belongs to one.
     */
    @Transactional(readOnly = true)
    public Optional<Household> forContacts(List<String> contacts) {
        return devotees.forContacts(contacts).stream()
                .flatMap(d -> members.findByDevoteeId(d.getId()).stream())
                .findFirst().flatMap(m -> households.findById(m.getHouseholdId()));
    }

    /** Net giving of all members in the current financial year (April-March, IST), in paise. */
    @Transactional(readOnly = true)
    public long givingThisYear(long householdId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = LocalDate.of(today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1, 4, 1);
        return households.netGivingPaise(householdId, from, from.plusYears(1).minusDays(1));
    }

    /** A new household, starting with one devotee (usually the head). */
    @Transactional
    public Household create(Details d, long devoteeId, HouseholdMember.Relation relation, long staffId) {
        Household h = new Household(currentTenant(), staffId, now());
        apply(h, d);
        h = households.save(h);
        addMember(h.getId(), devoteeId, relation);
        auditTrail.record(AuditAction.HOUSEHOLD_SAVED, "household", h.getId(), null);
        return h;
    }

    @Transactional
    public Household update(long id, Details d) {
        Household h = get(id);
        apply(h, d);
        auditTrail.record(AuditAction.HOUSEHOLD_SAVED, "household", id, null);
        return h;
    }

    @Transactional
    public void delete(long id) {
        households.delete(get(id)); // memberships cascade; devotee records stay
        auditTrail.record(AuditAction.HOUSEHOLD_DELETED, "household", id, null);
    }

    @Transactional
    public HouseholdMember addMember(long householdId, long devoteeId, HouseholdMember.Relation relation) {
        get(householdId);
        devotees.get(devoteeId);
        if (members.findByDevoteeId(devoteeId).isPresent()) {
            throw new AlreadyInAHouseholdException();
        }
        if (relation == HouseholdMember.Relation.HEAD) {
            ensureNoOtherHead(householdId, null);
        }
        HouseholdMember m = members.save(new HouseholdMember(currentTenant(), householdId, devoteeId, relation, now()));
        auditTrail.record(AuditAction.HOUSEHOLD_MEMBER_ADDED, "household", householdId, "devotee " + devoteeId);
        return m;
    }

    @Transactional
    public HouseholdMember setRelation(long householdId, long memberId, HouseholdMember.Relation relation) {
        HouseholdMember m = memberOf(householdId, memberId);
        if (relation == HouseholdMember.Relation.HEAD) {
            ensureNoOtherHead(householdId, memberId);
        }
        m.relation(relation);
        auditTrail.record(AuditAction.HOUSEHOLD_SAVED, "household", householdId, "relation " + memberId);
        return m;
    }

    @Transactional
    public void removeMember(long householdId, long memberId) {
        HouseholdMember m = memberOf(householdId, memberId);
        members.delete(m);
        auditTrail.record(AuditAction.HOUSEHOLD_MEMBER_REMOVED, "household", householdId, "devotee " + m.getDevoteeId());
    }

    private void ensureNoOtherHead(long householdId, Long exceptMemberId) {
        boolean other = members.findAllByHouseholdIdOrderByIdAsc(householdId).stream()
                .anyMatch(x -> x.getRelation() == HouseholdMember.Relation.HEAD && !x.getId().equals(exceptMemberId));
        if (other) {
            throw new InvalidFieldException("relation", "This household already has a head; change theirs first");
        }
    }

    private HouseholdMember memberOf(long householdId, long memberId) {
        return members.findById(memberId).filter(m -> m.getHouseholdId() == householdId)
                .orElseThrow(NotFoundException::new);
    }

    private void apply(Household h, Details d) {
        String pin = clean(d.pincode());
        if (pin != null && !pin.matches("[1-9]\\d{5}")) {
            throw new InvalidFieldException("pincode", "pincode must be 6 digits");
        }
        h.edit(clean(d.name()), clean(d.gotra()), clean(d.addressLine()), clean(d.city()), clean(d.state()), pin,
                clean(d.notes()), now());
        if (h.getName() == null) {
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
            throw new IllegalStateException("households need a tenant");
        }
        return t;
    }

    /** 404: no such household or member on this trust. */
    public static class NotFoundException extends RuntimeException { }

    /** 409: the devotee is already in a household; remove them there first. */
    public static class AlreadyInAHouseholdException extends RuntimeException { }
}
