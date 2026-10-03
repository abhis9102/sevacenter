package app.sevacenter.portal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff back-office API for managing Mandir Center:
 * Schedules & Aartis, Puja Catalog & Family Sankalp Roster,
 * Utsavs & Gate Pass Check-in, and Sevak/Volunteer assignments.
 */
@RestController
@RequestMapping("/api/v1/mandir")
public class MandirAdminController {

    private final MandirAdminService adminService;

    public MandirAdminController(MandirAdminService adminService) {
        this.adminService = adminService;
    }

    // =========================================================================
    // 1. MANDIR SETTINGS & SCHEDULE
    // =========================================================================

    @GetMapping("/settings")
    public ResponseEntity<MandirAdminService.MandirScheduleDto> getSettings() {
        return ResponseEntity.ok(adminService.getScheduleDto());
    }

    @PutMapping("/settings")
    public ResponseEntity<MandirAdminService.MandirScheduleDto> updateSettings(
            @Valid @RequestBody MandirAdminService.UpdateScheduleRequest req) {
        return ResponseEntity.ok(adminService.updateSchedule(req));
    }

    // =========================================================================
    // 2. PUJAS & SANKALP CATALOG & SANKALPAM ROSTER
    // =========================================================================

    @GetMapping("/pujas")
    public ResponseEntity<List<PujaCatalogItem>> getAllPujas() {
        return ResponseEntity.ok(adminService.getAllPujas());
    }

    @PostMapping("/pujas")
    public ResponseEntity<PujaCatalogItem> createPuja(
            @Valid @RequestBody MandirAdminService.CreatePujaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createPuja(req));
    }

    @PutMapping("/pujas/{id}")
    public ResponseEntity<PujaCatalogItem> updatePuja(
            @PathVariable Long id,
            @Valid @RequestBody MandirAdminService.UpdatePujaRequest req) {
        return ResponseEntity.ok(adminService.updatePuja(id, req));
    }

    @GetMapping("/pujas/bookings")
    public ResponseEntity<List<PujaBooking>> getPujaBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(adminService.getPujaBookings(date));
    }

    public record MarkPerformedRequest(String priestName) { }

    @PostMapping("/pujas/bookings/{id}/complete")
    public ResponseEntity<PujaBooking> markPujaPerformed(
            @PathVariable Long id,
            @RequestBody(required = false) MarkPerformedRequest req) {
        String priestName = (req != null) ? req.priestName() : "Temple Priest";
        return ResponseEntity.ok(adminService.markPujaPerformed(id, priestName));
    }

    // =========================================================================
    // 3. MANDIR UTSAVS & GATE PASS CHECK-IN
    // =========================================================================

    @GetMapping("/events")
    public ResponseEntity<List<MandirEventEntity>> getAllEvents() {
        return ResponseEntity.ok(adminService.getAllEvents());
    }

    @PostMapping("/events")
    public ResponseEntity<MandirEventEntity> createEvent(
            @Valid @RequestBody MandirAdminService.CreateEventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createEvent(req));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<MandirEventEntity> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody MandirAdminService.UpdateEventRequest req) {
        return ResponseEntity.ok(adminService.updateEvent(id, req));
    }

    @GetMapping("/events/{eventCode}/passes")
    public ResponseEntity<List<DarshanPass>> getEventPasses(@PathVariable String eventCode) {
        return ResponseEntity.ok(adminService.getEventPasses(eventCode));
    }

    public record CheckInRequest(String tokenOrPassNumber) { }

    @PostMapping("/passes/check-in")
    public ResponseEntity<DarshanPass> checkInPass(@Valid @RequestBody CheckInRequest req) {
        return ResponseEntity.ok(adminService.checkInPass(req.tokenOrPassNumber()));
    }

    // =========================================================================
    // 4. SEVAK & VOLUNTEER HUB ASSIGNMENTS
    // =========================================================================

    @GetMapping("/volunteers")
    public ResponseEntity<List<SevakSignup>> getVolunteers(
            @RequestParam(required = false) String sevaArea,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.getVolunteers(sevaArea, status));
    }

    @PutMapping("/volunteers/{id}/status")
    public ResponseEntity<SevakSignup> updateVolunteerStatus(
            @PathVariable Long id,
            @Valid @RequestBody MandirAdminService.VolunteerAssignmentRequest req) {
        return ResponseEntity.ok(adminService.updateVolunteerStatus(id, req));
    }
}
