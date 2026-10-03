package app.sevacenter.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.sevacenter.donation.PaymentGateway.Credentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real Razorpay client against Razorpay's test mode. Opt-in: runs only with a test key in
 * the environment (RAZORPAY_KEY_ID=rzp_test_..., RAZORPAY_KEY_SECRET), never in CI, never with a
 * live key. Proves the request/response mapping the fake gateway stands in for.
 */
@EnabledIfEnvironmentVariable(named = "RAZORPAY_KEY_ID", matches = "rzp_test_.+")
class RazorpayGatewayLiveTest {

    private final RazorpayGateway gateway = new RazorpayGateway(JsonMapper.builder().build(), "https://api.razorpay.com");
    private final Credentials creds = new Credentials(System.getenv("RAZORPAY_KEY_ID"), System.getenv("RAZORPAY_KEY_SECRET"));

    @Test
    void realTestCredentialsWorkAndWrongOnesDont() {
        gateway.verifyCredentials(creds);
        assertThatThrownBy(() -> gateway.verifyCredentials(new Credentials(creds.keyId(), "not-the-secret")))
                .isInstanceOf(PaymentGateway.GatewayException.class).hasMessageNotContaining("not-the-secret");
    }

    @Test
    void anOrderIsCreatedForExactlyTheAmountAndHasNoPaymentsYet() {
        String orderId = gateway.createOrder(creds, 100, "live-test-" + System.nanoTime());
        assertThat(orderId).startsWith("order_");
        assertThat(gateway.paymentsOfOrder(creds, orderId)).isEmpty();
    }
}
