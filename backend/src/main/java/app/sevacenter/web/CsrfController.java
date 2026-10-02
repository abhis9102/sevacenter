package app.sevacenter.web;

import java.util.Map;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands the SPA a CSRF token before its first state-changing request. Reading the token
 * also makes Spring write the XSRF-TOKEN cookie (tokens are loaded lazily otherwise).
 */
@RestController
@RequestMapping("/api/v1")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "token", token.getToken());
    }
}
