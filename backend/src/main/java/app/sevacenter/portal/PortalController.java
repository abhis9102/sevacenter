package app.sevacenter.portal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.donation.DonationHistory;
import app.sevacenter.donation.Money;
import app.sevacenter.donation.ReceiptController;
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
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final DonationHistory donations;
    private final boolean secureCookie;

    public PortalController(DevoteeLoginService login, RateLimiter rateLimiter, PujaBookingRepository bookings,
                            EventPassRepository passes, EventRepository events, SevakSignupRepository signups,
                            DonationHistory donations, @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie) {
        this.login = login;
        this.rateLimiter = rateLimiter;
        this.bookings = bookings;
        this.passes = passes;
        this.events = events;
        this.signups = signups;
        this.donations = donations;
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

    /**
     * Only records carrying a contact the devotee proved they own: any of their verified phones and
     * emails (ADR 0018, 0025), exact matches only.
     */
    @GetMapping("/api/v1/portal/me")
    @Transactional
    public ResponseEntity<MySeva> me(@CookieValue(name = COOKIE, required = false) String token) {
        DevoteeAccount account = login.resolve(token).orElseThrow(NotLoggedInException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(account));
    }

    /** Link another phone or email: send it a code with /public/devotee-login/code, then this. */
    @PostMapping("/api/v1/portal/contacts")
    @Transactional(noRollbackFor = DevoteeLoginService.InvalidCodeException.class)
    public ResponseEntity<MySeva> addContact(@Valid @RequestBody VerifyRequest r, HttpServletRequest request,
                                             @CookieValue(name = COOKIE, required = false) String token) {
        DevoteeAccount account = login.resolve(token).orElseThrow(NotLoggedInException::new);
        if (!rateLimiter.tryAcquire("otp-verify:" + request.getRemoteAddr())) {
            throw new LoginThrottle.TooManyAttemptsException();
        }
        DevoteeAccount me = login.addContact(account, r.channel(), r.contact(), r.code());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(me));
    }

    @PutMapping("/api/v1/portal/profile")
    @Transactional
    public ResponseEntity<MySeva> saveProfile(@Valid @RequestBody Profile p,
                                              @CookieValue(name = COOKIE, required = false) String token) {
        DevoteeAccount account = login.resolve(token).orElseThrow(NotLoggedInException::new);
        DevoteeAccount me = login.editProfile(account, clean(p.fullName()), clean(p.gotra()), clean(p.nakshatra()),
                clean(p.rashi()), p.dateOfBirth(), clean(p.familyNames()), clean(p.addressLine()), clean(p.city()),
                clean(p.state()), clean(p.pincode()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(me));
    }

    /** The receipt for one of the devotee's own donations, donor PAN masked (ADR 0019). */
    @GetMapping("/api/v1/portal/donations/{id:\\d+}/receipt")
    @Transactional
    public ResponseEntity<ReceiptController.ReceiptResponse> receipt(@PathVariable long id,
            @CookieValue(name = COOKIE, required = false) String token) {
        DevoteeAccount account = login.resolve(token).orElseThrow(NotLoggedInException::new);
        return login.contactsOf(account).stream()
                .map(c -> donations.receipt(c.getContact(), id)).flatMap(java.util.Optional::stream).findFirst()
                .map(r -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r))
                .orElse(ResponseEntity.notFound().build());
    }

    private MySeva view(DevoteeAccount account) {
        List<DevoteeContact> mine = login.contactsOf(account);
        List<String> contacts = mine.stream().map(DevoteeContact::getContact).toList();
        PageRequest top = PageRequest.of(0, 200);
        List<EventPass> myPasses = distinct(contacts.stream().flatMap(c -> passes.forContact(c, top).stream()),
                EventPass::getId).stream().sorted(Comparator.comparing(EventPass::getCreatedAt).reversed()).toList();
        Map<Long, Event> eventsById = events.findAllById(myPasses.stream().map(EventPass::getEventId).toList())
                .stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        return new MySeva(account.getChannel(), account.getContact(),
                mine.stream().map(c -> new MyContact(c.getChannel(), c.getContact())).toList(),
                new Profile(account.getFullName(), account.getGotra(), account.getNakshatra(), account.getRashi(),
                        account.getDateOfBirth(), account.getFamilyNames(), account.getAddressLine(), account.getCity(),
                        account.getState(), account.getPincode()),
                distinct(contacts.stream().flatMap(c -> donations.forContact(c).stream()), DonationHistory.Entry::id)
                        .stream().sorted(Comparator.comparing(DonationHistory.Entry::receivedOn)
                                .thenComparing(DonationHistory.Entry::id).reversed()).toList(),
                distinct(contacts.stream().flatMap(c -> bookings.forContact(c, top).stream()), b -> b.getId()).stream()
                        .sorted(Comparator.comparing((app.sevacenter.puja.PujaBooking b) -> b.getPujaDate()).reversed())
                        .map(b -> new MyBooking(b.getBookingCode(), b.getPujaName(), b.getPujaDate(),
                                Money.toRupees(b.getAmountPaise()), b.getStatus())).toList(),
                myPasses.stream().map(p -> {
                    Event e = eventsById.get(p.getEventId());
                    return new MyPass(p.getPassCode(), e == null ? null : e.getTitle(), e == null ? null : e.getStartsAt(),
                            p.getAttendeeCount(), p.getStatus());
                }).toList(),
                distinct(contacts.stream().flatMap(c -> signups.forContact(c, top).stream()), s -> s.getId()).stream()
                        .sorted(Comparator.comparing((app.sevacenter.sevak.SevakSignup x) -> x.getCreatedAt()).reversed())
                        .map(x -> new MySignup(x.getSevaAreas(), x.getStatus(), x.getCreatedAt())).toList());
    }

    /** A record made with two of the devotee's contacts (phone and email) is shown once. */
    private static <T> List<T> distinct(java.util.stream.Stream<T> items, Function<T, Object> key) {
        Map<Object, T> seen = new LinkedHashMap<>();
        items.forEach(i -> seen.putIfAbsent(key.apply(i), i));
        return List.copyOf(seen.values());
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

    /** The devotee's own words about themselves (ADR 0025); all optional. */
    public record Profile(
            @Schema(example = "Lakshmi Iyer") @Size(max = 120) String fullName,
            @Schema(example = "Kashyap") @Size(max = 60) String gotra,
            @Schema(example = "Rohini") @Size(max = 60) String nakshatra,
            @Schema(example = "Vrishabha") @Size(max = 60) String rashi,
            @Schema(example = "1974-03-12") @Past LocalDate dateOfBirth,
            @Schema(example = "Ravi, Meena") @Size(max = 500) String familyNames,
            @Schema(example = "12 Temple Street") @Size(max = 200) String addressLine,
            @Schema(example = "Pune") @Size(max = 80) String city,
            @Schema(example = "Maharashtra") @Size(max = 80) String state,
            @Schema(example = "411030") @Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be 6 digits") String pincode) { }

    public record MyContact(OtpChannel channel, String contact) { }

    public record MySeva(OtpChannel channel, String contact, List<MyContact> contacts, Profile profile,
                         List<DonationHistory.Entry> donations,
                         List<MyBooking> pujaBookings, List<MyPass> eventPasses,
                         List<MySignup> sevakSignups) { }

    public record MyBooking(String bookingCode, String pujaName, LocalDate pujaDate, String amount, String status) { }

    public record MyPass(String passCode, String eventTitle, OffsetDateTime startsAt, int attendeeCount, String status) { }

    public record MySignup(String sevaAreas, String status, OffsetDateTime createdAt) { }

    /** 401: no live devotee session on this trust's host. */
    public static class NotLoggedInException extends RuntimeException { }
}
