package app.sevacenter.household;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.devotee.Devotee;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.portal.DevoteeAccount;
import app.sevacenter.portal.DevoteeContact;
import app.sevacenter.portal.DevoteeLoginService;
import app.sevacenter.portal.OtpChannel;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A devotee's own household in My Mandir (ADR 0039). Who they are: the devotee record matching one
 * of their verified contacts (ADR 0038). The rules, all here:
 * <ul>
 *   <li>start a family: a signed-in devotee in no household; they become its head;</li>
 *   <li>the family's adults who manage it (head, spouse) add children (the parent's act is the
 *       parental consent DPDP asks for a minor), edit children's records, invite adults and remove
 *       members;</li>
 *   <li>an adult joins only by accepting an invite while signed in with the invited phone/email
 *       (their own consent), and only if they're in no other household;</li>
 *   <li>anyone can leave; nobody sees another adult's contact details.</li>
 * </ul>
 */
@Service
public class FamilyService {

    static final int INVITES_PER_DAY = 10;
    static final int INVITE_DAYS = 7;
    private static final Set<HouseholdMember.Relation> MANAGERS =
            Set.of(HouseholdMember.Relation.HEAD, HouseholdMember.Relation.SPOUSE);
    private static final Set<HouseholdMember.Relation> CHILDREN =
            Set.of(HouseholdMember.Relation.CHILD, HouseholdMember.Relation.GRANDCHILD);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Logger log = LoggerFactory.getLogger(FamilyService.class);

    private final HouseholdRepository households;
    private final HouseholdMemberRepository members;
    private final HouseholdInviteRepository invites;
    private final HouseholdService staffSide;
    private final DevoteeService devotees;
    private final DevoteeLoginService login;
    private final TenantRepository tenants;
    private final AuditTrail auditTrail;
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;
    private final Clock clock = Clock.system(IST);

    public FamilyService(HouseholdRepository households, HouseholdMemberRepository members,
                         HouseholdInviteRepository invites, HouseholdService staffSide, DevoteeService devotees,
                         DevoteeLoginService login, TenantRepository tenants, AuditTrail auditTrail,
                         ObjectProvider<JavaMailSender> mail, @Value("${sevacenter.mail.from:}") String from) {
        this.households = households;
        this.members = members;
        this.invites = invites;
        this.staffSide = staffSide;
        this.devotees = devotees;
        this.login = login;
        this.tenants = tenants;
        this.auditTrail = auditTrail;
        this.mail = mail;
        this.from = from.strip();
    }

    public record Member(long memberId, long devoteeId, String name, HouseholdMember.Relation relation, boolean child,
                         LocalDate dateOfBirth, boolean me, boolean canEdit, boolean canRemove) { }

    public record SentInvite(long id, OtpChannel channel, String contact, HouseholdMember.Relation relation,
                             OffsetDateTime expiresAt) { }

    public record Invitation(long id, String familyName, HouseholdMember.Relation relation) { }

    public record View(Long householdId, String name, String gotra, HouseholdMember.Relation myRelation,
                       boolean canManage, List<Member> members, List<SentInvite> sentInvites,
                       List<Invitation> invitations) { }

    @Transactional(readOnly = true)
    public View view(DevoteeAccount me) {
        List<String> contacts = contactsOf(me);
        List<Invitation> invitations = invites.openFor(contacts, now()).stream()
                .flatMap(i -> households.findById(i.getHouseholdId())
                        .map(h -> new Invitation(i.getId(), h.getName(), i.getRelation())).stream()).toList();
        Optional<HouseholdMember> mine = myMembership(contacts);
        if (mine.isEmpty()) {
            return new View(null, null, null, null, false, List.of(), List.of(), invitations);
        }
        HouseholdMember self = mine.get();
        Household h = households.findById(self.getHouseholdId()).orElseThrow();
        boolean manage = MANAGERS.contains(self.getRelation());
        List<Member> people = staffSide.members(h.getId()).stream().map(m -> {
            boolean child = isChild(m.member(), m.devotee());
            boolean isMe = m.member().getId().equals(self.getId());
            return new Member(m.member().getId(), m.devotee().getId(), m.devotee().getFullName(), m.member().getRelation(),
                    child, child && manage ? m.devotee().getDateOfBirth() : null, isMe, manage && child,
                    isMe || (manage && m.member().getRelation() != HouseholdMember.Relation.HEAD));
        }).toList();
        List<SentInvite> sent = manage ? invites.openOf(h.getId(), now()).stream()
                .map(i -> new SentInvite(i.getId(), i.getChannel(), i.getContact(), i.getRelation(), i.getExpiresAt()))
                .toList() : List.of();
        return new View(h.getId(), h.getName(), h.getGotra(), self.getRelation(), manage, people, sent, invitations);
    }

