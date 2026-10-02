package app.sevacenter.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload to register a new trust and its first administrator. */
public record RegistrationRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$",
                message = "slug must be 3-40 lowercase letters, digits or hyphens")
        String slug,

        @NotBlank String trustName,

        @NotBlank @Email String adminEmail,

        @NotBlank @Size(min = 12, max = 200, message = "password must be at least 12 characters")
        String adminPassword,

        @NotBlank String adminName) {
}
