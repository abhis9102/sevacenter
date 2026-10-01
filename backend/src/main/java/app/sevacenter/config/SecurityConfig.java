package app.sevacenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
            "/actuator/health",
            "/actuator/health/**",
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
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
