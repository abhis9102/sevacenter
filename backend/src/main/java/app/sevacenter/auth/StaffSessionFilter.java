package app.sevacenter.auth;

import java.io.IOException;

import app.sevacenter.tenant.TenantContext;
import app.sevacenter.user.AppUser;
import app.sevacenter.user.AppUserRepository;
import app.sevacenter.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates a staff session on every request, before authorization:
 * <ol>
 *   <li><b>Tenant binding:</b> the session is only valid on its own tenant's host. Browsers
 *       scope cookies per host anyway; this holds where that doesn't (header override in dev,
 *       custom domains later, a stolen cookie replayed elsewhere).</li>
 *   <li><b>Revocation:</b> the user is re-read from the database. A deactivated (or deleted)
 *       user's session ends now, not when it times out; a role change applies to the very next
 *       request, since authorities come from the database, not from the login-time snapshot.</li>
 * </ol>
 * A failed check invalidates the session and answers 401.
 */
public class StaffSessionFilter extends OncePerRequestFilter {

    private final AppUserRepository users;

    public StaffSessionFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof StaffUser user) {
            Long hostTenant = TenantContext.get();
            AppUser current = hostTenant != null && hostTenant == user.tenantId()
                    ? users.findById(user.userId()).orElse(null) : null;
            if (current == null || current.getStatus() != UserStatus.ACTIVE) {
                reject(request, response);
                return;
            }
            StaffUser fresh = new StaffUser(current);
            fresh.eraseCredentials();
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
    }
}
