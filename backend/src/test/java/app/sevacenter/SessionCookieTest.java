package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Cookie flags over a real server: the CSRF cookie (SameSite=Strict, readable by our SPA) and
 * the staff session cookie (HttpOnly, Secure, SameSite=Lax, not named JSESSIONID). Raw HTTP
 * because the JDK client won't set a Host header, and the tenant comes from the Host.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieTest {

    private static final Pattern XSRF = Pattern.compile("Set-Cookie: XSRF-TOKEN=([^;\\r\\n]+)", Pattern.CASE_INSENSITIVE);

    @LocalServerPort
    private int port;
    @Autowired
    private RegistrationService registration;

    @Test
    void sessionAndCsrfCookiesCarryTheRightFlags() throws Exception {
        String slug = "cookie-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin@" + slug + ".example";
        String password = "correct-horse-battery-staple";
        registration.register(new RegistrationRequest(slug, "Trust " + slug, email, password, "Admin"));
        String host = slug + ".sevacenter.app";

        String csrf = http("GET /api/v1/csrf HTTP/1.1\r\nHost: " + host + "\r\n", "");
        Matcher token = XSRF.matcher(csrf);
        assertThat(token.find()).isTrue();
        String xsrfCookie = csrf.lines().filter(l -> l.contains("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(xsrfCookie).containsIgnoringCase("SameSite=Strict")
                // Deliberately not HttpOnly: the SPA echoes it in X-XSRF-TOKEN (ADR 0007).
                .doesNotContainIgnoringCase("HttpOnly");
        String body = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        String response = http("POST /api/v1/auth/login HTTP/1.1\r\nHost: " + host + "\r\n"
                + "Cookie: XSRF-TOKEN=" + token.group(1) + "\r\nX-XSRF-TOKEN: " + token.group(1) + "\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n", body);

        assertThat(response).startsWith("HTTP/1.1 200");
        List<String> cookies = response.lines().filter(l -> l.regionMatches(true, 0, "Set-Cookie:", 0, 11)).toList();
        String session = cookies.stream().filter(c -> c.contains("SC_SESSION=")).findFirst().orElseThrow();
        assertThat(session).containsIgnoringCase("HttpOnly").containsIgnoringCase("Secure")
                .containsIgnoringCase("SameSite=Lax");
        assertThat(cookies).noneMatch(c -> c.contains("JSESSIONID"));
        assertThat(cookies).as("CSRF token rotated at login").anyMatch(c -> c.contains("XSRF-TOKEN="));
    }

    private String http(String head, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        // Plain loopback to the embedded test server; TLS would add nothing here.
        try (Socket socket = new Socket("127.0.0.1", port)) { // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
            OutputStream out = socket.getOutputStream();
            out.write((head + "Content-Length: " + payload.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
