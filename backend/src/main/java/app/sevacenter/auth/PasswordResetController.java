package app.sevacenter.auth;

import java.util.Optional;

import app.sevacenter.user.PasswordResetService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final boolean isDev;

    public PasswordResetController(PasswordResetService passwordResetService, Environment env) {
        this.passwordResetService = passwordResetService;
        this.isDev = env.acceptsProfiles(Profiles.of("local", "test", "default"));
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        Optional<String> tokenOpt = passwordResetService.requestReset(request.email());
        String devToken = (isDev && tokenOpt.isPresent()) ? tokenOpt.get() : null;
        return new ForgotPasswordResponse(
                "If an account exists with this email, password reset instructions have been sent.",
                devToken
        );
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    public record ForgotPasswordRequest(
            @Schema(example = "priya@example.org")
            @NotBlank @Email String email
    ) { }

    public record ForgotPasswordResponse(
            String message,
            @Schema(description = "Only populated in local/test environments for development convenience")
            String devToken
    ) { }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 200, message = "password must be at least 12 characters")
            String newPassword
    ) { }
}
