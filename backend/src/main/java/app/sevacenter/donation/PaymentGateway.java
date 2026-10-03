package app.sevacenter.donation;

import java.util.List;

/**
 * The calls we make to a trust's payment gateway (ADR 0013). An interface so tests use a fake
 * and never touch the network; {@link RazorpayGateway} is the real one.
 */
public interface PaymentGateway {

    /** Throws {@link GatewayException} unless the credentials work. */
    void verifyCredentials(Credentials credentials);

    /** Creates an order for exactly this amount; returns the gateway's order id. */
    String createOrder(Credentials credentials, long amountPaise, String receipt);

    Payment fetchPayment(Credentials credentials, String paymentId);

    List<Payment> paymentsOfOrder(Credentials credentials, String orderId);

    record Credentials(String keyId, String keySecret) {
        @Override
        public String toString() {
            return "Credentials[keyId=" + keyId + ", keySecret=***]"; // never log the secret
        }
    }

    /** What the gateway says about a payment; amounts in paise. */
    record Payment(String id, String orderId, String status, long amountPaise, String currency, String method) { }

    class GatewayException extends RuntimeException {
        public GatewayException(String message) {
            super(message);
        }
    }
}
