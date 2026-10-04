package app.sevacenter.temple;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.donation.DonationService;
import app.sevacenter.donation.OnlineDonationService;
import app.sevacenter.event.EventService;
import app.sevacenter.puja.PujaService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import app.sevacenter.web.InvalidFieldException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trust's public temple page (ADR 0017): GET is public on the trust's host and says which
 * services are actually available; LEADER+ edits it. Nothing is shown that the temple didn't enter.
 * Darshan hours, a same-day status override and the aarti timetable: ADR 0024.
 */
@RestController
public class TempleController {

    /** Enough for any temple's day; keeps the public payload and the editor bounded. */
    static final int MAX_AARTIS = 12;

    private final TempleProfileRepository profiles;
    private final TempleAartiRepository aartis;
    private final TenantRepository tenants;
    private final OnlineDonationService payments;
    private final EventService events;
    private final PujaService pujas;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.system(DonationService.IST);

    public TempleController(TempleProfileRepository profiles, TempleAartiRepository aartis, TenantRepository tenants, OnlineDonationService payments,
                            EventService events, PujaService pujas, AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.profiles = profiles;
        this.aartis = aartis;
        this.tenants = tenants;
        this.payments = payments;
        this.events = events;
        this.pujas = pujas;
    }

    @GetMapping("/api/v1/public/temple")
    @Transactional(readOnly = true)
    public ResponseEntity<PublicTemple> publicPage() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.notFound().build();
        }
        String name = tenants.findById(tenantId).map(t -> t.getName()).orElse(null);
        TempleProfile p = profiles.findById(tenantId).orElse(null);
        LocalDate today = LocalDate.now(clock);
        return ResponseEntity.ok(new PublicTemple(name,
                p == null ? null : p.getDeity(), p == null ? null : p.getAddress(), p == null ? null : p.getHelpline(),
                p == null ? null : p.getTimings(), p == null ? null : p.getAnnouncement(), hours(p), timetable(),
                status(p, today), p == null ? null : p.noteFor(today),
                p == null ? LunarCalendar.AMANTA : LunarCalendar.valueOf(p.getCalendar()),
                payments.settings().isPresent(), !events.upcoming().isEmpty(), !pujas.publicCatalog().isEmpty()));
    }

    @PutMapping("/api/v1/temple")
    @PreAuthorize("hasRole('LEADER')")
    @Transactional
    public Profile save(@Valid @RequestBody Profile r, @AuthenticationPrincipal StaffUser staff) {
        long tenantId = TenantContext.get();
        Hours h = r.hours() == null ? Hours.NONE : r.hours();
        h.validate();
        List<Aarti> list = r.aartis() == null ? List.of() : r.aartis();
        if (list.size() > MAX_AARTIS) {
            throw new InvalidFieldException("aartis", "at most " + MAX_AARTIS + " aartis");
        }
        TempleProfile p = locked(tenantId, staff.userId());
        p.edit(clean(r.deity()), clean(r.address()),
                r.helpline() == null || r.helpline().isBlank() ? null : DevoteeService.phone(r.helpline()),
                clean(r.timings()), clean(r.announcement()), staff.userId(), OffsetDateTime.now(clock));
        p.schedule(h.morningOpen(), h.morningClose(), h.eveningOpen(), h.eveningClose(),
                (r.calendar() == null ? LunarCalendar.AMANTA : r.calendar()).name());
        profiles.save(p);
        aartis.deleteForTenant(tenantId);
        aartis.flush();
        for (Aarti x : list) {
            aartis.save(new TempleAarti(tenantId, clean(x.name()), x.at(), clean(x.description())));
        }
        auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_profile", null, null);
        return profile(p);
    }

    /** "Closed today for grahan": holds for today (IST) only, then the hours apply again. */
    @PutMapping("/api/v1/temple/status")
    @PreAuthorize("hasRole('LEADER')")
    @Transactional
    public Profile setStatus(@Valid @RequestBody StatusRequest r, @AuthenticationPrincipal StaffUser staff) {
        long tenantId = TenantContext.get();
        TempleProfile p = locked(tenantId, staff.userId());
        p.override(r.status() == null ? null : r.status().name(), LocalDate.now(clock), clean(r.note()));
        profiles.save(p);
        auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_profile", null, null);
        return profile(p);
    }

    @GetMapping("/api/v1/temple")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public Profile get() {
        return profile(profiles.findById(TenantContext.get()).orElse(null));
    }

    private Profile profile(TempleProfile p) {
        if (p == null) {
            return new Profile(null, null, null, null, null, null, List.of(), LunarCalendar.AMANTA, null, null);
        }
        LocalDate today = LocalDate.now(clock);
        return new Profile(p.getDeity(), p.getAddress(), p.getHelpline(), p.getTimings(), p.getAnnouncement(), hours(p),
                timetable(), LunarCalendar.valueOf(p.getCalendar()),
                status(p, today), p.noteFor(today));
    }

    private static DarshanStatus status(TempleProfile p, LocalDate today) {
        String s = p == null ? null : p.overrideFor(today);
        return s == null ? null : DarshanStatus.valueOf(s);
    }

    /**
     * This temple's row, created if missing and locked for the rest of the transaction, so parallel
     * saves can't interleave their timetable delete-and-insert (found by DAST) or race to create it.
     */
    private TempleProfile locked(long tenantId, long staffId) {
        profiles.ensureRow(tenantId, staffId);
        return profiles.lockById(tenantId).orElseThrow();
    }

    private static Hours hours(TempleProfile p) {
        if (p == null || (p.getMorningOpen() == null && p.getEveningOpen() == null)) {
            return null;
        }
        return new Hours(p.getMorningOpen(), p.getMorningClose(), p.getEveningOpen(), p.getEveningClose());
    }

    private List<Aarti> timetable() {
        return aartis.timetable().stream().map(a -> new Aarti(a.getName(), a.getAt(), a.getDescription())).toList();
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.indexOf('\0') >= 0) {
            throw new app.sevacenter.web.InvalidFieldException("text", "text contains a NUL character");
        }
        return s.strip();
    }

    public enum DarshanStatus { OPEN, CLOSED }

    /** Amanta months end on the new moon (most of the south and west); purnimanta on the full moon. */
    public enum LunarCalendar { AMANTA, PURNIMANTA }

    /** Two darshan sessions; either may be absent (null pair), each must open before it closes. */
    public record Hours(
            @Schema(example = "05:30") LocalTime morningOpen, @Schema(example = "12:30") LocalTime morningClose,
            @Schema(example = "16:00") LocalTime eveningOpen, @Schema(example = "21:30") LocalTime eveningClose) {
        static final Hours NONE = new Hours(null, null, null, null);

        void validate() {
            pair("morning", morningOpen, morningClose);
            pair("evening", eveningOpen, eveningClose);
            if (morningClose != null && eveningOpen != null && eveningOpen.isBefore(morningClose)) {
                throw new InvalidFieldException("hours", "the evening session must start after the morning one ends");
            }
        }

        private static void pair(String session, LocalTime open, LocalTime close) {
            if ((open == null) != (close == null)) {
                throw new InvalidFieldException("hours", session + " needs both an opening and a closing time");
            }
            if (open != null && !open.isBefore(close)) {
                throw new InvalidFieldException("hours", session + " must open before it closes");
            }
        }
    }

    public record Aarti(
            @Schema(example = "Kakad Aarti") @NotBlank @Size(max = 60) String name,
            @Schema(example = "05:30") @NotNull LocalTime at,
            @Schema(example = "Waking the deity with bhajans") @Size(max = 200) String description) { }

    public record StatusRequest(
            @Schema(example = "CLOSED", description = "null returns to the regular hours") DarshanStatus status,
            @Schema(example = "Closed for the lunar eclipse; reopens tomorrow 5:30 am") @Size(max = 120) String note) { }

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record Profile(
            @Schema(example = "Shri Siddheshwar") @Size(max = 120) String deity,
            @Schema(example = "1 Temple Road, Pune 411001") @Size(max = 400) String address,
            @Schema(example = "98765 43210") @Size(max = 30) String helpline,
            @Schema(example = "Mondays open till 10:30 pm") @Size(max = 500) String timings,
            @Schema(example = "Annual utsav begins on Sunday.") @Size(max = 1000) String announcement,
            Hours hours,
            @Size(max = MAX_AARTIS) List<@Valid Aarti> aartis,
            @Schema(example = "AMANTA") LunarCalendar calendar,
            @Schema(description = "Read-only here: set with PUT /temple/status", accessMode = Schema.AccessMode.READ_ONLY)
            DarshanStatus status,
            @Schema(accessMode = Schema.AccessMode.READ_ONLY) String statusNote) { }

    /** Public: the temple's own words plus which services it actually offers right now. */
    public record PublicTemple(String trustName, String deity, String address, String helpline, String timings,
                               String announcement, Hours hours, List<Aarti> aartis, DarshanStatus status,
                               String statusNote, LunarCalendar calendar,
                               boolean onlineDonations, boolean upcomingEvents, boolean pujaBooking) { }
}
