package app.sevacenter.portal;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import app.sevacenter.devotee.Devotee;
import app.sevacenter.donation.Donation;
import app.sevacenter.portal.DevoteePortalService.DevoteeSummary;
import app.sevacenter.portal.DevoteePortalService.OnlineDonationReceipt;
import app.sevacenter.portal.DevoteePortalService.OnlineDonationRequest;
import app.sevacenter.portal.DevoteePortalService.SendOtpResult;
import app.sevacenter.portal.DevoteePortalService.VerifyOtpResult;
import app.sevacenter.tenant.Tenant;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public and devotee-authenticated endpoints for the per-mandir public portal
 * (e.g. &lt;slug&gt;.mandircenter.app or /portal).
 */
@RestController
@RequestMapping("/api/v1/portal")
public class DevoteePortalController {

    public static final String DEVOTEE_COOKIE_NAME = "MC_SESSION";

    private final DevoteePortalService portalService;
    private final TenantRepository tenants;

    public DevoteePortalController(DevoteePortalService portalService, TenantRepository tenants) {
        this.portalService = portalService;
        this.tenants = tenants;
    }

    public record SendOtpRequest(@NotBlank String channel, @NotBlank String identifier) { }

    @PostMapping("/auth/send-otp")
    public ResponseEntity<SendOtpResult> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        SendOtpResult result = portalService.sendOtp(req.channel(), req.identifier());
        return ResponseEntity.ok(result);
    }

    public record VerifyOtpRequest(@NotBlank String channel, @NotBlank String identifier, @NotBlank String otp) { }

    @PostMapping("/auth/verify-otp")
    public ResponseEntity<VerifyOtpResult> verifyOtp(@Valid @RequestBody VerifyOtpRequest req,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        VerifyOtpResult result = portalService.verifyOtp(req.channel(), req.identifier(), req.otp());
        if (result.authenticated() && result.sessionToken() != null) {
            setSessionCookie(response, result.sessionToken(), request.isSecure());
        }
        return ResponseEntity.ok(result);
    }

    public record RegisterDevoteeRequest(
            @NotBlank String fullName,
            String phone,
            String email,
            String address,
            String city,
            String state,
            String pincode
    ) { }

    @PostMapping("/auth/register")
    public ResponseEntity<VerifyOtpResult> register(@Valid @RequestBody RegisterDevoteeRequest req,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        VerifyOtpResult result = portalService.registerDevotee(req.fullName(), req.phone(), req.email(),
                req.address(), req.city(), req.state(), req.pincode());
        if (result.authenticated() && result.sessionToken() != null) {
            setSessionCookie(response, result.sessionToken(), request.isSecure());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = extractSessionCookie(request);
        if (sessionToken != null) {
            portalService.logout(sessionToken);
        }
        clearSessionCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    public record PortalInfo(String slug, String name, Long tenantId) { }

    @GetMapping("/info")
    public ResponseEntity<PortalInfo> getPortalInfo() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(new PortalInfo(tenant.getSlug(), tenant.getName(), tenant.getId()));
    }

    @GetMapping("/me")
    public ResponseEntity<DevoteeSummary> getMe(HttpServletRequest request) {
        Devotee devotee = requireDevotee(request);
        return ResponseEntity.ok(DevoteeSummary.from(devotee));
    }

    public record DonateRequest(Long amountRupees, String donorName, String mode, String purpose, String reference) { }

    @PostMapping("/donations")
    public ResponseEntity<OnlineDonationReceipt> donate(@Valid @RequestBody DonateRequest req, HttpServletRequest request) {
        Optional<Devotee> currentDevotee = resolveDevotee(request);
        Long devoteeId = currentDevotee.map(Devotee::getId).orElse(null);
        String donorName = currentDevotee.map(Devotee::getFullName).orElse(req.donorName());

        OnlineDonationReceipt receipt = portalService.donateOnline(
                new OnlineDonationRequest(devoteeId, donorName, req.amountRupees() != null ? req.amountRupees() : 0,
                        req.mode(), req.purpose(), req.reference())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    public record DevoteeDonationItem(long id, long amountRupees, String mode, String purpose, String receivedOn, String reference) {
        public static DevoteeDonationItem from(Donation d) {
            return new DevoteeDonationItem(d.getId(), d.getAmountPaise() / 100L, d.getMode().name(),
                    d.getPurpose(), d.getReceivedOn().toString(), d.getReference());
        }
    }

    @GetMapping("/donations")
    public ResponseEntity<List<DevoteeDonationItem>> getMyDonations(HttpServletRequest request) {
        Devotee devotee = requireDevotee(request);
        List<Donation> list = portalService.getDevoteeDonations(devotee.getId());
        return ResponseEntity.ok(list.stream().map(DevoteeDonationItem::from).toList());
    }

    @GetMapping("/schedule")
    public ResponseEntity<DevoteePortalService.MandirSchedule> getSchedule() {
        return ResponseEntity.ok(portalService.getMandirSchedule());
    }

    @GetMapping("/pujas")
    public ResponseEntity<List<DevoteePortalService.PujaItem>> getPujas() {
        return ResponseEntity.ok(portalService.getAvailablePujas());
    }

    @PostMapping("/pujas/book")
    public ResponseEntity<DevoteePortalService.PujaBookingResponse> bookPuja(
            @Valid @RequestBody DevoteePortalService.BookPujaRequest req,
            HttpServletRequest request) {
        Optional<Devotee> currentDevotee = resolveDevotee(request);
        Long devoteeId = currentDevotee.map(Devotee::getId).orElse(req.devoteeId());
        String devoteeName = (req.devoteeName() != null && !req.devoteeName().isBlank())
                ? req.devoteeName()
                : currentDevotee.map(Devotee::getFullName).orElse("Devotee");

        DevoteePortalService.BookPujaRequest adjusted = new DevoteePortalService.BookPujaRequest(
                devoteeId, req.pujaCode(), req.pujaDate(), req.timeSlot(), devoteeName,
                req.gotra(), req.nakshatra(), req.rashi(), req.familyMembers(), req.contact(), req.paymentMode()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(portalService.bookPuja(adjusted));
    }

    @GetMapping("/pujas/my-bookings")
    public ResponseEntity<List<PujaBooking>> getMyPujaBookings(HttpServletRequest request) {
        Devotee devotee = requireDevotee(request);
        return ResponseEntity.ok(portalService.getDevoteePujaBookings(devotee.getId()));
    }

    @GetMapping("/events")
    public ResponseEntity<List<DevoteePortalService.MandirEvent>> getEvents() {
        return ResponseEntity.ok(portalService.getUpcomingEvents());
    }

    @PostMapping("/events/pass")
    public ResponseEntity<DevoteePortalService.DarshanPassResponse> bookPass(
            @Valid @RequestBody DevoteePortalService.BookPassRequest req,
            HttpServletRequest request) {
        Optional<Devotee> currentDevotee = resolveDevotee(request);
        Long devoteeId = currentDevotee.map(Devotee::getId).orElse(req.devoteeId());
        String primaryName = (req.primaryDevoteeName() != null && !req.primaryDevoteeName().isBlank())
                ? req.primaryDevoteeName()
                : currentDevotee.map(Devotee::getFullName).orElse("Devotee");

        DevoteePortalService.BookPassRequest adjusted = new DevoteePortalService.BookPassRequest(
                devoteeId, req.eventCode(), req.visitDate(), req.timeSlot(), primaryName,
                req.attendeeCount(), req.contact()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(portalService.bookDarshanPass(adjusted));
    }

    @GetMapping("/events/my-passes")
    public ResponseEntity<List<DarshanPass>> getMyDarshanPasses(HttpServletRequest request) {
        Devotee devotee = requireDevotee(request);
        return ResponseEntity.ok(portalService.getDevoteeDarshanPasses(devotee.getId()));
    }

    @PostMapping("/volunteer")
    public ResponseEntity<DevoteePortalService.SevakResponse> volunteer(
            @Valid @RequestBody DevoteePortalService.SevakRequest req,
            HttpServletRequest request) {
        Optional<Devotee> currentDevotee = resolveDevotee(request);
        Long devoteeId = currentDevotee.map(Devotee::getId).orElse(req.devoteeId());
        String name = (req.fullName() != null && !req.fullName().isBlank())
                ? req.fullName()
                : currentDevotee.map(Devotee::getFullName).orElse("Devotee");

        DevoteePortalService.SevakRequest adjusted = new DevoteePortalService.SevakRequest(
                devoteeId, name, req.contact(), req.sevaArea(), req.availableDays(),
                req.shiftPreference(), req.notes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(portalService.signupSevak(adjusted));
    }

    private Devotee requireDevotee(HttpServletRequest request) {
        return resolveDevotee(request).orElseThrow(() -> new DevoteeUnauthorizedException("Devotee sign-in required"));
    }

    private Optional<Devotee> resolveDevotee(HttpServletRequest request) {
        String token = extractSessionCookie(request);
        if (token == null) {
            return Optional.empty();
        }
        return portalService.resolveDevoteeFromSession(token);
    }

    private String extractSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> DEVOTEE_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void setSessionCookie(HttpServletResponse response, String token, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(DEVOTEE_COOKIE_NAME, token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .secure(secure)
                .maxAge(Duration.ofDays(30))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(DEVOTEE_COOKIE_NAME, "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static class DevoteeUnauthorizedException extends RuntimeException {
        public DevoteeUnauthorizedException(String message) {
            super(message);
        }
    }
}
