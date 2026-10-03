package app.sevacenter.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import app.sevacenter.devotee.ConsentSource;
import app.sevacenter.devotee.Devotee;
import app.sevacenter.devotee.Devotee.Details;
import app.sevacenter.devotee.DevoteeRepository;
import app.sevacenter.donation.Donation;
import app.sevacenter.donation.DonationMode;
import app.sevacenter.donation.DonationRepository;
import app.sevacenter.tenant.Tenant;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevoteePortalService {

    private static final Logger log = LoggerFactory.getLogger(DevoteePortalService.class);
    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DevoteeAuthTokenRepository authTokens;
    private final DevoteeSessionRepository sessions;
    private final DevoteeRepository devotees;
    private final DonationRepository donations;
    private final TenantRepository tenants;
    private final PujaBookingRepository pujaBookings;
    private final DarshanPassRepository darshanPasses;
    private final SevakSignupRepository sevakSignups;
    private final MandirAdminService mandirAdmin;
    private final Clock clock = Clock.system(IST);
    private final boolean devMode;

    public DevoteePortalService(DevoteeAuthTokenRepository authTokens,
                                DevoteeSessionRepository sessions,
                                DevoteeRepository devotees,
                                DonationRepository donations,
                                TenantRepository tenants,
                                PujaBookingRepository pujaBookings,
                                DarshanPassRepository darshanPasses,
                                SevakSignupRepository sevakSignups,
                                MandirAdminService mandirAdmin,
                                @Value("${sevacenter.dev-tokens:false}") boolean devTokens) {
        this.authTokens = authTokens;
        this.sessions = sessions;
        this.devotees = devotees;
        this.donations = donations;
        this.tenants = tenants;
        this.pujaBookings = pujaBookings;
        this.darshanPasses = darshanPasses;
        this.sevakSignups = sevakSignups;
        this.mandirAdmin = mandirAdmin;
        this.devMode = devTokens;
    }

    public record SendOtpResult(String message, String devOtp) { }

    @Transactional
    public SendOtpResult sendOtp(String rawChannel, String rawIdentifier) {
        long tenantId = currentTenant();
        String channel = normalizeChannel(rawChannel);
        String identifier = normalizeIdentifier(channel, rawIdentifier);

        // Generate 6-digit numeric OTP
        int numericOtp = 100000 + RANDOM.nextInt(900000);
        String otpString = String.valueOf(numericOtp);
        String otpHash = sha256(otpString);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(10));

        DevoteeAuthToken token = new DevoteeAuthToken(tenantId, channel, identifier, otpHash, expiresAt);
        authTokens.save(token);

        log.info("Devotee OTP issued for tenant={} channel={} target={} devMode={}", tenantId, channel, identifier, devMode);
        audit.info("event=devotee_otp_sent tenant={} channel={} target={}", tenantId, channel, mask(identifier));

        String devOtp = devMode ? otpString : null;
        return new SendOtpResult("OTP sent successfully", devOtp);
    }

    public record VerifyOtpResult(boolean authenticated, String sessionToken, DevoteeSummary devotee, String verifiedIdentifier, String channel) { }

    public record DevoteeSummary(long id, String fullName, String phone, String email, String city) {
        public static DevoteeSummary from(Devotee d) {
            return new DevoteeSummary(d.getId(), d.getFullName(), d.getPhone(), d.getEmail(), d.getCity());
        }
    }

    @Transactional
    public VerifyOtpResult verifyOtp(String rawChannel, String rawIdentifier, String rawOtp) {
        long tenantId = currentTenant();
        String channel = normalizeChannel(rawChannel);
        String identifier = normalizeIdentifier(channel, rawIdentifier);

        if (rawOtp == null || rawOtp.trim().length() < 4) {
            throw new InvalidFieldException("otp", "Invalid OTP format");
        }
        String cleanOtp = rawOtp.trim();

        Instant now = clock.instant();
        DevoteeAuthToken token = authTokens
                .findTopByTenantIdAndTargetTypeAndTargetValueAndConsumedAtIsNullOrderByCreatedAtDesc(tenantId, channel, identifier)
                .orElseThrow(() -> new InvalidOtpException("No active OTP found. Please request a new one."));

        if (token.isExpired(now)) {
            throw new InvalidOtpException("OTP has expired. Please request a new one.");
        }

        token.incrementAttempts();
        if (token.getAttempts() > 5) {
            token.markConsumed(now);
            throw new InvalidOtpException("Too many incorrect attempts. Please request a new OTP.");
        }

        String enteredHash = sha256(cleanOtp);
        if (!token.getTokenHash().equals(enteredHash)) {
            throw new InvalidOtpException("Incorrect OTP. Please check and try again.");
        }

        token.markConsumed(now);

        // Find existing devotee by phone or email
        Optional<Devotee> existingDevotee = "PHONE".equals(channel)
                ? devotees.findFirstByTenantIdAndPhoneAndErasedAtIsNull(tenantId, identifier)
                : devotees.findFirstByTenantIdAndEmailIgnoreCaseAndErasedAtIsNull(tenantId, identifier);

        if (existingDevotee.isPresent()) {
            Devotee d = existingDevotee.get();
            String rawSessionToken = createSession(tenantId, d.getId(), now);
            audit.info("event=devotee_login tenant={} devotee={}", tenantId, d.getId());
            return new VerifyOtpResult(true, rawSessionToken, DevoteeSummary.from(d), identifier, channel);
        } else {
            // Unregistered devotee: return authenticated=false, client shows registration step
            return new VerifyOtpResult(false, null, null, identifier, channel);
        }
    }

    @Transactional
    public VerifyOtpResult registerDevotee(String fullName, String phone, String email, String address, String city,
                                           String state, String pincode) {
        long tenantId = currentTenant();
        if (fullName == null || fullName.trim().length() < 2) {
            throw new InvalidFieldException("fullName", "Name is required (at least 2 characters)");
        }

        String cleanPhone = (phone != null && !phone.isBlank()) ? normalizeIdentifier("PHONE", phone) : null;
        String cleanEmail = (email != null && !email.isBlank()) ? normalizeIdentifier("EMAIL", email) : null;

        if (cleanPhone == null && cleanEmail == null) {
            throw new InvalidFieldException("identifier", "Either mobile number or email is required");
        }

        OffsetDateTime now = OffsetDateTime.now(IST);
        Details details = new Details(fullName.trim(), cleanPhone, cleanEmail,
                blankToNull(address), blankToNull(city), blankToNull(state), blankToNull(pincode), null);

        // Online registration records explicit DPDP consent via ONLINE_FORM:
        Devotee devotee = new Devotee(tenantId, details, ConsentSource.ONLINE_FORM, null, now);
        Devotee saved = devotees.save(devotee);

        String rawSessionToken = createSession(tenantId, saved.getId(), clock.instant());
        audit.info("event=devotee_registered tenant={} devotee={}", tenantId, saved.getId());
        return new VerifyOtpResult(true, rawSessionToken, DevoteeSummary.from(saved), cleanPhone != null ? cleanPhone : cleanEmail, cleanPhone != null ? "PHONE" : "EMAIL");
    }

    public record OnlineDonationRequest(Long devoteeId, String donorName, long amountRupees, String mode, String purpose, String reference) { }

    public record OnlineDonationReceipt(long donationId, String receiptNumber, String donorName, long amountRupees, String mode, String purpose, LocalDate date, String mandirName) { }

    @Transactional
    public OnlineDonationReceipt donateOnline(OnlineDonationRequest req) {
        long tenantId = currentTenant();
        if (req.amountRupees() <= 0 || req.amountRupees() > 10_00_00_000) {
            throw new InvalidFieldException("amountRupees", "Donation amount must be between ₹1 and ₹10,00,00,000");
        }

        String name;
        if (req.devoteeId() != null) {
            Devotee d = devotees.findByIdAndErasedAtIsNull(req.devoteeId())
                    .orElseThrow(() -> new InvalidFieldException("devoteeId", "Devotee not found"));
            name = d.getFullName();
        } else if (req.donorName() != null && !req.donorName().isBlank()) {
            name = req.donorName().trim();
        } else {
            name = "Devotee";
        }

        DonationMode mode;
        try {
            mode = (req.mode() != null) ? DonationMode.valueOf(req.mode().toUpperCase(Locale.ROOT)) : DonationMode.UPI;
        } catch (IllegalArgumentException e) {
            mode = DonationMode.UPI;
        }

        long amountPaise = req.amountRupees() * 100L;
        LocalDate receivedOn = LocalDate.now(clock);

        Donation donation = Donation.received(tenantId, req.devoteeId(), name, amountPaise, mode,
                blankToNull(req.reference()), blankToNull(req.purpose()), receivedOn, null);
        Donation saved = donations.save(donation);

        Tenant tenant = tenants.findById(tenantId).orElse(null);
        String mandirName = (tenant != null) ? tenant.getName() : "Mandir";

        String receiptNumber = String.format("MDR-%d-%06d", tenantId, saved.getId());
        audit.info("event=online_donation tenant={} donation={} amount={} paise", tenantId, saved.getId(), amountPaise);

        return new OnlineDonationReceipt(saved.getId(), receiptNumber, name, req.amountRupees(),
                mode.name(), req.purpose(), receivedOn, mandirName);
    }

    @Transactional(readOnly = true)
    public List<Donation> getDevoteeDonations(long devoteeId) {
        return donations.findAllByDevoteeIdAndReversesIdIsNullOrderByReceivedOnDesc(devoteeId);
    }

    public record AartiTiming(String name, String time, String description) { }

    public record MandirSchedule(
            String mandirName,
            String deity,
            String address,
            String helpline,
            String morningHours,
            String eveningHours,
            boolean isOpenNow,
            String panchangTithi,
            String nakshatra,
            String specialAnnouncement,
            List<AartiTiming> aartis
    ) { }

    @Transactional
    public MandirSchedule getMandirSchedule() {
        MandirAdminService.MandirScheduleDto s = mandirAdmin.getScheduleDto();
        List<AartiTiming> aartis = s.aartis().stream()
                .map(a -> new AartiTiming(a.name(), a.time(), a.description()))
                .toList();
        return new MandirSchedule(
                s.mandirName(),
                s.deity(),
                s.address(),
                s.helpline(),
                s.morningHours(),
                s.eveningHours(),
                s.isOpenNow(),
                s.panchangTithi(),
                s.nakshatra(),
                s.specialAnnouncement(),
                aartis
        );
    }

    public record PujaItem(String code, String name, String deity, String duration, long dakshinaRupees, String description, boolean prasadIncluded) { }

    public List<PujaItem> getAvailablePujas() {
        return mandirAdmin.getAllPujas().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(p -> new PujaItem(p.getCode(), p.getName(), p.getDeity(), p.getDuration(),
                        p.getDakshinaRupees(), p.getDescription(), p.getPrasadIncluded()))
                .toList();
    }

    public record BookPujaRequest(
            Long devoteeId,
            String pujaCode,
            LocalDate pujaDate,
            String timeSlot,
            String devoteeName,
            String gotra,
            String nakshatra,
            String rashi,
            String familyMembers,
            String contact,
            String paymentMode
    ) { }

    public record PujaBookingResponse(
            long id,
            String bookingNumber,
            String pujaCode,
            String pujaName,
            LocalDate pujaDate,
            String timeSlot,
            String devoteeName,
            String gotra,
            String nakshatra,
            String rashi,
            String familyMembers,
            long amountRupees,
            String status,
            String mandirName
    ) { }

    @Transactional
    public PujaBookingResponse bookPuja(BookPujaRequest req) {
        long tenantId = currentTenant();
        if (req.devoteeName() == null || req.devoteeName().trim().length() < 2) {
            throw new InvalidFieldException("devoteeName", "Devotee name is required for Sankalp");
        }
        if (req.pujaDate() == null || req.pujaDate().isBefore(LocalDate.now(clock))) {
            throw new InvalidFieldException("pujaDate", "Puja date must be today or in the future");
        }

        PujaItem puja = getAvailablePujas().stream()
                .filter(p -> p.code().equalsIgnoreCase(req.pujaCode()))
                .findFirst()
                .orElse(new PujaItem(req.pujaCode(), req.pujaCode(), "Pradhan Devata", "30 mins", 501L, "Special Vedic Puja", true));

        long amountPaise = puja.dakshinaRupees() * 100L;
        String bookingNumber = String.format("PJ-%d-%06d", tenantId, System.currentTimeMillis() % 1_000_000);

        PujaBooking booking = new PujaBooking(
                tenantId,
                req.devoteeId(),
                bookingNumber,
                puja.code(),
                puja.name(),
                req.pujaDate(),
                req.timeSlot() != null ? req.timeSlot() : "Morning (08:00 AM - 10:00 AM)",
                req.devoteeName().trim(),
                blankToNull(req.gotra()),
                blankToNull(req.nakshatra()),
                blankToNull(req.rashi()),
                blankToNull(req.familyMembers()),
                blankToNull(req.contact()),
                amountPaise,
                req.paymentMode() != null ? req.paymentMode() : "UPI"
        );

        PujaBooking saved = pujaBookings.save(booking);

        if (amountPaise > 0) {
            Donation donation = Donation.received(
                    tenantId,
                    req.devoteeId(),
                    req.devoteeName().trim(),
                    amountPaise,
                    DonationMode.UPI,
                    bookingNumber,
                    "Puja Dakshina: " + puja.name(),
                    req.pujaDate(),
                    null
            );
            donations.save(donation);
        }

        Tenant tenant = tenants.findById(tenantId).orElse(null);
        String mandirName = (tenant != null) ? tenant.getName() : "Shri Mandir";

        audit.info("event=puja_booked tenant={} booking={} amountPaise={}", tenantId, bookingNumber, amountPaise);

        return new PujaBookingResponse(
                saved.getId(),
                saved.getBookingNumber(),
                saved.getPujaCode(),
                saved.getPujaName(),
                saved.getPujaDate(),
                saved.getTimeSlot(),
                saved.getDevoteeName(),
                saved.getGotra(),
                saved.getNakshatra(),
                saved.getRashi(),
                saved.getFamilyMembers(),
                saved.getAmountPaise() / 100L,
                saved.getStatus(),
                mandirName
        );
    }

    @Transactional(readOnly = true)
    public List<PujaBooking> getDevoteePujaBookings(long devoteeId) {
        return pujaBookings.findAllByDevoteeIdOrderByPujaDateDesc(devoteeId);
    }

    public record MandirEvent(
            String code,
            String name,
            LocalDate date,
            String timeRange,
            String description,
            String highlights,
            boolean registrationOpen
    ) { }

    public List<MandirEvent> getUpcomingEvents() {
        return mandirAdmin.getAllEvents().stream()
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .map(e -> new MandirEvent(e.getCode(), e.getName(), e.getEventDate(), e.getTimeRange(),
                        e.getDescription(), e.getHighlights(), e.getRegistrationOpen()))
                .toList();
    }

    public record BookPassRequest(
            Long devoteeId,
            String eventCode,
            LocalDate visitDate,
            String timeSlot,
            String primaryDevoteeName,
            int attendeeCount,
            String contact
    ) { }

    public record DarshanPassResponse(
            long id,
            String passNumber,
            String eventCode,
            String eventName,
            LocalDate visitDate,
            String timeSlot,
            String primaryDevoteeName,
            int attendeeCount,
            String contact,
            String status,
            String qrString,
            String mandirName
    ) { }

    @Transactional
    public DarshanPassResponse bookDarshanPass(BookPassRequest req) {
        long tenantId = currentTenant();
        if (req.primaryDevoteeName() == null || req.primaryDevoteeName().trim().length() < 2) {
            throw new InvalidFieldException("primaryDevoteeName", "Name is required for pass");
        }
        if (req.contact() == null || req.contact().trim().length() < 5) {
            throw new InvalidFieldException("contact", "Contact number is required");
        }

        MandirEvent event = getUpcomingEvents().stream()
                .filter(e -> e.code().equalsIgnoreCase(req.eventCode()))
                .findFirst()
                .orElse(new MandirEvent(req.eventCode(), req.eventCode(), req.visitDate() != null ? req.visitDate() : LocalDate.now(clock), "All Day", "Festival Darshan", "Special Darshan", true));

        String passNumber = String.format("PASS-%d-%06d", tenantId, System.currentTimeMillis() % 1_000_000);
        LocalDate visitDate = req.visitDate() != null ? req.visitDate() : event.date();
        String timeSlot = req.timeSlot() != null ? req.timeSlot() : "Morning (07:00 AM - 10:00 AM)";
        int count = Math.clamp(req.attendeeCount(), 1, 10);

        DarshanPass pass = new DarshanPass(
                tenantId,
                req.devoteeId(),
                passNumber,
                event.code(),
                event.name(),
                visitDate,
                timeSlot,
                req.primaryDevoteeName().trim(),
                count,
                req.contact().trim()
        );

        DarshanPass saved = darshanPasses.save(pass);
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        String mandirName = (tenant != null) ? tenant.getName() : "Shri Mandir";

        String qrString = String.format("MANDIR_TOKEN:%s:%d:%s", passNumber, tenantId, saved.getPrimaryDevoteeName());
        audit.info("event=darshan_pass_issued tenant={} pass={} attendees={}", tenantId, passNumber, count);

        return new DarshanPassResponse(
                saved.getId(),
                saved.getPassNumber(),
                saved.getEventCode(),
                saved.getEventName(),
                saved.getVisitDate(),
                saved.getTimeSlot(),
                saved.getPrimaryDevoteeName(),
                saved.getAttendeeCount(),
                saved.getContact(),
                saved.getStatus(),
                qrString,
                mandirName
        );
    }

    @Transactional(readOnly = true)
    public List<DarshanPass> getDevoteeDarshanPasses(long devoteeId) {
        return darshanPasses.findAllByDevoteeIdOrderByVisitDateDesc(devoteeId);
    }

    public record SevakRequest(
            Long devoteeId,
            String fullName,
            String contact,
            String sevaArea,
            String availableDays,
            String shiftPreference,
            String notes
    ) { }

    public record SevakResponse(
            long id,
            String fullName,
            String sevaArea,
            String availableDays,
            String shiftPreference,
            String message
    ) { }

    @Transactional
    public SevakResponse signupSevak(SevakRequest req) {
        long tenantId = currentTenant();
        if (req.fullName() == null || req.fullName().trim().length() < 2) {
            throw new InvalidFieldException("fullName", "Name is required");
        }
        if (req.contact() == null || req.contact().trim().length() < 5) {
            throw new InvalidFieldException("contact", "Contact number is required");
        }

        SevakSignup signup = new SevakSignup(
                tenantId,
                req.devoteeId(),
                req.fullName().trim(),
                req.contact().trim(),
                blankToNull(req.sevaArea()) != null ? req.sevaArea().trim() : "Prasad / Kitchen Seva",
                blankToNull(req.availableDays()) != null ? req.availableDays().trim() : "Weekends & Festivals",
                blankToNull(req.shiftPreference()) != null ? req.shiftPreference().trim() : "Morning Shift",
                blankToNull(req.notes())
        );

        SevakSignup saved = sevakSignups.save(signup);
        audit.info("event=sevak_signed_up tenant={} sevakId={} area={}", tenantId, saved.getId(), saved.getSevaArea());

        return new SevakResponse(
                saved.getId(),
                saved.getFullName(),
                saved.getSevaArea(),
                saved.getAvailableDays(),
                saved.getShiftPreference(),
                "Dhanyavad! Your volunteer seva application has been received. The mandir committee will contact you before upcoming utsavs."
        );
    }

    @Transactional
    public Optional<Devotee> resolveDevoteeFromSession(String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            return Optional.empty();
        }
        long tenantId = currentTenant();
        String hash = sha256(rawSessionToken.trim());
        Optional<DevoteeSession> sessionOpt = sessions.findByTenantIdAndSessionTokenHash(tenantId, hash);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        DevoteeSession s = sessionOpt.get();
        Instant now = clock.instant();
        if (s.isExpired(now)) {
            sessions.delete(s);
            return Optional.empty();
        }
        s.touch(now);
        return devotees.findByIdAndErasedAtIsNull(s.getDevoteeId());
    }

    @Transactional
    public void logout(String rawSessionToken) {
        if (rawSessionToken != null && !rawSessionToken.isBlank()) {
            long tenantId = currentTenant();
            sessions.deleteByTenantIdAndSessionTokenHash(tenantId, sha256(rawSessionToken.trim()));
        }
    }

    private String createSession(long tenantId, long devoteeId, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawSession = HexFormat.of().formatHex(bytes);
        String sessionHash = sha256(rawSession);
        Instant expiresAt = now.plus(Duration.ofDays(30));

        DevoteeSession session = new DevoteeSession(tenantId, devoteeId, sessionHash, expiresAt);
        sessions.save(session);
        return rawSession;
    }

    private String normalizeChannel(String channel) {
        if (channel == null) {
            throw new InvalidFieldException("channel", "Channel must be PHONE or EMAIL");
        }
        String c = channel.trim().toUpperCase(Locale.ROOT);
        if (!c.equals("PHONE") && !c.equals("EMAIL")) {
            throw new InvalidFieldException("channel", "Channel must be PHONE or EMAIL");
        }
        return c;
    }

    private String normalizeIdentifier(String channel, String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new InvalidFieldException("identifier", "Mobile number or email is required");
        }
        String id = rawIdentifier.trim();
        if ("PHONE".equals(channel)) {
            // Keep digits and leading plus:
            String digits = id.replaceAll("[^0-9+]", "");
            if (!digits.startsWith("+")) {
                // Default to India (+91) if 10-digit number
                if (digits.length() == 10) {
                    digits = "+91" + digits;
                } else {
                    digits = "+" + digits;
                }
            }
            if (!digits.matches("^\\+[1-9][0-9]{7,14}$")) {
                throw new InvalidFieldException("identifier", "Please enter a valid mobile number with country code (e.g. +919876543210)");
            }
            return digits;
        } else {
            String lower = id.toLowerCase(Locale.ROOT);
            if (!lower.contains("@") || lower.length() > 254) {
                throw new InvalidFieldException("identifier", "Please enter a valid email address");
            }
            return lower;
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String mask(String identifier) {
        if (identifier == null) return "";
        if (identifier.contains("@")) {
            int at = identifier.indexOf('@');
            return (at <= 2 ? "*" : identifier.substring(0, 2)) + "***" + identifier.substring(at);
        } else if (identifier.length() > 6) {
            return identifier.substring(0, 3) + "****" + identifier.substring(identifier.length() - 3);
        }
        return "***";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("tenant required");
        }
        return tenantId;
    }

    public static class InvalidOtpException extends RuntimeException {
        public InvalidOtpException(String message) {
            super(message);
        }
    }
}
