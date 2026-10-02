package app.sevacenter.auth;

import java.io.IOException;

import app.sevacenter.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A staff session is only valid on its own tenant's host. If the authenticated user's tenant
 * differs from the tenant resolved from the Host (or no tenant was resolved), the session is
 * invalidated and the request answered 401. Browsers scope cookies per host anyway; this holds
 * even where that doesn't (header override in dev, custom domains later, a stolen cookie
 * replayed elsewhere).
 */
public class TenantBindingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof StaffUser user) {
            Long hostTenant = TenantContext.get();
            if (hostTenant == null || hostTenant != user.tenantId()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
