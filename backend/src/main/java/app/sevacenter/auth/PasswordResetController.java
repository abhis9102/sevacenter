package app.sevacenter.auth;

import app.sevacenter.user.PasswordResetService;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * Redeems a one-time password reset link (issued by a trust admin, see UserController). Public
 * (the user can't sign in) but CSRF-protected. There is deliberately no "forgot password" endpoint
 * that hands out tokens: without email delivery, whoever asked would get the token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    public record ResetPasswordRequest(
            @Schema(example = "3f2a9c1e7b6d4a5f8e0c2b1a9d7f6e5c4b3a2918f7e6d5c4b3a29180f7e6d5c4")
            @NotBlank @Size(max = 100) String token,
            @Schema(example = "correct-horse-battery-staple")
            @NotBlank @Size(min = 12, max = 200, message = "password must be at least 12 characters")
            String newPassword
    ) { }
}
