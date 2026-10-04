package app.sevacenter.sevak;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sevak (volunteer) signups (ADR 0015). Authorization on the controllers; tenancy is RLS. */
@Service
public class SevakService {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    /** Plenty for a temple; keeps a team's card readable. */
    static final int MAX_SHIFTS = 8;

    /** The icons a team can show; the frontend draws exactly these (lib/sevak.ts SEVA_ICON_KEYS). */
    static final java.util.Set<String> ICONS = java.util.Set.of(
            "hands", "kitchen", "meal", "prasad", "cow", "queue", "crowd", "elder", "wheelchair", "child", "flower", "garland", "rangoli", "lamp", "bell", "flag", "temple", "music", "mic", "footwear", "broom", "water", "first-aid", "shield", "parking", "transport", "tent", "light", "tools", "camera", "book", "info", "phone", "clipboard", "rupee", "gift", "leaf", "star");

    private final SevakSignupRepository signups;
    private final SevaTeamRepository teams;
    private final SevaShiftRepository shifts;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.systemUTC();

    public SevakService(SevakSignupRepository signups, SevaTeamRepository teams, SevaShiftRepository shifts,
                        AuditTrail auditTrail) {
        this.signups = signups;
        this.teams = teams;
        this.shifts = shifts;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public SevakSignup signUp(String fullName, String phone, String email, String sevaAreas, String availability,
                              String notes) {
        return create(fullName, phone, email, sevaAreas, availability, notes);
    }

    /** A volunteer registered by staff (ADR 0028), optionally approved and placed in a team at once. */
    @Transactional
    public SevakSignup register(String fullName, String phone, String email, String sevaAreas, String availability,
                                String notes, boolean approved, Long teamId, String duty, long staffId) {
        SevakSignup s = create(fullName, phone, email, sevaAreas, availability, notes);
        s.registeredBy(staffId, approved || teamId != null, OffsetDateTime.now(clock));
        if (teamId != null) {
            s.assign(liveTeam(teamId).getId(), optional("duty", duty, 120));
        }
        auditTrail.record(AuditAction.SEVAK_REGISTERED, "sevak_signup", s.getId(), null);
        return s;
    }

    private SevakSignup create(String fullName, String phone, String email, String sevaAreas, String availability,
                              String notes) {
        String name = required("fullName", fullName, 120);
        String areas = required("sevaAreas", sevaAreas, 300);
        String cleanPhone = phone == null || phone.isBlank() ? null : DevoteeService.phone(phone);
        String cleanEmail = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
        if (cleanPhone == null && cleanEmail == null) {
            throw new InvalidFieldException("phone", "a phone number or an email is required");
        }
        if (cleanEmail != null && (cleanEmail.length() > 254 || !cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
            throw new InvalidFieldException("email", "email is not valid");
        }
        SevakSignup saved = signups.save(new SevakSignup(currentTenant(), name, cleanPhone, cleanEmail, areas,
                optional("availability", availability, 300), optional("notes", notes, 1000)));
        audit.info("event=sevak_signup tenant={} signup={}", currentTenant(), saved.getId());
        return saved;
    }

    /** Place an approved (or new, which approves it) volunteer in a team; null team releases them. */
    @Transactional
    public SevakSignup assign(long id, Long teamId, String duty, long staffId) {
        SevakSignup s = signups.findById(id).filter(x -> x.getRemovedAt() == null).orElseThrow(SignupNotFoundException::new);
        if ("DECLINED".equals(s.getStatus()) && teamId != null) {
            throw new InvalidFieldException("teamId", "this offer was declined");
        }
        if ("NEW".equals(s.getStatus()) && teamId != null) {
            s.review(true, staffId, OffsetDateTime.now(clock));
        }
        s.assign(teamId == null ? null : liveTeam(teamId).getId(), optional("duty", duty, 120));
        auditTrail.record(AuditAction.SEVAK_ASSIGNED, "sevak_signup", id, teamId == null ? null : teamId.toString());
        return s;
    }

    /** Hidden from every list (and from My Mandir); the row stays for the audit trail. */
    @Transactional
    public void remove(long id, long staffId) {
        SevakSignup s = signups.findById(id).filter(x -> x.getRemovedAt() == null).orElseThrow(SignupNotFoundException::new);
        s.remove(OffsetDateTime.now(clock));
        auditTrail.record(AuditAction.SEVAK_REMOVED, "sevak_signup", id, null);
    }

    // --- teams (volunteer activities) ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SevaTeam> teams() {
        return teams.live();
    }

    @Transactional(readOnly = true)
    public List<SevaShift> allShifts() {
        return teams.shifts();
    }

    public record ShiftDetails(String name, java.time.LocalTime startsAt, java.time.LocalTime endsAt) { }

    @Transactional
    public SevaTeam saveTeam(Long id, String name, String description, Integer targetCount, String icon,
                             List<ShiftDetails> shiftList, long staffId) {
        String clean = required("name", name, 80);
        if (teams.nameTaken(clean, id)) {
            throw new InvalidFieldException("name", "a team with this name already exists");
        }
        if (targetCount != null && (targetCount < 1 || targetCount > 1000)) {
            throw new InvalidFieldException("targetCount", "target must be between 1 and 1000");
        }
        if (icon != null && !ICONS.contains(icon)) {
            throw new InvalidFieldException("icon", "choose one of the listed icons");
        }
        List<ShiftDetails> list = shiftList == null ? List.of() : shiftList;
        if (list.size() > MAX_SHIFTS) {
            throw new InvalidFieldException("shifts", "at most " + MAX_SHIFTS + " shifts");
        }
        for (ShiftDetails sh : list) {
            required("shifts", sh.name(), 60);
            if (sh.startsAt() == null || sh.endsAt() == null || sh.startsAt().equals(sh.endsAt())) {
                throw new InvalidFieldException("shifts", "each shift needs a start and a different end time");
            }
        }
        SevaTeam t = id == null ? new SevaTeam(currentTenant()) : liveTeam(id);
        t.edit(clean, optional("description", description, 300), targetCount, icon, staffId, OffsetDateTime.now(clock));
        try {
            t = teams.saveAndFlush(t);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Two saves of one team name at once both pass the check above; the unique index lets
            // one win, and the other gets the same answer as a plain duplicate (as for priests).
            throw new InvalidFieldException("name", "a team with this name already exists");
        }
        teams.deleteShifts(t.getId(), currentTenant());
        for (ShiftDetails sh : list) {
            shifts.save(new SevaShift(currentTenant(), t.getId(), sh.name().strip(), sh.startsAt(), sh.endsAt()));
        }
        auditTrail.record(AuditAction.SEVA_TEAM_SAVED, "seva_team", t.getId(), null);
        return t;
    }

    /** Deletes a team from every screen and releases its volunteers; the row stays for the audit trail. */
    @Transactional
    public void deleteTeam(long id, long staffId) {
        SevaTeam t = liveTeam(id);
        signups.inTeam(id).forEach(s -> s.assign(null, null));
        t.delete(staffId, OffsetDateTime.now(clock));
        auditTrail.record(AuditAction.SEVA_TEAM_DELETED, "seva_team", id, null);
    }

    private SevaTeam liveTeam(long id) {
        return teams.findById(id).filter(t -> !t.isDeleted()).orElseThrow(TeamNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<SevakSignup> list() {
        return signups.newestFirst(PageRequest.of(0, 500));
    }

    @Transactional
    public SevakSignup review(long id, boolean approve, long staffId) {
        SevakSignup s = signups.findById(id).orElseThrow(SignupNotFoundException::new);
        s.review(approve, staffId, OffsetDateTime.now(clock));
        audit.info("event=sevak_reviewed tenant={} user={} signup={} approved={}", currentTenant(), staffId, id, approve);
        auditTrail.record(AuditAction.SEVAK_REVIEWED, "sevak_signup", id, approve ? "approved" : "declined");
        return s;
    }

    private static String required(String field, String value, int max) {
        String v = optional(field, value, max);
        if (v == null) {
            throw new InvalidFieldException(field, field + " is required");
        }
        return v;
    }

    private static String optional(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > max || v.indexOf('\0') >= 0) {
            throw new InvalidFieldException(field, field + " is too long");
        }
        return v;
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    /** 404: no such live team here (RLS hides other trusts' teams). */
    public static class TeamNotFoundException extends RuntimeException { }

    /** 404: unknown here (RLS hides other trusts' signups). */
    public static class SignupNotFoundException extends RuntimeException { }
}
