package app.sevacenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Baseline security policy (M0).
 *
 * <p>Secure-by-default: every request requires authentication except an explicit
 * allowlist of public endpoints. Real authentication (registration/login, sessions,
 * tenant-aware authorization) arrives in M1; HTTP Basic is a placeholder mechanism so
 * protected routes are genuinely protected in the meantime.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Endpoints intentionally reachable without authentication. Keep this list short. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/ping",
            "/api/v1/register",
            "/api/v1/csrf",
            "/actuator/health",
            "/actuator/health/**",
            // Spring's error dispatch. Without this, a 403/404/400 is re-checked on the
            // forward to /error and surfaces as a misleading 401. Error bodies are already
            // stripped of message/stacktrace (server.error.* in application.yml).
            "/error",
            // API documentation (OpenAPI JSON + Swagger UI). Fine to expose in dev;
            // revisit before production (restrict or disable in the prod profile).
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .httpBasic(Customizer.withDefaults());
        return http.build();
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
