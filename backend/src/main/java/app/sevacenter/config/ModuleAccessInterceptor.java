package app.sevacenter.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.user.ModuleAccess;
import app.sevacenter.user.StaffModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces per-module access limits on every staff API call (ADR 0021), on top of the role checks:
 * NONE denies the module outright, VIEW denies anything but GET/HEAD. Mapped by path prefix in one
 * place; ModuleAccessTest fails if a new staff endpoint is in neither a module nor the explicit
 * list of non-module paths, so nothing can slip past unmapped.
 */
public class ModuleAccessInterceptor implements HandlerInterceptor {

    static final Map<String, StaffModule> MODULES = Map.ofEntries(
            Map.entry("/api/v1/devotees", StaffModule.DEVOTEES),
            Map.entry("/api/v1/donations", StaffModule.DONATIONS),
            Map.entry("/api/v1/receipts", StaffModule.DONATIONS),
            Map.entry("/api/v1/donation-funds", StaffModule.DONATIONS),
            Map.entry("/api/v1/trust-profile", StaffModule.DONATIONS),
            Map.entry("/api/v1/events", StaffModule.EVENTS),
            Map.entry("/api/v1/pujas", StaffModule.PUJAS),
            Map.entry("/api/v1/puja-bookings", StaffModule.PUJAS),
            Map.entry("/api/v1/priests", StaffModule.PUJAS),
            Map.entry("/api/v1/sevaks", StaffModule.VOLUNTEERS),
            Map.entry("/api/v1/temple", StaffModule.TEMPLE));

    /** Staff paths that are deliberately not modules: account, admin-only (by role), public, portal. */
    static final List<String> NOT_MODULES = List.of("/api/v1/users", "/api/v1/me", "/api/v1/profile",
            "/api/v1/payment-settings", "/api/v1/audit", "/api/v1/public/", "/api/v1/portal/", "/api/v1/auth/",
            "/api/v1/csrf", "/api/v1/ping", "/api/v1/register");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof StaffUser staff)) {
            return true; // not a staff request: the security rules already decided
        }
        Optional<StaffModule> module = moduleOf(request.getRequestURI());
        if (module.isEmpty()) {
            return true;
        }
        ModuleAccess access = staff.access(module.get());
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        if (access == ModuleAccess.NONE || (access == ModuleAccess.VIEW && !read)) {
            // Same 403 and body as a role denial: the response doesn't say why.
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    static Optional<StaffModule> moduleOf(String path) {
        return MODULES.entrySet().stream()
                .filter(e -> path.equals(e.getKey()) || path.startsWith(e.getKey() + "/"))
                .map(Map.Entry::getValue).findFirst();
    }
}
