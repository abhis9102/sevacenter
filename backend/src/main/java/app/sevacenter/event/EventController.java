package app.sevacenter.event;

import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.event.EventService.EventDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff side of events (ADR 0014). MEMBER: see events, check passes in at the gate (name and head
 * count only). LEADER: create, edit, publish, cancel; see registrations with contact details.
 * Responses are explicit records, never entities.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public List<EventResponse> list() {
        return service.list().stream().map(e -> EventResponse.of(e, service.seatsTaken(e.getId()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public EventResponse create(@Valid @RequestBody EventRequest r, @AuthenticationPrincipal StaffUser staff) {
        return EventResponse.of(service.create(r.details(), staff.userId()), 0);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    public EventResponse update(@PathVariable long id, @Valid @RequestBody EventRequest r,
                                @AuthenticationPrincipal StaffUser staff) {
        return EventResponse.of(service.update(id, r.details(), staff.userId()), service.seatsTaken(id));
    }

    @PostMapping("/{id:\\d+}/publish")
    @PreAuthorize("hasRole('LEADER')")
    public EventResponse publish(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return EventResponse.of(service.changeStatus(id, EventStatus.PUBLISHED, staff.userId()), service.seatsTaken(id));
    }

    @PostMapping("/{id:\\d+}/cancel")
    @PreAuthorize("hasRole('LEADER')")
    public EventResponse cancel(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return EventResponse.of(service.changeStatus(id, EventStatus.CANCELLED, staff.userId()), service.seatsTaken(id));
    }

    @GetMapping("/{id:\\d+}/passes")
    @PreAuthorize("hasRole('LEADER')")
    public List<PassResponse> passes(@PathVariable long id) {
        return service.passesOf(id).stream().map(PassResponse::of).toList();
    }

    @PostMapping("/{id:\\d+}/passes/{passId:\\d+}/cancel")
    @PreAuthorize("hasRole('LEADER')")
    public PassResponse cancelPass(@PathVariable long id, @PathVariable long passId, @AuthenticationPrincipal StaffUser staff) {
        return PassResponse.of(service.cancelPass(id, passId, staff.userId()));
    }

    /** A walk-in pass issued at the counter or gate (ADR 0026); contact optional. */
    @PostMapping("/{id:\\d+}/passes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MEMBER')")
    public PassResponse issuePass(@PathVariable long id, @Valid @RequestBody CounterPassRequest r,
                                  @AuthenticationPrincipal StaffUser staff) {
        return PassResponse.of(service.issueAtCounter(id, r.name(), r.count(), r.phone(), r.email(), staff.userId()));
    }

    /** At the gate. Volunteers (MEMBER) see only the name and head count. */
    @PostMapping("/{id:\\d+}/check-in")
    @PreAuthorize("hasRole('MEMBER')")
    public CheckInResponse checkIn(@PathVariable long id, @Valid @RequestBody CheckInRequest r,
                                   @AuthenticationPrincipal StaffUser staff) {
        return CheckInResponse.of(service.checkIn(id, r.passCode(), staff.userId()));
    }

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record EventRequest(
            @Schema(example = "Janmashtami Darshan") @NotBlank @Size(max = 120) String title,
            @Schema(example = "Midnight darshan and aarti.") @Size(max = 2000) String description,
            @Schema(example = "2030-08-15T22:00:00+05:30") @NotNull OffsetDateTime startsAt,
            @Schema(example = "2030-08-16T01:00:00+05:30") @NotNull OffsetDateTime endsAt,
            @Schema(example = "500") @Min(1) @Max(100000) Integer capacity,
            @Schema(example = "true") boolean registrationOpen) {
        EventDetails details() {
            return new EventDetails(title, description, startsAt, endsAt, capacity, registrationOpen);
        }
    }

    public record EventResponse(long id, String title, String description, OffsetDateTime startsAt,
                                OffsetDateTime endsAt, Integer capacity, long seatsTaken, EventStatus status,
                                boolean registrationOpen) {
        static EventResponse of(Event e, long taken) {
            return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getStartsAt(), e.getEndsAt(),
                    e.getCapacity(), taken, e.getStatus(), e.isRegistrationOpen());
        }
    }

    /** LEADER+: includes contact details. */
    public record PassResponse(long id, String passCode, String attendeeName, int attendeeCount, String phone,
                               String email, String status, OffsetDateTime checkedInAt, OffsetDateTime createdAt) {
        static PassResponse of(EventPass p) {
            return new PassResponse(p.getId(), p.getPassCode(), p.getAttendeeName(), p.getAttendeeCount(), p.getPhone(),
                    p.getEmail(), p.getStatus(), p.getCheckedInAt(), p.getCreatedAt());
        }
    }

    public record CounterPassRequest(
            @Schema(example = "Lakshmi Iyer") @NotBlank @Size(max = 120) String name,
            @Schema(example = "2") @Min(1) @Max(10) int count,
            @Schema(example = "98765 43210") @Size(max = 30) String phone,
            @Schema(example = "lakshmi@example.org") @Size(max = 254) String email) { }

    public record CheckInRequest(@Schema(example = "ABCD234567") @NotBlank @Size(max = 20) String passCode) { }

    /** Gate view: no contact details. */
    public record CheckInResponse(String attendeeName, int attendeeCount, OffsetDateTime checkedInAt) {
        static CheckInResponse of(EventPass p) {
            return new CheckInResponse(p.getAttendeeName(), p.getAttendeeCount(), p.getCheckedInAt());
        }
    }
}
