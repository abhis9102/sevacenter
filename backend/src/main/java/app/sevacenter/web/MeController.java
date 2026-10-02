package app.sevacenter.web;

import app.sevacenter.auth.MeResponse;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.tenant.TenantRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in staff member. Authentication is required (not in the public allowlist). */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final TenantRepository tenants;

    public MeController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal StaffUser user) {
        return MeResponse.of(user, tenants);
    }
}
