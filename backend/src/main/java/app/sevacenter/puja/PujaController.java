package app.sevacenter.puja;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.donation.Money;
import app.sevacenter.donation.OnlineDonationService.CreatedOrder;
import app.sevacenter.donation.RateLimiter;
import app.sevacenter.puja.PujaService.BookingDetails;
import app.sevacenter.puja.PujaService.PujaDetails;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pujas (ADR 0016). LEADER: catalog; bookings with contacts; cancel. MEMBER (e.g. a priest): the
 * day's schedule without contacts; mark performed. Public: catalog and booking (rate-limited).
 */
@RestController
public class PujaController {

    private final PujaService service;
    private final RateLimiter rateLimiter;

    public PujaController(PujaService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    // --- staff ------------------------------------------------------------------------------------

    @GetMapping("/api/v1/pujas")
    @PreAuthorize("hasRole('MEMBER')")
    public List<PujaResponse> catalog() {
        return service.catalog().stream().map(PujaResponse::of).toList();
    }

    @PostMapping("/api/v1/pujas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public PujaResponse create(@Valid @RequestBody PujaRequest r, @AuthenticationPrincipal StaffUser staff) {
        return PujaResponse.of(service.save(null, r.details(), staff.userId()));
    }

    @PutMapping("/api/v1/pujas/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    public PujaResponse update(@PathVariable long id, @Valid @RequestBody PujaRequest r, @AuthenticationPrincipal StaffUser staff) {
        return PujaResponse.of(service.save(id, r.details(), staff.userId()));
    }

    /** The day's schedule. Contacts only for LEADER+, not for the priest at the altar. */
    @GetMapping("/api/v1/puja-bookings")
    @PreAuthorize("hasRole('MEMBER')")
    public List<BookingResponse> forDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @AuthenticationPrincipal StaffUser staff) {
        boolean contacts = staff.role() != Role.MEMBER;
        Map<Long, String> names = service.priestNames();
        return service.forDate(date).stream().map(b -> BookingResponse.of(b, contacts, names)).toList();
    }

    /**
     * A walk-in devotee booked at the counter (ADR 0026). LEADER+, like recording a donation: a
     * paid puja means money taken in person.
     */
    @PostMapping("/api/v1/puja-bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public BookingResponse bookAtCounter(@Valid @RequestBody CounterBookingRequest r, @AuthenticationPrincipal StaffUser staff) {
        return BookingResponse.of(service.bookAtCounter(r.pujaId(), new PujaService.BookingDetails(r.devoteeName(),
                r.gotra(), r.nakshatra(), r.rashi(), r.familyNames(), r.pujaDate(), r.phone(), r.email()),
                r.mode(), r.reference(), staff.userId()), true, service.priestNames());
    }

    /** Assign (or clear, with null) the priest who performs this sankalp (ADR 0027). */
    @PostMapping("/api/v1/puja-bookings/{id:\\d+}/priest")
    @PreAuthorize("hasRole('LEADER')")
    public BookingResponse assignPriest(@PathVariable long id, @RequestBody PriestAssignment r, @AuthenticationPrincipal StaffUser staff) {
        return BookingResponse.of(service.assignPriest(id, r.priestId(), staff.userId()), true, service.priestNames());
    }

    // --- priests (ADR 0027) -----------------------------------------------------------------------

    @GetMapping("/api/v1/priests")
    @PreAuthorize("hasRole('MEMBER')")
    public List<PriestResponse> priests(@AuthenticationPrincipal StaffUser staff) {
        boolean contacts = staff.role() != Role.MEMBER;
        return service.priests().stream().map(p -> PriestResponse.of(p, contacts)).toList();
    }

    @PostMapping("/api/v1/priests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public PriestResponse addPriest(@Valid @RequestBody PriestRequest r, @AuthenticationPrincipal StaffUser staff) {
        return PriestResponse.of(service.savePriest(null, r.name(), r.phone(), r.specialties(),
                r.active() == null || r.active(), staff.userId()), true);
    }

    @PutMapping("/api/v1/priests/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    public PriestResponse editPriest(@PathVariable long id, @Valid @RequestBody PriestRequest r, @AuthenticationPrincipal StaffUser staff) {
        return PriestResponse.of(service.savePriest(id, r.name(), r.phone(), r.specialties(),
                r.active() == null || r.active(), staff.userId()), true);
    }

    @PostMapping("/api/v1/puja-bookings/{id:\\d+}/performed")
    @PreAuthorize("hasRole('MEMBER')")
    public BookingResponse performed(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return BookingResponse.of(service.perform(id, staff.userId()), staff.role() != Role.MEMBER, service.priestNames());
    }

    @PostMapping("/api/v1/puja-bookings/{id:\\d+}/cancel")
    @PreAuthorize("hasRole('LEADER')")
    public BookingResponse cancel(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return BookingResponse.of(service.cancel(id, staff.userId()), true, service.priestNames());
    }

    // --- public -----------------------------------------------------------------------------------

