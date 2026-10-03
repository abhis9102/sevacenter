package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Error responses must not leak internals (messages, exception class, stack trace).
 *
 * <p>Runs against a real embedded server: MockMvc never forwards to Spring's /error page, so
 * a MockMvc version of this test passed even with stack traces switched on. Also guards the
 * Boot 4 rename of server.error.* to spring.web.error.* (the old keys are silently ignored).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDisclosureTest {

    private static final Pattern TOKEN = Pattern.compile("XSRF-TOKEN=([^;]+)");

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void malformedRequestErrorLeaksNoInternals() throws Exception {
        HttpResponse<String> csrf = http.send(
                HttpRequest.newBuilder(uri("/api/v1/csrf")).build(), HttpResponse.BodyHandlers.ofString());
        Matcher cookie = TOKEN.matcher(csrf.headers().firstValue("Set-Cookie").orElseThrow());
        assertThat(cookie.find()).isTrue();
        String token = cookie.group(1);

        HttpResponse<String> error = http.send(HttpRequest.newBuilder(uri("/api/v1/register"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + token)
                        .header("X-XSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.ofString("{not json"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(error.statusCode()).isEqualTo(400);
        assertThat(error.body())
                .contains("\"status\":400")
                .doesNotContain("\"trace\"", "\"exception\"", "\"message\"", "\"errors\"",
                        "at org.", "Exception");
    }

    @Test
    void browsersGetJsonNotAFrameworkFingerprintPage() throws Exception {
        HttpResponse<String> error = http.send(HttpRequest.newBuilder(uri("/api/v1/does-not-exist"))
                        .header("Accept", "text/html,application/xhtml+xml").build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(error.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(error.body()).doesNotContain("Whitelabel", "<html");
    }

    @Test
    void errorEndpointCalledDirectlyIsNotFound() throws Exception {
        HttpResponse<String> direct = http.send(HttpRequest.newBuilder(uri("/error")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(direct.statusCode()).isEqualTo(404);
        assertThat(direct.body()).doesNotContain("999", "None");
    }

    /** Tomcat 11 throws on malformed parameters ("?=x"); found by DAST as 500s. */
    @Test
    void malformedQueryParametersAreABadRequest() throws Exception {
        for (String query : new String[] {"?=x", "?q=%00", "?q=a&=b"}) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/devotees" + query)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(response.body()).as(query).doesNotContain("Exception", "at org.");
        }
    }

    /**
     * Uploads over the 2 MB limit are refused by the container with a clean 4xx. The sanity filter
     * reads parameters (which parses multipart) before authentication, so this holds even for
     * anonymous requests, and the parse is bounded by the same limit.
     */
    @Test
    void oversizedUploadsAreRefusedCleanly() throws Exception {
        String boundary = "sevacenter-test";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"d.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n" + "x".repeat(3 * 1024 * 1024) + "\r\n--" + boundary + "--\r\n";
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/devotees/import"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(400, 499);
        assertThat(response.body()).doesNotContain("Exception", "at org.");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
