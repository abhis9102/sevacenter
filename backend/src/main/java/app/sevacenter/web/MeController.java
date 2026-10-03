package app.sevacenter.web;

import app.sevacenter.auth.MeResponse;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.tenant.Tenant;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.user.AppUser;
import app.sevacenter.user.AppUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in staff member. Authentication is required (not in the public allowlist). */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final TenantRepository tenants;
    private final AppUserRepository users;

    public MeController(TenantRepository tenants, AppUserRepository users) {
        this.tenants = tenants;
        this.users = users;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal StaffUser user) {
        AppUser u = users.findById(user.userId()).orElse(null);
        if (u != null) {
            String slug = tenants.findById(u.getTenantId()).map(Tenant::getSlug).orElse(null);
            return new MeResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), slug, u.hasAvatar(),
                    u.getModuleLimits());
        }
        return MeResponse.of(user, tenants);
    }
}
