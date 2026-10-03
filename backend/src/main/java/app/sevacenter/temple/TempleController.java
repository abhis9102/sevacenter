package app.sevacenter.temple;

import java.time.Clock;
import java.time.OffsetDateTime;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.donation.OnlineDonationService;
import app.sevacenter.event.EventService;
import app.sevacenter.puja.PujaService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
 */
@RestController
public class TempleController {

    private final TempleProfileRepository profiles;
    private final TenantRepository tenants;
    private final OnlineDonationService payments;
    private final EventService events;
    private final PujaService pujas;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.systemUTC();

    public TempleController(TempleProfileRepository profiles, TenantRepository tenants, OnlineDonationService payments,
                            EventService events, PujaService pujas, AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.profiles = profiles;
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
        return ResponseEntity.ok(new PublicTemple(name,
                p == null ? null : p.getDeity(), p == null ? null : p.getAddress(), p == null ? null : p.getHelpline(),
                p == null ? null : p.getTimings(), p == null ? null : p.getAnnouncement(),
                payments.settings().isPresent(), !events.upcoming().isEmpty(), !pujas.publicCatalog().isEmpty()));
    }

    @PutMapping("/api/v1/temple")
    @PreAuthorize("hasRole('LEADER')")
    @Transactional
    public Profile save(@Valid @RequestBody Profile r, @AuthenticationPrincipal StaffUser staff) {
        long tenantId = TenantContext.get();
        TempleProfile p = profiles.findById(tenantId).orElseGet(() -> new TempleProfile(tenantId));
        p.edit(clean(r.deity()), clean(r.address()),
                r.helpline() == null || r.helpline().isBlank() ? null : DevoteeService.phone(r.helpline()),
                clean(r.timings()), clean(r.announcement()), staff.userId(), OffsetDateTime.now(clock));
        TempleProfile saved = profiles.save(p);
        auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_profile", null, null);
        return new Profile(saved.getDeity(), saved.getAddress(), saved.getHelpline(), saved.getTimings(), saved.getAnnouncement());
    }

    @GetMapping("/api/v1/temple")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public Profile get() {
        TempleProfile p = profiles.findById(TenantContext.get()).orElse(null);
        return p == null ? new Profile(null, null, null, null, null)
                : new Profile(p.getDeity(), p.getAddress(), p.getHelpline(), p.getTimings(), p.getAnnouncement());
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

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record Profile(
            @Schema(example = "Shri Siddheshwar") @Size(max = 120) String deity,
            @Schema(example = "1 Temple Road, Pune 411001") @Size(max = 400) String address,
            @Schema(example = "98765 43210") @Size(max = 30) String helpline,
            @Schema(example = "Darshan 5:30 am - 12:30 pm, 4 pm - 9 pm") @Size(max = 500) String timings,
            @Schema(example = "Annual utsav begins on Sunday.") @Size(max = 1000) String announcement) { }

    /** Public: the temple's own words plus which services it actually offers right now. */
    public record PublicTemple(String trustName, String deity, String address, String helpline, String timings,
                               String announcement, boolean onlineDonations, boolean upcomingEvents, boolean pujaBooking) { }
}
