package app.sevacenter.auth;

/** What a successful registration returns — never the password or internal detail. */
public record RegistrationResponse(String slug, Long tenantId, String adminUrl, String publicUrl) {
}
