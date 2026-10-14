package app.sevacenter.domain;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * DNS over HTTPS (ADR 0036). The app box's DNS Firewall answers only allowlisted names (ADR 0032),
 * so a customer's domain can't be looked up through the VPC resolver; only the DoH endpoint is
 * allowlisted. JSON API, short timeouts, answers filtered by record type.
 */
@Component
class DohDnsLookup implements DnsLookup {

    private static final Logger log = LoggerFactory.getLogger(DohDnsLookup.class);
    private static final int A = 1;
    private static final int CNAME = 5;
    private static final int TXT = 16;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI endpoint;
    private final JsonMapper json;

    DohDnsLookup(@Value("${sevacenter.dns.doh-url:https://cloudflare-dns.com/dns-query}") String endpoint, JsonMapper json) {
        URI u = URI.create(endpoint);
        if (!"https".equals(u.getScheme())) {
            throw new IllegalStateException("sevacenter.dns.doh-url must be https");
        }
        this.endpoint = u;
        this.json = json;
    }

    @Override
    public List<String> txt(String name) {
        return query(name, "TXT", TXT).stream().map(DohDnsLookup::unquoteTxt).toList();
    }

    @Override
    public List<String> cname(String name) {
        return query(name, "CNAME", CNAME).stream().map(DohDnsLookup::noDot).toList();
    }

    @Override
    public List<String> a(String name) {
        return query(name, "A", A);
    }

    private List<String> query(String name, String type, int typeCode) {
        URI uri = URI.create(endpoint + "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&type=" + type);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Accept", "application/dns-json").GET().build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return List.of();
            }
            JsonNode answers = json.readTree(resp.body()).path("Answer");
            List<String> out = new ArrayList<>();
            for (JsonNode a : answers) {
                if (a.path("type").asInt() == typeCode) {
                    out.add(a.path("data").asString());
                }
            }
            return out;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("DoH lookup failed: type={} error={}", type, e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** "\"sc-verify-abc\"" or split chunks "\"part1\" \"part2\"" -> sc-verify-abc / part1part2. */
    static String unquoteTxt(String data) {
        StringBuilder sb = new StringBuilder();
        boolean in = false;
        for (char c : data.toCharArray()) {
            if (c == '"') {
                in = !in;
            } else if (in) {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? data.strip() : sb.toString();
    }

    static String noDot(String s) {
        String t = s.strip().toLowerCase(java.util.Locale.ROOT);
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }
}