    /** Starts a family with the signed-in devotee as head (creating their devotee record if needed). */
    @Transactional
    public View start(DevoteeAccount me, String yourName, String familyName, String gotra) {
        List<String> contacts = contactsOf(me);
        if (myMembership(contacts).isPresent()) {
            throw new AlreadyInAHouseholdException();
        }
        Devotee self = myRecord(me, contacts, yourName);
        Household h = Household.startedByDevotee(currentTenant(), me.getId(), now());
        h.edit(required(familyName, "familyName"), clean(gotra), null, null, null, null, null, now());
        h = households.save(h);
        HouseholdMember m = new HouseholdMember(currentTenant(), h.getId(), self.getId(), HouseholdMember.Relation.HEAD, now());
        m.addedBy(me.getId());
        members.save(m);
        auditTrail.record(AuditAction.HOUSEHOLD_SAVED, "household", h.getId(), "by devotee " + me.getId());
        return view(me);
    }

    @Transactional
    public View addChild(DevoteeAccount me, String fullName, LocalDate dateOfBirth, HouseholdMember.Relation relation) {
        HouseholdMember self = manager(me);
        if (!CHILDREN.contains(relation)) {
            throw new InvalidFieldException("relation", "Add adults by inviting them");
        }
        Devotee child = devotees.createByDevotee(new Devotee.Details(required(fullName, "fullName"), null, null, null, null,
                null, null, dateOfBirth), me.getId());
        HouseholdMember m = new HouseholdMember(currentTenant(), self.getHouseholdId(), child.getId(), relation, now());
        m.addedBy(me.getId());
        members.save(m);
        auditTrail.record(AuditAction.HOUSEHOLD_MEMBER_ADDED, "household", self.getHouseholdId(), "by devotee " + me.getId());
        return view(me);
    }

    /** Managers edit a child's name and date of birth; never an adult's record. */
    @Transactional
    public View editChild(DevoteeAccount me, long memberId, String fullName, LocalDate dateOfBirth) {
        HouseholdMember self = manager(me);
        HouseholdMember m = memberOf(self.getHouseholdId(), memberId);
        Devotee d = devotees.get(m.getDevoteeId());
        if (!isChild(m, d)) {
            throw new NotAllowedException();
        }
        devotees.updateByDevotee(d.getId(), new Devotee.Details(required(fullName, "fullName"), d.getPhone(), d.getEmail(),
                d.getAddressLine(), d.getCity(), d.getState(), d.getPincode(), dateOfBirth), me.getId());
        return view(me);
    }

    @Transactional
    public View invite(DevoteeAccount me, OtpChannel channel, String rawContact, HouseholdMember.Relation relation) {
        HouseholdMember self = manager(me);
        if (relation == HouseholdMember.Relation.HEAD) {
            throw new InvalidFieldException("relation", "A family has one head");
        }
        String contact = DevoteeLoginService.normaliseContact(channel, rawContact);
        if (contactsOf(me).contains(contact)) {
            throw new InvalidFieldException("contact", "That's your own contact");
        }
        if (invites.countByHouseholdIdAndCreatedAtAfter(self.getHouseholdId(), now().minusDays(1)) >= INVITES_PER_DAY) {
            throw new app.sevacenter.auth.LoginThrottle.TooManyAttemptsException();
        }
        Household h = households.findById(self.getHouseholdId()).orElseThrow();
        HouseholdInvite inv = invites.save(new HouseholdInvite(currentTenant(), h.getId(), channel, contact, relation,
                me.getId(), now(), now().plusDays(INVITE_DAYS)));
        auditTrail.record(AuditAction.HOUSEHOLD_INVITE_SENT, "household", h.getId(), "by devotee " + me.getId());
        notifyInvitee(inv, h);
        return view(me);
    }

    @Transactional
    public View cancelInvite(DevoteeAccount me, long inviteId) {
        HouseholdMember self = manager(me);
        HouseholdInvite inv = invites.findById(inviteId).filter(i -> i.getHouseholdId().equals(self.getHouseholdId()))
                .orElseThrow(NotFoundException::new);
        inv.close(HouseholdInvite.Status.DECLINED);
        return view(me);
    }

    /** Joins the family the invite is for: only with the invited contact verified, and in no other household. */
    @Transactional
    public View accept(DevoteeAccount me, long inviteId, String yourName) {
        List<String> contacts = contactsOf(me);
        HouseholdInvite inv = invites.findById(inviteId)
                .filter(i -> i.open(now()) && contacts.contains(i.getContact()))
                .orElseThrow(NotFoundException::new);
        if (myMembership(contacts).isPresent()) {
            throw new AlreadyInAHouseholdException();
        }
        Devotee self = myRecord(me, contacts, yourName);
        HouseholdMember m = new HouseholdMember(currentTenant(), inv.getHouseholdId(), self.getId(), inv.getRelation(), now());
        m.addedBy(me.getId());
        members.save(m);
        inv.close(HouseholdInvite.Status.ACCEPTED);
        auditTrail.record(AuditAction.HOUSEHOLD_MEMBER_ADDED, "household", inv.getHouseholdId(), "invite accepted " + me.getId());
        return view(me);
    }

