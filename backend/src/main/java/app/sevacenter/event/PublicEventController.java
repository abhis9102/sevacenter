package app.sevacenter.event;

import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.donation.RateLimiter;
import app.sevacenter.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public events on the trust's own host (ADR 0014): upcoming published events, and free
 * registration that returns a pass code. Rate-limited per client IP; no sign-in.
 */
@RestController
public class PublicEventController {

    private final EventService service;
    private final RateLimiter rateLimiter;

    public PublicEventController(EventService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/api/v1/public/events")
    public ResponseEntity<List<PublicEvent>> upcoming() {
        if (TenantContext.get() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(service.upcoming().stream().map(e -> PublicEvent.of(e, service.seatsTaken(e.getId()))).toList());
    }

    @PostMapping("/api/v1/public/events/{id:\\d+}/register")
    public ResponseEntity<PassIssued> register(@PathVariable long id, @Valid @RequestBody RegisterRequest r,
                                               HttpServletRequest request) {
        if (TenantContext.get() == null) {
            throw new EventService.EventNotFoundException();
        }
        if (!rateLimiter.tryAcquire("pass:" + request.getRemoteAddr())) {
            throw new app.sevacenter.auth.LoginThrottle.TooManyAttemptsException();
        }
        EventPass p = service.register(id, r.name(), r.count(), r.phone(), r.email());
        Event e = service.get(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PassIssued(p.getPassCode(), e.getTitle(),
                e.getStartsAt(), p.getAttendeeName(), p.getAttendeeCount()));
    }

    /** What the public may know: no registrations, just how many places are left. */
    public record PublicEvent(long id, String title, String description, OffsetDateTime startsAt, OffsetDateTime endsAt,
                              Long placesLeft, boolean registrationOpen) {
        static PublicEvent of(Event e, long taken) {
            Long left = e.getCapacity() == null ? null : Math.max(0, e.getCapacity() - taken);
            return new PublicEvent(e.getId(), e.getTitle(), e.getDescription(), e.getStartsAt(), e.getEndsAt(), left,
                    e.takesRegistrations(OffsetDateTime.now()));
        }
    }

    public record RegisterRequest(
            @Schema(example = "Lakshmi Iyer") @NotBlank @Size(max = 120) String name,
            @Schema(example = "2") @Min(1) @Max(10) int count,
            @Schema(example = "98765 43210") @Size(max = 30) String phone,
            @Schema(example = "lakshmi@example.org") @Size(max = 254) String email) { }

    public record PassIssued(String passCode, String eventTitle, OffsetDateTime startsAt, String name, int count) { }
}