    @GetMapping("/api/v1/public/pujas")
    public ResponseEntity<List<PujaResponse>> publicCatalog() {
        if (TenantContext.get() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(service.publicCatalog().stream().map(PujaResponse::of).toList());
    }

    @PostMapping("/api/v1/public/pujas/{id:\\d+}/book")
    @ResponseStatus(HttpStatus.CREATED)
    public BookedResponse book(@PathVariable long id, @Valid @RequestBody BookRequest r, HttpServletRequest request) {
        if (TenantContext.get() == null) {
            throw new PujaService.PujaNotFoundException();
        }
        if (!rateLimiter.tryAcquire("puja:" + request.getRemoteAddr())) {
            throw new app.sevacenter.auth.LoginThrottle.TooManyAttemptsException();
        }
        PujaService.Booked b = service.book(id, new BookingDetails(r.devoteeName(), r.gotra(), r.nakshatra(), r.rashi(),
                r.familyNames(), r.pujaDate(), r.phone(), r.email()));
        CreatedOrder o = b.order();
        return new BookedResponse(b.booking().getBookingCode(), b.booking().getStatus(), b.booking().getPujaName(),
                b.booking().getPujaDate(), Money.toRupees(b.booking().getAmountPaise()),
                o == null ? null : o.orderId(), o == null ? null : o.keyId(), o == null ? 0 : o.amountPaise());
    }

    // --- records ----------------------------------------------------------------------------------

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record PujaRequest(
            @Schema(example = "Rudrabhishek") @NotBlank @Size(max = 120) String name,
            @Schema(example = "Shiva") @Size(max = 120) String deity,
            @Schema(example = "Abhishek with milk and bilva") @Size(max = 1000) String description,
            @Schema(example = "1100") @NotBlank @Size(max = 20) String dakshina,
            @Schema(example = "true") boolean active,
            @Schema(example = "1") @Min(0) @Max(1000) int displayOrder) {
        PujaDetails details() {
            long paise = "0".equals(dakshina.strip()) ? 0 : Money.toPaise("dakshina", dakshina.strip());
            return new PujaDetails(name, deity, description, paise, active, displayOrder);
        }
    }

    public record PujaResponse(long id, String name, String deity, String description, String dakshina, boolean active,
                               int displayOrder) {
        static PujaResponse of(Puja p) {
            return new PujaResponse(p.getId(), p.getName(), p.getDeity(), p.getDescription(),
                    Money.toRupees(p.getDakshinaPaise()), p.isActive(), p.getDisplayOrder());
        }
    }

    public record BookRequest(
            @Schema(example = "Lakshmi Iyer") @NotBlank @Size(max = 120) String devoteeName,
            @Schema(example = "Kashyap") @Size(max = 60) String gotra,
            @Schema(example = "Rohini") @Size(max = 60) String nakshatra,
            @Schema(example = "Vrishabha") @Size(max = 60) String rashi,
            @Schema(example = "Ravi, Meena") @Size(max = 500) String familyNames,
            @Schema(example = "2030-08-15") @NotNull LocalDate pujaDate,
            @Schema(example = "98765 43210") @Size(max = 30) String phone,
            @Schema(example = "lakshmi@example.org") @Size(max = 254) String email) { }

    public record PriestAssignment(@Schema(example = "1", description = "null clears the assignment") Long priestId) { }

    public record PriestRequest(
            @Schema(example = "Pt. Shridhar Joshi") @NotBlank @Size(max = 120) String name,
            @Schema(example = "98765 43210") @Size(max = 30) String phone,
            @Schema(example = "Rudrabhishek, Navagraha homa") @Size(max = 200) String specialties,
            @Schema(example = "true") Boolean active) { }

    /** A priest's phone only for LEADER+, like devotee contacts. */
    public record PriestResponse(long id, String name, String phone, String specialties, boolean active) {
        static PriestResponse of(Priest p, boolean contacts) {
            return new PriestResponse(p.getId(), p.getName(), contacts ? p.getPhone() : null, p.getSpecialties(), p.isActive());
        }
    }

    public record CounterBookingRequest(
            @Schema(example = "1") @NotNull Long pujaId,
            @Schema(example = "Lakshmi Iyer") @NotBlank @Size(max = 120) String devoteeName,
            @Schema(example = "Kashyap") @Size(max = 60) String gotra,
            @Schema(example = "Rohini") @Size(max = 60) String nakshatra,
            @Schema(example = "Vrishabha") @Size(max = 60) String rashi,
            @Schema(example = "Ravi, Meena") @Size(max = 500) String familyNames,
            @Schema(example = "2030-08-15") @NotNull LocalDate pujaDate,
            @Schema(example = "98765 43210", description = "Optional for a walk-in") @Size(max = 30) String phone,
            @Schema(example = "lakshmi@example.org") @Size(max = 254) String email,
            @Schema(example = "CASH", description = "Required for a paid puja: CASH, UPI, CARD, CHEQUE or BANK_TRANSFER")
            @Size(max = 20) String mode,
            @Schema(example = "UTR 412345678901") @Size(max = 64) String reference) { }

    /** For a paid puja, the order to open Checkout with; confirm via /public/donations/confirm. */
    public record BookedResponse(String bookingCode, String status, String pujaName, LocalDate pujaDate, String amount,
                                 String orderId, String keyId, long amountPaise) { }

    public record BookingResponse(long id, String bookingCode, String pujaName, LocalDate pujaDate, String devoteeName,
                                  String gotra, String nakshatra, String rashi, String familyNames, String phone,
                                  String email, String amount, String status, boolean counter, String counterMode,
                                  Long priestId, String priestName) {
        static BookingResponse of(PujaBooking b, boolean contacts, Map<Long, String> priests) {
            return new BookingResponse(b.getId(), b.getBookingCode(), b.getPujaName(), b.getPujaDate(), b.getDevoteeName(),
                    b.getGotra(), b.getNakshatra(), b.getRashi(), b.getFamilyNames(), contacts ? b.getPhone() : null,
                    contacts ? b.getEmail() : null, Money.toRupees(b.getAmountPaise()), b.getStatus(),
                    b.getBookedBy() != null, contacts ? b.getCounterMode() : null,
                    b.getPriestId(), b.getPriestId() == null ? null : priests.get(b.getPriestId()));
        }
    }
}
