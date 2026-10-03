package app.sevacenter.auth;

import app.sevacenter.tenant.TenantRepository;

/** The signed-in staff member, as returned by login and {@code GET /api/v1/me}. */
public record MeResponse(long userId, String email, String displayName, String role, String tenant, boolean hasAvatar,
                         java.util.Map<app.sevacenter.user.StaffModule, app.sevacenter.user.ModuleAccess> moduleLimits) {

    public static MeResponse of(StaffUser user, TenantRepository tenants) {
        String slug = tenants.findById(user.tenantId()).map(t -> t.getSlug()).orElse(null);
        return new MeResponse(user.userId(), user.email(), user.displayName(), user.role().name(), slug, false,
                user.moduleLimits());
    }
}
