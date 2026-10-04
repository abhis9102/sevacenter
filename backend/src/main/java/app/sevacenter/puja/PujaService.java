package app.sevacenter.puja;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.donation.DonationService;
import app.sevacenter.donation.OnlineDonationService;
import app.sevacenter.donation.OnlineDonationService.CreatedOrder;
import app.sevacenter.donation.PujaSettlement;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.Codes;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puja catalog and bookings (ADR 0016). Paid bookings go through the verified online-payment flow
 * and are confirmed only when it settles them; the dakshina never enters the donation ledger.
 */
@Service
public class PujaService implements PujaSettlement {

    static final int MAX_DAYS_AHEAD = 365;
    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final PujaRepository pujas;
    private final PujaBookingRepository bookings;
    private final PriestRepository priests;
    private final OnlineDonationService payments;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.system(DonationService.IST);

    public PujaService(PujaRepository pujas, PujaBookingRepository bookings, PriestRepository priests,
                       OnlineDonationService payments,
                       AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.pujas = pujas;
        this.bookings = bookings;
        this.priests = priests;
        this.payments = payments;
    }

    // --- catalog (LEADER+) ----------------------------------------------------------------------

    @Transactional
    public Puja save(Long id, PujaDetails d, long staffId) {
        Puja p = id == null ? new Puja(currentTenant()) : pujas.findById(id).orElseThrow(PujaNotFoundException::new);
        p.edit(d.name().strip(), blankToNull(d.deity()), blankToNull(d.description()), d.dakshinaPaise(), d.active(),
                d.displayOrder(), staffId, OffsetDateTime.now(clock));
        Puja saved = pujas.save(p);
        auditTrail.record(AuditAction.PUJA_SAVED, "puja", saved.getId(), null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Puja> catalog() {
        return pujas.catalog();
    }

    @Transactional(readOnly = true)
    public List<Puja> publicCatalog() {
        return pujas.activeCatalog();
    }

    // --- booking (public) -------------------------------------------------------------------------

    @Transactional
    public Booked book(long pujaId, BookingDetails d) {
        PujaBooking booking = bookings.saveAndFlush(newBooking(pujaId, d, true));
        audit.info("event=puja_booked tenant={} booking={} puja={} paise={}", currentTenant(), booking.getId(),
                pujaId, booking.getAmountPaise());
        CreatedOrder order = booking.getAmountPaise() == 0 ? null
                : payments.createPujaOrder(booking.getId(), booking.getAmountPaise(), booking.getDevoteeName(),
                        "Puja: " + booking.getPujaName());
        return new Booked(booking, order);
    }

    /**
     * Booked by staff for a walk-in devotee (ADR 0026): confirmed at once. A paid puja records how
     * the dakshina was taken; like online dakshina it is a seva fee, not an 80G donation.
     */
    @Transactional
    public PujaBooking bookAtCounter(long pujaId, BookingDetails d, String mode, String reference, long staffId) {
        PujaBooking booking = newBooking(pujaId, d, false);
        if (booking.getAmountPaise() > 0 && (mode == null || !COUNTER_MODES.contains(mode))) {
            throw new InvalidFieldException("mode", "say how the dakshina was paid");
        }
        String ref = booking.getAmountPaise() == 0 ? null : optional("reference", reference, 64);
        booking.bookedAtCounter(staffId, booking.getAmountPaise() == 0 ? null : mode, ref);
        booking = bookings.saveAndFlush(booking);
        auditTrail.record(AuditAction.PUJA_BOOKED_AT_COUNTER, "puja_booking", booking.getId(),
                booking.getAmountPaise() == 0 ? null : mode);
        return booking;
    }

    private PujaBooking newBooking(long pujaId, BookingDetails d, boolean contactRequired) {
        Puja puja = pujas.findById(pujaId).filter(Puja::isActive).orElseThrow(PujaNotFoundException::new);
        LocalDate today = LocalDate.now(clock);
        if (d.pujaDate() == null || d.pujaDate().isBefore(today) || d.pujaDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new InvalidFieldException("pujaDate", "choose a date from today up to a year ahead");
        }
        String name = required("devoteeName", d.devoteeName(), 120);
        String phone = d.phone() == null || d.phone().isBlank() ? null : DevoteeService.phone(d.phone());
        String email = d.email() == null || d.email().isBlank() ? null : d.email().strip().toLowerCase(Locale.ROOT);
        if (contactRequired && phone == null && email == null) {
            throw new InvalidFieldException("phone", "a phone number or an email is required");
        }
        if (email != null && (email.length() > 254 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
            throw new InvalidFieldException("email", "email is not valid");
        }
        return new PujaBooking(currentTenant(), puja, Codes.fresh(bookings::existsByBookingCode), name,
                optional("gotra", d.gotra(), 60), optional("nakshatra", d.nakshatra(), 60), optional("rashi", d.rashi(), 60),
                optional("familyNames", d.familyNames(), 500), d.pujaDate(), phone, email);
    }

    private static final java.util.Set<String> COUNTER_MODES = java.util.Set.of("CASH", "UPI", "CARD", "CHEQUE", "BANK_TRANSFER");

    // --- settlement (called by the payment flow, inside its transaction) --------------------------

    @Override
    @Transactional
    public String confirmPaid(long bookingId, String paymentRef) {
        PujaBooking b = bookings.lockById(bookingId).orElseThrow(PujaNotFoundException::new);
        if (!"AWAITING_PAYMENT".equals(b.getStatus())) {
            throw new OnlineDonationService.PaymentNotVerifiedException();
        }
        b.confirmPaid(paymentRef);
        return b.getBookingCode();
    }

    @Override
    @Transactional(readOnly = true)
    public String bookingCode(long bookingId) {
        return bookings.findById(bookingId).map(PujaBooking::getBookingCode).orElseThrow(PujaNotFoundException::new);
    }

    // --- the day's schedule (staff) ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PujaBooking> forDate(LocalDate date) {
        return bookings.forDate(date);
    }

    /** The priest marks it done; only a confirmed (paid or free) booking can be performed. */
    @Transactional
    public PujaBooking perform(long bookingId, long staffId) {
        PujaBooking b = bookings.lockById(bookingId).orElseThrow(PujaNotFoundException::new);
        if (!"CONFIRMED".equals(b.getStatus())) {
            throw new PujaConflictException("not_confirmed");
        }
        b.perform(staffId, OffsetDateTime.now(clock));
        audit.info("event=puja_performed tenant={} user={} booking={}", currentTenant(), staffId, bookingId);
        return b;
    }

    @Transactional
    public PujaBooking cancel(long bookingId, long staffId) {
        PujaBooking b = bookings.lockById(bookingId).orElseThrow(PujaNotFoundException::new);
        if ("PERFORMED".equals(b.getStatus())) {
            throw new PujaConflictException("already_performed");
        }
        b.cancel();
        audit.info("event=puja_cancelled tenant={} user={} booking={}", currentTenant(), staffId, bookingId);
        auditTrail.record(AuditAction.PUJA_BOOKING_CANCELLED, "puja_booking", bookingId, null);
        return b;
    }

    // --- priests (ADR 0027) -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Priest> priests() {
        return priests.listed();
    }

    @Transactional
    public Priest savePriest(Long id, String name, String phone, String specialties, boolean active, long staffId) {
        String clean = required("name", name, 120);
        if (priests.nameTaken(clean, id)) {
            throw new InvalidFieldException("name", "a priest with this name is already listed");
        }
        Priest p = id == null ? new Priest(currentTenant()) : priests.findById(id).orElseThrow(PujaNotFoundException::new);
        p.edit(clean, phone == null || phone.isBlank() ? null : DevoteeService.phone(phone),
                optional("specialties", specialties, 200), active, staffId, java.time.OffsetDateTime.now(clock));
        Priest saved = priests.saveAndFlush(p);
        auditTrail.record(AuditAction.PRIEST_SAVED, "priest", saved.getId(), active ? null : "inactive");
        return saved;
    }

    /** Who performs this sankalp; null clears it. Only an active priest, only an open booking. */
    @Transactional
    public PujaBooking assignPriest(long bookingId, Long priestId, long staffId) {
        PujaBooking b = bookings.lockById(bookingId).orElseThrow(PujaNotFoundException::new);
        if ("CANCELLED".equals(b.getStatus()) || "PERFORMED".equals(b.getStatus())) {
            throw new PujaConflictException("booking_closed");
        }
        if (priestId != null) {
            priests.findById(priestId).filter(Priest::isActive)
                    .orElseThrow(() -> new InvalidFieldException("priestId", "choose an active priest of this temple"));
        }
        b.assignPriest(priestId);
        auditTrail.record(AuditAction.PRIEST_ASSIGNED, "puja_booking", bookingId, priestId == null ? null : priestId.toString());
        return b;
    }

    /** Names for the roster, inactive priests included (past bookings keep their priest). */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> priestNames() {
        return priests.findAll().stream().collect(java.util.stream.Collectors.toMap(Priest::getId, Priest::getName));
    }

    // --- helpers ----------------------------------------------------------------------------------

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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    public record PujaDetails(String name, String deity, String description, long dakshinaPaise, boolean active,
                              int displayOrder) { }

    public record BookingDetails(String devoteeName, String gotra, String nakshatra, String rashi, String familyNames,
                                 LocalDate pujaDate, String phone, String email) { }

    /** A booking and, for a paid puja, the gateway order to pay it. */
    public record Booked(PujaBooking booking, CreatedOrder order) { }

    public static class PujaNotFoundException extends RuntimeException { }

    public static class PujaConflictException extends RuntimeException {
        public PujaConflictException(String reason) {
            super(reason);
        }
    }
}
