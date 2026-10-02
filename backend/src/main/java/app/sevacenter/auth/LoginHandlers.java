package app.sevacenter.auth;

import java.io.IOException;
import java.util.Map;

import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON outcomes for Spring Security's form login (ADR 0009). Session fixation protection,
 * CSRF token rotation and saving the security context are done by the framework before
 * these run. Every failure looks the same: no hint whether the email exists, the account is
 * disabled, or the password was wrong.
 */
@Component
public class LoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    static final String EMAIL_PARAM = "email";

    private final LoginThrottle throttle;
    private final TenantRepository tenants;
    private final JsonMapper json;

    public LoginHandlers(LoginThrottle throttle, TenantRepository tenants, JsonMapper json) {
        this.throttle = throttle;
        this.tenants = tenants;
        this.json = json;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        StaffUser user = (StaffUser) authentication.getPrincipal();
        throttle.reset(LoginThrottle.accountKey(user.tenantId(), user.email()));
        write(response, HttpServletResponse.SC_OK, MeResponse.of(user, tenants));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        throttle.recordFailure(accountKey(request));
        throttle.recordFailure(LoginThrottle.ipKey(request.getRemoteAddr()));
        write(response, HttpServletResponse.SC_UNAUTHORIZED, Map.of("error", "invalid_credentials"));
    }

    static String accountKey(HttpServletRequest request) {
        Long tenantId = TenantContext.get();
        return LoginThrottle.accountKey(tenantId == null ? -1 : tenantId, request.getParameter(EMAIL_PARAM));
    }

    private void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }
}
