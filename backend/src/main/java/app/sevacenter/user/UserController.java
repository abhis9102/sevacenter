package app.sevacenter.user;

import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.user.UserManagementService.CreatedUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff management within the caller's tenant. Authorization is declared per endpoint
 * (threat model invariant 5) over the hierarchy TRUST_ADMIN > LEADER > MEMBER. Request
 * bodies are explicit records: tenant, status or password fields in a request are ignored.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserManagementService service;

    public UserController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('LEADER')")
    public List<UserResponse> list() {
        return service.list().stream().map(UserResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public CreatedUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        CreatedUser created = service.create(request.email(), request.displayName(), request.role());
        return new CreatedUserResponse(UserResponse.of(created.user()), created.setupUrl());
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public UserResponse changeRole(@PathVariable long id, @Valid @RequestBody ChangeRoleRequest request) {
        return UserResponse.of(service.changeRole(id, request.role()));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public UserResponse deactivate(@PathVariable long id) {
        return UserResponse.of(service.deactivate(id));
    }

    /** Removes a staff member for good (see UserManagementService#delete for what is kept). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public void delete(@PathVariable long id, @AuthenticationPrincipal StaffUser caller) {
        service.delete(id, caller.userId());
    }

    /** A one-time password reset link for an active staff member (shown once, handed over). */
    @PostMapping("/{id}/reset-link")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public ResetLinkResponse issueResetLink(@PathVariable long id, @AuthenticationPrincipal StaffUser caller) {
        return new ResetLinkResponse(service.issueResetLink(id, caller.userId()));
    }

    public record ResetLinkResponse(String resetUrl) {
    }

    @PostMapping("/{id}/setup-link")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public SetupLinkResponse reissueSetupLink(@PathVariable long id) {
        return new SetupLinkResponse(service.reissueSetupLink(id));
    }

    /** Examples are valid on purpose, so DAST attacks reach the database (see RegistrationRequest). */
    public record CreateUserRequest(
            @Schema(example = "staff@example.org") @NotBlank @Email @Size(max = 254) String email,
            @Schema(example = "Ravi Kumar") @NotBlank @Size(max = 120) String displayName,
            @Schema(example = "MEMBER") @NotNull Role role) {
    }

    public record ChangeRoleRequest(@Schema(example = "LEADER") @NotNull Role role) {
    }

    public record UserResponse(long id, String email, String displayName, Role role, UserStatus status,
                               OffsetDateTime createdAt) {
        static UserResponse of(AppUser u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.getStatus(),
                    u.getCreatedAt());
        }
    }

    /** The setup link is returned once, to the admin who hands it over; it's never stored. */
    public record CreatedUserResponse(UserResponse user, String setupUrl) {
    }

    public record SetupLinkResponse(String setupUrl) {
    }
}
