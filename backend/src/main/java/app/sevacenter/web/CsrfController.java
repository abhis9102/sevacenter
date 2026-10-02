package app.sevacenter.web;

import java.util.Map;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands the SPA a CSRF token before its first state-changing request. Reading the token
 * also makes Spring write the XSRF-TOKEN cookie (tokens are loaded lazily otherwise).
 *
 * <p>The {@link CsrfToken} argument is injected by Spring, not sent by the client, so it is
 * hidden from the OpenAPI spec. Documenting it as a query parameter invited tokens in URLs
 * (found by DAST, G5).
 */
@RestController
@RequestMapping("/api/v1")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, String> csrf(@Parameter(hidden = true) CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "token", token.getToken());
    }
}
