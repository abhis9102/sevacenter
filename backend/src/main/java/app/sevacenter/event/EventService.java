package app.sevacenter.event;

import java.security.SecureRandom;
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

/** Events and their registration passes (ADR 0014). Authorization on the controllers; tenancy is RLS. */
@Service
public class EventService {

    /** No 0/O, 1/I: codes are read aloud and typed at the gate. 32 symbols x 10 = 50 bits. */
    static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int MAX_PER_PASS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final EventRepository events;
    private final EventPassRepository passes;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.systemUTC();

    public EventService(EventRepository events, EventPassRepository passes, AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.events = events;
        this.passes = passes;
    }

    // --- staff ---------------------------------------------------------------------------------

    @Transactional
    public Event create(EventDetails d, long staffId) {
        Event e = new Event(currentTenant(), staffId, now());
        apply(e, d, staffId);
        return events.save(e);
    }

    @Transactional
    public Event update(long id, EventDetails d, long staffId) {
        Event e = lock(id);
        if (e.getStatus() == EventStatus.CANCELLED) {
            throw new EventConflictException("event_cancelled");
        }
        apply(e, d, staffId);
        return e;
    }

    @Transactional
    public Event changeStatus(long id, EventStatus status, long staffId) {
        Event e = lock(id);
        if (e.getStatus() == EventStatus.CANCELLED) {
            throw new EventConflictException("event_cancelled");
        }
        e.changeStatus(status, staffId, now());
        audit.info("event=event_status tenant={} user={} event={} status={}", currentTenant(), staffId, id, status);
        auditTrail.record(AuditAction.EVENT_STATUS_CHANGED, "event", id, status.name());
        return e;
    }

    /**
     * ADR 0029: a draft or cancelled event can be deleted; a published one is cancelled first, so
     * registered devotees see "cancelled". The row and its passes stay (My Mandir, audit trail).
     */
    @Transactional
    public void delete(long id, long staffId) {
        Event e = lock(id);
        if (e.getStatus() == EventStatus.PUBLISHED) {
            throw new EventConflictException("cancel_first");
        }
        e.delete(staffId, now());
        audit.info("event=event_deleted tenant={} user={} event={}", currentTenant(), staffId, id);
        auditTrail.record(AuditAction.EVENT_DELETED, "event", id, null);
    }

    @Transactional(readOnly = true)
    public List<Event> list() {
        return events.newestFirst(PageRequest.of(0, 200));
    }

    @Transactional(readOnly = true)
    public Event get(long id) {
        return events.live(id).orElseThrow(EventNotFoundException::new);
    }

