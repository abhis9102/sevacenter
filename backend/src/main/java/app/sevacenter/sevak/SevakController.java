package app.sevacenter.sevak;

import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.donation.RateLimiter;
import app.sevacenter.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sevak signups (ADR 0015): public signup on the trust's host (rate-limited, no sign-in); LEADER+
 * reviews them, contact details included. Responses are explicit records, never entities.
 */
@RestController
public class SevakController {

    private final SevakService service;
    private final RateLimiter rateLimiter;

    public SevakController(SevakService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/v1/public/sevak")
    @ResponseStatus(HttpStatus.CREATED)
    public Thanks signUp(@Valid @RequestBody SignupRequest r, HttpServletRequest request) {
        if (TenantContext.get() == null) {
            throw new SevakService.SignupNotFoundException();
        }
        if (!rateLimiter.tryAcquire("sevak:" + request.getRemoteAddr())) {
            throw new app.sevacenter.auth.LoginThrottle.TooManyAttemptsException();
        }
        SevakSignup s = service.signUp(r.fullName(), r.phone(), r.email(), r.sevaAreas(), r.availability(), r.notes());
        return new Thanks(s.getFullName());
    }

    @GetMapping("/api/v1/sevaks")
    @PreAuthorize("hasRole('LEADER')")
    public List<SignupResponse> list() {
        return service.list().stream().map(SignupResponse::of).toList();
    }

    @PostMapping("/api/v1/sevaks/{id:\\d+}/approve")
    @PreAuthorize("hasRole('LEADER')")
    public SignupResponse approve(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return SignupResponse.of(service.review(id, true, staff.userId()));
    }

    @PostMapping("/api/v1/sevaks/{id:\\d+}/decline")
    @PreAuthorize("hasRole('LEADER')")
    public SignupResponse decline(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return SignupResponse.of(service.review(id, false, staff.userId()));
    }

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record SignupRequest(
            @Schema(example = "Ravi Kumar") @NotBlank @Size(max = 120) String fullName,
            @Schema(example = "98765 43210") @Size(max = 30) String phone,
            @Schema(example = "ravi@example.org") @Size(max = 254) String email,
            @Schema(example = "Annadanam kitchen, crowd management") @NotBlank @Size(max = 300) String sevaAreas,
            @Schema(example = "Weekends, festival days") @Size(max = 300) String availability,
            @Schema(example = "Can drive the temple van") @Size(max = 1000) String notes) { }

    /** The public only learns that it worked. */
    public record Thanks(String name) { }

    public record SignupResponse(long id, String fullName, String phone, String email, String sevaAreas,
                                 String availability, String notes, String status, OffsetDateTime createdAt) {
        static SignupResponse of(SevakSignup s) {
            return new SignupResponse(s.getId(), s.getFullName(), s.getPhone(), s.getEmail(), s.getSevaAreas(),
                    s.getAvailability(), s.getNotes(), s.getStatus(), s.getCreatedAt());
        }
    }
}
