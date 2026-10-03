package app.sevacenter.donation;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay's REST API (https://razorpay.com/docs/api/), called with the trust's own credentials.
 * Plain JDK HTTP client: no SDK dependency to track. Errors never include the secret.
 */
@Component
public class RazorpayGateway implements PaymentGateway {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json;
    private final String baseUrl;

    public RazorpayGateway(JsonMapper json, @Value("${sevacenter.razorpay.base-url:https://api.razorpay.com}") String baseUrl) {
        this.json = json;
        this.baseUrl = baseUrl;
    }

    @Override
    public void verifyCredentials(Credentials c) {
        call(c, HttpRequest.newBuilder(uri("/v1/orders?count=1")).GET());
    }

    @Override
    public String createOrder(Credentials c, long amountPaise, String receipt) {
        String body = json.writeValueAsString(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt));
        JsonNode order = call(c, HttpRequest.newBuilder(uri("/v1/orders"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)));
        if (order.at("/amount").asLong() != amountPaise) {
            throw new GatewayException("gateway created an order for a different amount");
        }
        return order.at("/id").asString();
    }

    @Override
    public Payment fetchPayment(Credentials c, String paymentId) {
        return payment(call(c, HttpRequest.newBuilder(uri("/v1/payments/" + encode(paymentId))).GET()));
    }

    @Override
    public List<Payment> paymentsOfOrder(Credentials c, String orderId) {
        JsonNode list = call(c, HttpRequest.newBuilder(uri("/v1/orders/" + encode(orderId) + "/payments")).GET());
        List<Payment> out = new ArrayList<>();
        for (JsonNode p : list.at("/items")) {
            out.add(payment(p));
        }
        return out;
    }

    private static Payment payment(JsonNode p) {
        return new Payment(p.at("/id").asString(), p.at("/order_id").asString(), p.at("/status").asString(),
                p.at("/amount").asLong(), p.at("/currency").asString(), p.at("/method").asString());
    }

    private JsonNode call(Credentials c, HttpRequest.Builder request) {
        String basic = Base64.getEncoder().encodeToString((c.keyId() + ":" + c.keySecret()).getBytes(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = http.send(request.header("Authorization", "Basic " + basic)
                    .header("Accept", "application/json").timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new GatewayException("gateway answered HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new GatewayException("gateway unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("interrupted");
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private static String encode(String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8);
    }
}
