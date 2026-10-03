package app.sevacenter.portal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import app.sevacenter.tenant.Tenant;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Service for temple staff, trustees, and priests to configure Mandir schedules,
 * manage the Puja & Rituals catalog, view incoming family Sankalp lists,
 * manage Utsavs/Festivals, check in devotee Darshan passes at the gate,
 * and assign volunteers (Sevaks) to teams and shifts.
 */
@Service
public class MandirAdminService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MandirSettingRepository mandirSettings;
    private final PujaCatalogRepository pujaCatalog;
    private final MandirEventRepository mandirEvents;
    private final PujaBookingRepository pujaBookings;
    private final DarshanPassRepository darshanPasses;
    private final SevakSignupRepository sevakSignups;
    private final TenantRepository tenants;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock = Clock.system(IST);

    public MandirAdminService(MandirSettingRepository mandirSettings,
                              PujaCatalogRepository pujaCatalog,
                              MandirEventRepository mandirEvents,
                              PujaBookingRepository pujaBookings,
                              DarshanPassRepository darshanPasses,
                              SevakSignupRepository sevakSignups,
                              TenantRepository tenants) {
        this.mandirSettings = mandirSettings;
        this.pujaCatalog = pujaCatalog;
        this.mandirEvents = mandirEvents;
        this.pujaBookings = pujaBookings;
        this.darshanPasses = darshanPasses;
        this.sevakSignups = sevakSignups;
        this.tenants = tenants;
    }

    private long currentTenant() {
        Long id = TenantContext.get();
        if (id == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return id;
    }

    // =========================================================================
    // 1. MANDIR SETTINGS & SCHEDULE
    // =========================================================================

    public record AartiItemDto(String name, String time, String description) { }

    public record MandirScheduleDto(
            long id,
            String mandirName,
            String morningHours,
            String eveningHours,
            Boolean isOpenOverride,
            boolean isOpenNow,
            String deity,
            String address,
            String helpline,
            String panchangTithi,
            String nakshatra,
            String specialAnnouncement,
            List<AartiItemDto> aartis
    ) { }

    public record UpdateScheduleRequest(
            String morningHours,
            String eveningHours,
            Boolean isOpenOverride,
            String deity,
            String address,
            String helpline,
            String panchangTithi,
            String nakshatra,
            String specialAnnouncement,
            List<AartiItemDto> aartis
    ) { }

    @Transactional
    public MandirSetting getOrCreateSettings() {
        long tenantId = currentTenant();
        return mandirSettings.findByTenantId(tenantId).orElseGet(() -> {
            Tenant t = tenants.findById(tenantId).orElse(null);
            String name = (t != null) ? t.getName() : "Shri Mandir";
            List<AartiItemDto> defaultAartis = List.of(
                    new AartiItemDto("Mangala Aarti", "05:30 AM", "Morning awakening prayer and first sacred darshan"),
                    new AartiItemDto("Shringar Darshan", "07:30 AM", "Adorning the deity with fresh flowers and sacred vastram"),
                    new AartiItemDto("Rajbhog Aarti", "12:00 PM", "Noon sacred bhog offering followed by afternoon temple rest"),
                    new AartiItemDto("Sandhya Aarti", "07:00 PM", "Evening deepam aarti with holy chantings and bhajans"),
                    new AartiItemDto("Shayan Aarti", "09:00 PM", "Night closing prayer and bedtime lullaby for the deity")
            );
            String aartisJson = null;
            try {
                aartisJson = json.writeValueAsString(defaultAartis);
            } catch (Exception ignored) { }

            MandirSetting s = new MandirSetting(
                    tenantId,
                    "05:00 AM – 01:00 PM",
                    "04:00 PM – 09:30 PM",
                    "Pradhan Devata",
                    "Temple Road, Central Sanctum",
                    "+91 98765 43210",
                    "Shukla Paksha Ekadashi / Trayodashi",
                    "Rohini / Uttara Phalguni",
                    "Special Darshan arrangements for upcoming festival. Senior citizens can use Priority Queue Pass.",
                    aartisJson
            );
            return mandirSettings.save(s);
        });
    }

    @Transactional
    public MandirScheduleDto getScheduleDto() {
        long tenantId = currentTenant();
        MandirSetting s = getOrCreateSettings();
        Tenant t = tenants.findById(tenantId).orElse(null);
        String name = (t != null) ? t.getName() : "Shri Mandir";

        List<AartiItemDto> aartis = parseAartis(s.getAartisJson());
        boolean openNow = s.getIsOpenOverride() != null ? s.getIsOpenOverride() : true;

        return new MandirScheduleDto(
                s.getId(),
                name,
                s.getMorningHours(),
                s.getEveningHours(),
                s.getIsOpenOverride(),
                openNow,
                s.getDeity(),
                s.getAddress(),
                s.getHelpline(),
                s.getPanchangTithi(),
                s.getNakshatra(),
                s.getSpecialAnnouncement(),
                aartis
        );
    }

    @Transactional
    public MandirScheduleDto updateSchedule(UpdateScheduleRequest req) {
        MandirSetting s = getOrCreateSettings();
        String aartisJson = s.getAartisJson();
        if (req.aartis() != null) {
            try {
                aartisJson = json.writeValueAsString(req.aartis());
            } catch (Exception ignored) { }
        }

        s.update(
                req.morningHours(),
                req.eveningHours(),
                req.isOpenOverride(),
                req.deity(),
                req.address(),
                req.helpline(),
                req.panchangTithi(),
                req.nakshatra(),
                req.specialAnnouncement(),
                aartisJson
        );
        mandirSettings.save(s);
        return getScheduleDto();
    }

    private List<AartiItemDto> parseAartis(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return List.of(
                    new AartiItemDto("Mangala Aarti", "05:30 AM", "Morning awakening prayer and first sacred darshan"),
                    new AartiItemDto("Shringar Darshan", "07:30 AM", "Adorning the deity with fresh flowers and sacred vastram"),
                    new AartiItemDto("Rajbhog Aarti", "12:00 PM", "Noon sacred bhog offering followed by afternoon temple rest"),
                    new AartiItemDto("Sandhya Aarti", "07:00 PM", "Evening deepam aarti with holy chantings and bhajans"),
                    new AartiItemDto("Shayan Aarti", "09:00 PM", "Night closing prayer and bedtime lullaby for the deity")
            );
        }
        try {
            return json.readValue(jsonStr, new TypeReference<List<AartiItemDto>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    // =========================================================================
    // 2. PUJA & SANKALP CATALOG & SANKALPAM ROSTER
    // =========================================================================

    public record CreatePujaRequest(
            String code,
            String name,
            String deity,
            String duration,
            Long dakshinaRupees,
            String description,
            Boolean prasadIncluded,
            Boolean active,
            Integer displayOrder
    ) { }

    public record UpdatePujaRequest(
            String name,
            String deity,
            String duration,
            Long dakshinaRupees,
            String description,
            Boolean prasadIncluded,
            Boolean active,
            Integer displayOrder
    ) { }

    @Transactional
    public List<PujaCatalogItem> getAllPujas() {
        long tenantId = currentTenant();
        List<PujaCatalogItem> list = pujaCatalog.findAllByTenantIdOrderByDisplayOrderAsc(tenantId);
        if (list.isEmpty()) {
            seedDefaultPujas(tenantId);
            return pujaCatalog.findAllByTenantIdOrderByDisplayOrderAsc(tenantId);
        }
        return list;
    }

    private void seedDefaultPujas(long tenantId) {
        List<PujaCatalogItem> seeds = List.of(
                new PujaCatalogItem(tenantId, "RUDRABHISHEK", "Shri Rudrabhishek Seva", "Lord Shiva", "45 mins", 1100L,
                        "Vedic panchamrit abhishek with Bilva leaves, chanting Sri Rudram for health and peace.", true, true, 1),
                new PujaCatalogItem(tenantId, "ARCHANA", "Special Ashtothara Archana", "Pradhan Devata", "20 mins", 251L,
                        "108 sacred names chanting with individualized family Sankalp and floral offering.", true, true, 2),
                new PujaCatalogItem(tenantId, "SATYANARAYAN", "Shri Satyanarayan Maha Katha", "Lord Vishnu", "90 mins", 2100L,
                        "Sacred story recitation, family sankalp, panchamrit and prasad preparation.", true, true, 3),
                new PujaCatalogItem(tenantId, "VAHAN_PUJA", "Vahan / Vehicle Blessing Puja", "Lord Ganesha & Hanuman", "30 mins", 501L,
                        "Auspicious blessings, coconut breaking and raksha sutra for new vehicles.", false, true, 4),
                new PujaCatalogItem(tenantId, "NAVGRAH", "Navgrah Shanti Havan", "Navgrah Devatas", "60 mins", 3100L,
                        "Sacred fire offering to pacify planetary afflictions and invoke cosmic harmony.", true, true, 5),
                new PujaCatalogItem(tenantId, "ANNADANAM", "Nitya Annadanam Sponsorship", "Annapurna Devi", "Noon Seva", 5001L,
                        "Sponsor sacred Mahaprasad lunch distribution to 100 pilgrims in the temple annakshetra.", true, true, 6)
        );
        pujaCatalog.saveAll(seeds);
    }

    @Transactional
    public PujaCatalogItem createPuja(CreatePujaRequest req) {
        long tenantId = currentTenant();
        String code = req.code() != null ? req.code().trim().toUpperCase() : "PUJA_" + System.currentTimeMillis() % 10000;
        PujaCatalogItem item = new PujaCatalogItem(
                tenantId,
                code,
                req.name(),
                req.deity(),
                req.duration(),
                req.dakshinaRupees(),
                req.description(),
                req.prasadIncluded(),
                req.active(),
                req.displayOrder()
        );
        return pujaCatalog.save(item);
    }

    @Transactional
    public PujaCatalogItem updatePuja(Long id, UpdatePujaRequest req) {
        PujaCatalogItem item = pujaCatalog.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Puja item not found"));
        item.update(
                req.name(),
                req.deity(),
                req.duration(),
                req.dakshinaRupees(),
                req.description(),
                req.prasadIncluded(),
                req.active(),
                req.displayOrder()
        );
        return pujaCatalog.save(item);
    }

    @Transactional(readOnly = true)
    public List<PujaBooking> getPujaBookings(LocalDate date) {
        long tenantId = currentTenant();
        if (date != null) {
            return pujaBookings.findAllByPujaDateOrderByCreatedAtDesc(date);
        }
        return pujaBookings.findAllByOrderByPujaDateDesc();
    }

    @Transactional
    public PujaBooking markPujaPerformed(Long id, String priestName) {
        PujaBooking booking = pujaBookings.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        booking.markPerformed(priestName != null ? priestName : "Temple Priest", clock.instant());
        return pujaBookings.save(booking);
    }

    // =========================================================================
    // 3. MANDIR UTSAVS & GATE PASS CHECK-IN
    // =========================================================================

    public record CreateEventRequest(
            String code,
            String name,
            LocalDate eventDate,
            String timeRange,
            String description,
            String highlights,
            Boolean registrationOpen,
            Integer maxCapacity,
            Boolean active
    ) { }

    public record UpdateEventRequest(
            String name,
            LocalDate eventDate,
            String timeRange,
            String description,
            String highlights,
            Boolean registrationOpen,
            Integer maxCapacity,
            Boolean active
    ) { }

    @Transactional
    public List<MandirEventEntity> getAllEvents() {
        long tenantId = currentTenant();
        List<MandirEventEntity> list = mandirEvents.findAllByTenantIdOrderByEventDateAsc(tenantId);
        if (list.isEmpty()) {
            seedDefaultEvents(tenantId);
            return mandirEvents.findAllByTenantIdOrderByEventDateAsc(tenantId);
        }
        return list;
    }

    private void seedDefaultEvents(long tenantId) {
        List<MandirEventEntity> seeds = List.of(
                new MandirEventEntity(tenantId, "MAHASHIVRATRI", "Maha Shivratri Mahotsav", LocalDate.of(2026, 2, 17),
                        "All Day & Night Jagran", "Grand 4-Prahar Rudrabhishek, continuous bilva archana, and midnight aarti.",
                        "Free Mahaprasad, Thandai distribution, special queue for seniors", true, 2000, true),
                new MandirEventEntity(tenantId, "RAM_NAVAMI", "Shri Ram Navami Utsav", LocalDate.of(2026, 4, 18),
                        "09:00 AM – 02:00 PM", "Birth celebration of Lord Rama, Sundarkand path, and grand Panakam distribution.",
                        "Pushpa Abhishek at 12:00 Noon, Bhajan Sandhya", true, 1500, true),
                new MandirEventEntity(tenantId, "JANMASHTAMI", "Shri Krishna Janmashtami", LocalDate.of(2026, 8, 25),
                        "06:00 PM – Midnight 12:30 AM", "Midnight birth abhishek, Bhagavad Gita chanting, and Dahi Handi utsav.",
                        "Makhan Mishri prasad, Bal Krishna fancy dress for children", true, 2500, true),
                new MandirEventEntity(tenantId, "NAVRATRI", "Sharad Navratri & Chandi Havan", LocalDate.of(2026, 10, 11),
                        "9 Days Sacred Utsav", "Nine sacred nights of Devi worship, daily kumkum archana and Durga Saptashati parayan.",
                        "Garba & Dandiya in evening, Maha Havan on Ashtami", true, 3000, true)
        );
        mandirEvents.saveAll(seeds);
    }

    @Transactional
    public MandirEventEntity createEvent(CreateEventRequest req) {
        long tenantId = currentTenant();
        String code = req.code() != null ? req.code().trim().toUpperCase() : "EVENT_" + System.currentTimeMillis() % 10000;
        MandirEventEntity event = new MandirEventEntity(
                tenantId,
                code,
                req.name(),
                req.eventDate(),
                req.timeRange(),
                req.description(),
                req.highlights(),
                req.registrationOpen(),
                req.maxCapacity(),
                req.active()
        );
        return mandirEvents.save(event);
    }

    @Transactional
    public MandirEventEntity updateEvent(Long id, UpdateEventRequest req) {
        MandirEventEntity event = mandirEvents.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        event.update(
                req.name(),
                req.eventDate(),
                req.timeRange(),
                req.description(),
                req.highlights(),
                req.registrationOpen(),
                req.maxCapacity(),
                req.active()
        );
        return mandirEvents.save(event);
    }

    @Transactional(readOnly = true)
    public List<DarshanPass> getEventPasses(String eventCode) {
        if (eventCode != null && !eventCode.isBlank()) {
            return darshanPasses.findAllByEventCodeOrderByCreatedAtDesc(eventCode);
        }
        return darshanPasses.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public DarshanPass checkInPass(String tokenOrPassNumber) {
        if (tokenOrPassNumber == null || tokenOrPassNumber.isBlank()) {
            throw new IllegalArgumentException("Pass number or QR token is required");
        }
        String clean = tokenOrPassNumber.trim();
        // If scanned full QR token like MANDIR_TOKEN:PASS-1-123456:1:Name
        if (clean.startsWith("MANDIR_TOKEN:")) {
            String[] parts = clean.split(":");
            if (parts.length > 1) {
                clean = parts[1];
            }
        }

        DarshanPass pass = darshanPasses.findByPassNumber(clean)
                .orElseThrow(() -> new IllegalArgumentException("Pass not found: " + tokenOrPassNumber));
        pass.markCheckedIn(clock.instant());
        return darshanPasses.save(pass);
    }

    // =========================================================================
    // 4. SEVAK & VOLUNTEER HUB ASSIGNMENTS
    // =========================================================================

    public record VolunteerAssignmentRequest(
            String status,
            String assignedTeam,
            String assignedEvent
    ) { }

    @Transactional(readOnly = true)
    public List<SevakSignup> getVolunteers(String sevaArea, String status) {
        List<SevakSignup> list = sevakSignups.findAllByOrderByCreatedAtDesc();
        return list.stream()
                .filter(s -> sevaArea == null || sevaArea.isBlank() || s.getSevaArea().equalsIgnoreCase(sevaArea))
                .filter(s -> status == null || status.isBlank() || s.getStatus().equalsIgnoreCase(status))
                .toList();
    }

    @Transactional
    public SevakSignup updateVolunteerStatus(Long id, VolunteerAssignmentRequest req) {
        SevakSignup s = sevakSignups.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Volunteer signup not found"));
        s.updateAssignment(req.status(), req.assignedTeam(), req.assignedEvent());
        return sevakSignups.save(s);
    }
}
