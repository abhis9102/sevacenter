package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.sevacenter.auth.LoginThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * The login throttle sees the real client behind the load balancer (found while connecting the
 * frontend: behind a proxy every login came from the proxy's IP, so anyone could lock out
 * everyone). Real server: only Tomcat's RemoteIpValve resolves X-Forwarded-For, and only from
 * a trusted proxy (here: loopback, like a local proxy).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientIpTest {

    private static final Pattern TOKEN = Pattern.compile("XSRF-TOKEN=([^;]+)");

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void eachForwardedClientIsThrottledOnItsOwn() throws Exception {
        String attacker = "203.0.113.7";
        for (int i = 0; i < LoginThrottle.MAX_IP_FAILURES; i++) {
            assertThat(login(attacker, "spray" + i + "@example.org")).isEqualTo(401);
        }
        assertThat(login(attacker, "one-more@example.org")).as("the sprayer is locked").isEqualTo(429);
        assertThat(login("198.51.100.9", "someone@example.org")).as("other clients are not").isEqualTo(401);
    }

    /** The rightmost hop the trusted proxy appended is the client: a forged left part changes nothing. */
    @Test
    void aClientCantDodgeTheThrottleByForgingTheHeader() throws Exception {
        String attacker = "203.0.113.8";
        for (int i = 0; i < LoginThrottle.MAX_IP_FAILURES; i++) {
            login("10.0.0." + (i % 250) + ", " + attacker, "forge" + i + "@example.org");
        }
        assertThat(login("192.0.2.1, " + attacker, "again@example.org")).isEqualTo(429);
    }

    private int login(String forwardedFor, String email) throws Exception {
        HttpResponse<String> csrf = http.send(HttpRequest.newBuilder(uri("/api/v1/csrf")).build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = TOKEN.matcher(csrf.headers().firstValue("Set-Cookie").orElseThrow());
        assertThat(m.find()).isTrue();
        String token = m.group(1);
        return http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", "XSRF-TOKEN=" + token)
                        .header("X-XSRF-TOKEN", token)
                        .header("X-Forwarded-For", forwardedFor)
                        .POST(HttpRequest.BodyPublishers.ofString("email=" + email + "&password=wrong-password-1"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
