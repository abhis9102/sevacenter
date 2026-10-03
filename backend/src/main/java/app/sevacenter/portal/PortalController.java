package app.sevacenter.portal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.donation.Money;
import app.sevacenter.donation.RateLimiter;
import app.sevacenter.event.Event;
import app.sevacenter.event.EventPass;
import app.sevacenter.event.EventPassRepository;
import app.sevacenter.event.EventRepository;
import app.sevacenter.puja.PujaBookingRepository;
import app.sevacenter.sevak.SevakSignupRepository;
import app.sevacenter.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Devotee login and "my seva" (ADR 0018). Login lives under /public (anyone may ask for a code);
 * the portal under /portal answers only to the devotee cookie, which is not a Spring Security
 * login: it opens no staff API, and a staff session opens no portal API. CSRF applies to both.
 */
@RestController
public class PortalController {

    /** Scoped to the portal path: the cookie never even reaches the staff API. */
    static final String COOKIE = "SC_DEVOTEE";
    static final String COOKIE_PATH = "/api/v1/portal";

    private final DevoteeLoginService login;
    private final RateLimiter rateLimiter;
    private final PujaBookingRepository bookings;
    private final EventPassRepository passes;
    private final EventRepository events;
    private final SevakSignupRepository signups;
    private final boolean secureCookie;

    public PortalController(DevoteeLoginService login, RateLimiter rateLimiter, PujaBookingRepository bookings,
                            EventPassRepository passes, EventRepository events, SevakSignupRepository signups,
                            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie) {
        this.login = login;
        this.rateLimiter = rateLimiter;
        this.bookings = bookings;
        this.passes = passes;
        this.events = events;
        this.signups = signups;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/api/v1/public/devotee-login")
    public ResponseEntity<Channels> channels() {
        if (TenantContext.get() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new Channels(login.offered(OtpChannel.EMAIL), login.offered(OtpChannel.SMS)));
    }

    /** Always the same answer for any well-formed contact: whether it's known is not revealed. */
    @PostMapping("/api/v1/public/devotee-login/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> sendCode(@Valid @RequestBody CodeRequest r, HttpServletRequest request) {
        if (!rateLimiter.tryAcquire("otp:" + request.getRemoteAddr())) {
            throw new LoginThrottle.TooManyAttemptsException();
        }
        login.sendCode(r.channel(), r.contact());
        return Map.of("sent", true);
    }

    @PostMapping("/api/v1/public/devotee-login/verify")
    public ResponseEntity<Map<String, Object>> verify(@Valid @RequestBody VerifyRequest r, HttpServletRequest request) {
        if (!rateLimiter.tryAcquire("otp-verify:" + request.getRemoteAddr())) {
            throw new LoginThrottle.TooManyAttemptsException();
        }
        String token = login.verify(r.channel(), r.contact(), r.code());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(token, DevoteeLoginService.SESSION_TTL.toSeconds()))
                .body(Map.of("loggedIn", true));
    }

    @PostMapping("/api/v1/portal/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token) {
        login.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0)).build();
    }

    /** Only records carrying the exact contact the devotee proved they own. */
    @GetMapping("/api/v1/portal/me")
    @Transactional(readOnly = true)
    public MySeva me(@CookieValue(name = COOKIE, required = false) String token) {
        DevoteeAccount account = login.resolve(token).orElseThrow(NotLoggedInException::new);
        String contact = account.getContact();
        PageRequest top = PageRequest.of(0, 200);
        List<EventPass> myPasses = passes.forContact(contact, top);
        Map<Long, Event> eventsById = events.findAllById(myPasses.stream().map(EventPass::getEventId).toList())
                .stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        return new MySeva(account.getChannel(), contact,
                bookings.forContact(contact, top).stream().map(b -> new MyBooking(b.getBookingCode(), b.getPujaName(),
                        b.getPujaDate(), Money.toRupees(b.getAmountPaise()), b.getStatus())).toList(),
                myPasses.stream().map(p -> {
                    Event e = eventsById.get(p.getEventId());
                    return new MyPass(p.getPassCode(), e == null ? null : e.getTitle(), e == null ? null : e.getStartsAt(),
                            p.getAttendeeCount(), p.getStatus());
                }).toList(),
                signups.forContact(contact, top).stream().map(s -> new MySignup(s.getSevaAreas(), s.getStatus(),
                        s.getCreatedAt())).toList());
    }

    private String cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secureCookie).sameSite("Lax")
                .path(COOKIE_PATH).maxAge(maxAgeSeconds).build().toString();
    }

    // --- records ----------------------------------------------------------------------------------

    public record Channels(boolean email, boolean sms) { }

    /** Examples are valid on purpose, so DAST attacks reach the service. */
    public record CodeRequest(
            @Schema(example = "EMAIL") @NotNull OtpChannel channel,
            @Schema(example = "lakshmi@example.org") @Size(max = 254) String contact) { }

    public record VerifyRequest(
            @Schema(example = "EMAIL") @NotNull OtpChannel channel,
            @Schema(example = "lakshmi@example.org") @Size(max = 254) String contact,
            @Schema(example = "123456") @Size(max = 10) String code) { }

    public record MySeva(OtpChannel channel, String contact, List<MyBooking> pujaBookings, List<MyPass> eventPasses,
                         List<MySignup> sevakSignups) { }

    public record MyBooking(String bookingCode, String pujaName, LocalDate pujaDate, String amount, String status) { }

    public record MyPass(String passCode, String eventTitle, OffsetDateTime startsAt, int attendeeCount, String status) { }

    public record MySignup(String sevaAreas, String status, OffsetDateTime createdAt) { }

    /** 401: no live devotee session on this trust's host. */
    public static class NotLoggedInException extends RuntimeException { }
}
