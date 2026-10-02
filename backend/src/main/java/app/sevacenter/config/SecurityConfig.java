package app.sevacenter.config;

import app.sevacenter.auth.LoginHandlers;
import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.auth.LoginThrottleFilter;
import app.sevacenter.auth.TenantBindingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;

/**
 * Security policy.
 *
 * <p>Secure-by-default: every request requires authentication except an explicit allowlist
 * of public endpoints. Staff authenticate with a server-side session (ADR 0007/0009): form
 * login at {@value #LOGIN_PATH} on their tenant's host, CSRF on every state-changing request,
 * the session bound to that tenant ({@link TenantBindingFilter}), and roles enforced with
 * {@code @PreAuthorize} over a TRUST_ADMIN > LEADER > MEMBER hierarchy.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Staff login (form-encoded email + password, CSRF-protected) and logout (ADR 0009). */
    public static final String LOGIN_PATH = "/api/v1/auth/login";
    public static final String LOGOUT_PATH = "/api/v1/auth/logout";
    /** Must match server.servlet.session.cookie.name in application.yml. */
    private static final String SESSION_COOKIE = "SC_SESSION";

    /** Endpoints intentionally reachable without authentication. Keep this list short. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/ping",
            "/api/v1/register",
            "/api/v1/csrf",
            "/actuator/health",
            "/actuator/health/**",
            // Spring's error dispatch. Without this, a 403/404/400 is re-checked on the
            // forward to /error and surfaces as a misleading 401. Error bodies carry no
            // internals (ApiErrorController).
            "/error",
            // API documentation (OpenAPI JSON + Swagger UI). Fine to expose in dev;
            // revisit before production (restrict or disable in the prod profile).
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginHandlers loginHandlers,
                                            LoginThrottle loginThrottle) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                // CSRF stays ON (ADR 0007): auth is an HttpOnly session cookie, which the
                // browser attaches automatically, so state-changing requests must prove they
                // came from our frontend. SPA pattern: the token is set in a readable
                // XSRF-TOKEN cookie and the client echoes it in the X-XSRF-TOKEN header; a
                // cross-site page cannot read our cookie, so it cannot forge the header.
                // Plain (non-XOR) handler is fine: BREACH needs the secret and attacker-
                // reflected input in the same compressed body; /csrf reflects no input.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // DAST (G5): other origins may not embed our responses as resources (img/script).
                .headers(headers -> headers
                        .crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN)))
                // Staff login via the framework's form login, so session-fixation protection,
                // CSRF token rotation and saving the security context aren't hand-written.
                // loginPage is set only to switch off Spring's generated HTML login page.
                .formLogin(form -> form
                        .loginPage(LOGIN_PATH)
                        .loginProcessingUrl(LOGIN_PATH)
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginHandlers)
                        .failureHandler(loginHandlers)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_PATH)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies(SESSION_COOKIE))
                // An API answers 401, never a redirect to a login page or a Basic-auth challenge.
                // sendError routes it through ApiErrorController: the same safe JSON as every error.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .addFilterBefore(new LoginThrottleFilter(loginThrottle, LOGIN_PATH),
                        UsernamePasswordAuthenticationFilter.class)
                // After the session's security context is loaded, before authorization.
                .addFilterBefore(new TenantBindingFilter(), ExceptionTranslationFilter.class);
        return http.build();
    }

    /** TRUST_ADMIN can do everything a LEADER can, who can do everything a MEMBER can. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_TRUST_ADMIN > ROLE_LEADER
                ROLE_LEADER > ROLE_MEMBER
                """);
    }

    /**
     * XSRF-TOKEN must stay readable by our own JS (no HttpOnly; ADR 0007), but it should never
     * ride along on cross-site requests: SameSite=Strict (found by DAST, G5). Secure is added
     * by the HTTPS deployment profile (M6), since local dev runs on plain http.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        return repository;
    }

    /**
     * Delegating encoder: stores hashes with an algorithm prefix (default bcrypt) so the
     * scheme can be upgraded later without breaking existing hashes. Never store plaintext.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
