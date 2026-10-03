package app.sevacenter.auth;

import app.sevacenter.user.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Completes a one-time setup link: public (the user has no password yet) but CSRF-protected,
 * and only valid on the tenant's own host (the token table is RLS-scoped). The token is a
 * 256-bit random value, so guessing one isn't a realistic attack.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SetupController {

    private final UserManagementService users;

    public SetupController(UserManagementService users) {
        this.users = users;
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeSetup(@Valid @RequestBody SetupRequest request) {
        users.completeSetup(request.token(), request.password());
    }

    public record SetupRequest(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 12, max = 200, message = "password must be at least 12 characters")
            String password) {
    }
}