    /** Staff changes to one event serialize with each other and with seat counting. */
    private Event lock(long id) {
        return events.lockById(id).orElseThrow(EventNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<EventPass> passesOf(long eventId) {
        get(eventId);
        return passes.findByEventIdOrderByCreatedAtAsc(eventId);
    }

    @Transactional(readOnly = true)
    public long seatsTaken(long eventId) {
        return passes.seatsTaken(eventId);
    }

    /** At the gate: checks a pass in once. A re-scan says when it was already used. */
    @Transactional
    public EventPass checkIn(long eventId, String rawCode, long staffId) {
        get(eventId);
        EventPass pass = passes.lockByCode(normaliseCode(rawCode))
                .filter(p -> p.getEventId() == eventId)
                .orElseThrow(PassNotFoundException::new);
        if (!pass.isActive()) {
            throw new EventConflictException("pass_cancelled");
        }
        if (pass.getCheckedInAt() != null) {
            throw new AlreadyCheckedInException(pass);
        }
        pass.checkIn(staffId, now());
        audit.info("event=pass_checked_in tenant={} user={} event={} pass={}", currentTenant(), staffId, eventId, pass.getId());
        return pass;
    }

    @Transactional
    public EventPass cancelPass(long eventId, long passId, long staffId) {
        get(eventId);
        EventPass pass = passes.findById(passId).filter(p -> p.getEventId() == eventId)
                .orElseThrow(PassNotFoundException::new);
        pass.cancel();
        audit.info("event=pass_cancelled tenant={} user={} pass={}", currentTenant(), staffId, passId);
        return pass;
    }

    // --- public --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Event> upcoming() {
        return events.upcomingPublished(now(), PageRequest.of(0, 50));
    }

    /**
     * Registers attendees for a published event. The event row is locked while seats are counted,
     * so two simultaneous registrations can't oversell the last places.
     */
    @Transactional
    public EventPass register(long eventId, String name, int count, String phone, String email) {
        return issue(eventId, name, count, phone, email, null);
    }

    /**
     * A pass issued by staff at the counter or gate (ADR 0026): contact optional for a walk-in,
     * and possible until the event ends even after online registration closes. Seats still count.
     */
    @Transactional
    public EventPass issueAtCounter(long eventId, String name, int count, String phone, String email, long staffId) {
        EventPass pass = issue(eventId, name, count, phone, email, staffId);
        auditTrail.record(AuditAction.PASS_ISSUED_AT_COUNTER, "event_pass", pass.getId(), String.valueOf(count));
        return pass;
    }

    private EventPass issue(long eventId, String name, int count, String phone, String email, Long staffId) {
        String attendee = name == null ? "" : name.strip();
        if (attendee.isEmpty() || attendee.length() > 120 || attendee.indexOf('\0') >= 0) {
            throw new InvalidFieldException("name", "name is required");
        }
        if (count < 1 || count > MAX_PER_PASS) {
            throw new InvalidFieldException("count", "count must be between 1 and " + MAX_PER_PASS);
        }
        String cleanPhone = phone == null || phone.isBlank() ? null : DevoteeService.phone(phone);
        String cleanEmail = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
        if (staffId == null && cleanPhone == null && cleanEmail == null) {
            throw new InvalidFieldException("phone", "a phone number or an email is required");
        }
        if (cleanEmail != null && (cleanEmail.length() > 254 || !cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
            throw new InvalidFieldException("email", "email is not valid");
        }
        Event e = events.lockById(eventId).filter(ev -> ev.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(EventNotFoundException::new);
        if (staffId == null ? !e.takesRegistrations(now()) : !e.takesCounterPasses(now())) {
            throw new EventConflictException("registration_closed");
        }
        if (e.getCapacity() != null && passes.seatsTaken(eventId) + count > e.getCapacity()) {
            throw new EventConflictException("event_full");
        }
        EventPass pass = new EventPass(currentTenant(), eventId, newCode(), attendee, count, cleanPhone, cleanEmail);
        if (staffId != null) {
            pass.issuedBy(staffId);
        }
        pass = passes.save(pass);
        audit.info("event=pass_registered tenant={} event={} pass={} count={} counter={}", currentTenant(), eventId,
                pass.getId(), count, staffId != null);
        return pass;
    }

    // --- helpers -------------------------------------------------------------------------------

    private void apply(Event e, EventDetails d, long staffId) {
        // Plausible times only. JSON numbers (and numeric strings) parse as epoch seconds, so "10"
        // would otherwise be accepted as 1 January 1970 (found by DAST).
        OffsetDateTime earliest = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        if (d.startsAt().isBefore(earliest) || d.startsAt().isAfter(now().plusYears(5))) {
            throw new InvalidFieldException("startsAt", "startsAt must be a real date within the next five years");
        }
        if (!d.endsAt().isAfter(d.startsAt())) {
            throw new InvalidFieldException("endsAt", "endsAt must be after startsAt");
        }
        e.edit(d.title().strip(), d.description() == null || d.description().isBlank() ? null : d.description().strip(),
                d.startsAt(), d.endsAt(), d.capacity(), d.registrationOpen(), staffId, now());
    }

    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!passes.existsByPassCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("could not allocate a pass code");
    }

    /** Volunteers type codes in any case, with spaces or dashes. */
    static String normaliseCode(String raw) {
        String c = raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z2-9]{10}")) {
            throw new PassNotFoundException();
        }
        return c;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    public record EventDetails(String title, String description, OffsetDateTime startsAt, OffsetDateTime endsAt,
                               Integer capacity, boolean registrationOpen) { }

    /** 404: unknown here (RLS), or not public. */
    public static class EventNotFoundException extends RuntimeException { }

    /** 404: no such pass for this event on this trust's host. */
    public static class PassNotFoundException extends RuntimeException { }

    /** 409 with a reason code. */
    public static class EventConflictException extends RuntimeException {
        public EventConflictException(String reason) {
            super(reason);
        }
    }

    /** 409 already_checked_in, saying when. */
    public static class AlreadyCheckedInException extends RuntimeException {
        private final transient EventPass pass;

        public AlreadyCheckedInException(EventPass pass) {
            super("already_checked_in");
            this.pass = pass;
        }

        public EventPass pass() {
            return pass;
        }
    }
}
