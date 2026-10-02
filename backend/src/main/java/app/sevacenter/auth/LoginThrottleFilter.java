package app.sevacenter.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses login attempts for a locked account or IP before any password is checked, with 429.
 * A locked key gets 429 even with the right password, so lockout can't be used to confirm a
 * guess. Locks apply to non-existent emails too, so a 429 says nothing about whether an
 * account exists.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginThrottle throttle;
    private final RequestMatcher loginRequest;

    /**
     * Matches login requests the same way Spring Security's form login does for
     * {@code loginProcessingUrl}. A hand-rolled comparison (servlet path) missed every request
     * in testing; two different notions of "the login URL" would also let a variant that
     * reaches the login filter bypass the throttle.
     */
    public LoginThrottleFilter(LoginThrottle throttle, String loginPath) {
        this.throttle = throttle;
        this.loginRequest = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, loginPath);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !loginRequest.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (throttle.isLocked(LoginHandlers.accountKey(request))
                || throttle.isLocked(LoginThrottle.ipKey(request.getRemoteAddr()))) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"too_many_attempts\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
