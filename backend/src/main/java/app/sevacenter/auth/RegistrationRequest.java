package app.sevacenter.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload to register a new trust and its first administrator.
 *
 * <p>The OpenAPI examples are valid values on purpose: API clients and the DAST scan (G5) build
 * requests from them, so each attack varies one field while the others pass validation.
 */
public record RegistrationRequest(
        @Schema(example = "siddheshwar")
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$",
                message = "slug must be 3-40 lowercase letters, digits or hyphens")
        String slug,

        @Schema(example = "Shri Siddheshwar Seva Trust")
        @NotBlank String trustName,

        @Schema(example = "admin@example.org")
        @NotBlank @Email String adminEmail,

        @Schema(example = "correct-horse-battery-staple")
        @NotBlank @Size(min = 12, max = 200, message = "password must be at least 12 characters")
        String adminPassword,

        @Schema(example = "Priya Sharma")
        @NotBlank String adminName) {
}