    @Transactional
    public View decline(DevoteeAccount me, long inviteId) {
        List<String> contacts = contactsOf(me);
        invites.findById(inviteId).filter(i -> i.open(now()) && contacts.contains(i.getContact()))
                .orElseThrow(NotFoundException::new).close(HouseholdInvite.Status.DECLINED);
        return view(me);
    }

    /** Managers remove anyone but the head; anyone can remove themselves (leave). */
    @Transactional
    public View remove(DevoteeAccount me, long memberId) {
        HouseholdMember self = myMembership(contactsOf(me)).orElseThrow(NotFoundException::new);
        HouseholdMember m = memberOf(self.getHouseholdId(), memberId);
        boolean leaving = m.getId().equals(self.getId());
        boolean allowed = leaving || (MANAGERS.contains(self.getRelation()) && m.getRelation() != HouseholdMember.Relation.HEAD);
        if (!allowed) {
            throw new NotAllowedException();
        }
        members.delete(m);
        auditTrail.record(AuditAction.HOUSEHOLD_MEMBER_REMOVED, "household", self.getHouseholdId(),
                (leaving ? "left " : "by devotee ") + me.getId());
        return view(me);
    }

    // --- helpers ----------------------------------------------------------------------------

    private List<String> contactsOf(DevoteeAccount me) {
        return login.contactsOf(me).stream().map(DevoteeContact::getContact).toList();
    }

    private Optional<HouseholdMember> myMembership(List<String> contacts) {
        return devotees.forContacts(contacts).stream().flatMap(d -> members.findByDevoteeId(d.getId()).stream()).findFirst();
    }

    private HouseholdMember manager(DevoteeAccount me) {
        HouseholdMember self = myMembership(contactsOf(me)).orElseThrow(NotFoundException::new);
        if (!MANAGERS.contains(self.getRelation())) {
            throw new NotAllowedException();
        }
        return self;
    }

    /** The devotee's own record: the one matching their contacts, or a new one from their account. */
    private Devotee myRecord(DevoteeAccount me, List<String> contacts, String yourName) {
        return devotees.forContacts(contacts).stream().findFirst().orElseGet(() -> {
            String name = clean(yourName) != null ? clean(yourName) : clean(me.getFullName());
            boolean email = me.getChannel() == OtpChannel.EMAIL;
            return devotees.createByDevotee(new Devotee.Details(required(name, "yourName"),
                    email ? null : me.getContact(), email ? me.getContact() : null, null, null, null, null, null), me.getId());
        });
    }

    private HouseholdMember memberOf(long householdId, long memberId) {
        return members.findById(memberId).filter(m -> m.getHouseholdId() == householdId).orElseThrow(NotFoundException::new);
    }

    /** A child: related as one, and under 18 (or with no date of birth yet). */
    private boolean isChild(HouseholdMember m, Devotee d) {
        if (!CHILDREN.contains(m.getRelation())) {
            return false;
        }
        LocalDate dob = d.getDateOfBirth();
        return dob == null || Period.between(dob, LocalDate.now(clock)).getYears() < 18;
    }

    private void notifyInvitee(HouseholdInvite inv, Household h) {
        JavaMailSender sender = mail.getIfAvailable();
        if (inv.getChannel() != OtpChannel.EMAIL || sender == null || from.isEmpty()) {
            return; // SMS invites: the family tells them; they see it when they sign in
        }
        try {
            String trust = tenants.findById(currentTenant()).map(t -> t.getName()).orElse("your temple");
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(inv.getContact());
            m.setSubject("You're invited to join your family on " + trust);
            m.setText("A family member invited you to join \"" + h.getName() + "\" on " + trust + "'s Mandir Center.\n\n"
                    + "To accept, sign in to My Mandir on the temple's website with this email address. The invitation "
                    + "is open for " + INVITE_DAYS + " days. If you don't know about this, ignore this email.\n");
            sender.send(m);
        } catch (RuntimeException e) {
            log.warn("household invite email failed: {}", e.getClass().getSimpleName());
        }
    }

    private static String required(String s, String field) {
        String c = clean(s);
        if (c == null) {
            throw new InvalidFieldException(field, field + " is required");
        }
        return c;
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

    /** 404: no such household, member or open invite for this devotee. */
    public static class NotFoundException extends RuntimeException { }

    /** 403: this devotee may not do that in their household. */
    public static class NotAllowedException extends RuntimeException { }

    /** 409: already in a household. */
    public static class AlreadyInAHouseholdException extends RuntimeException { }
}
