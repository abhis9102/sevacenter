package app.sevacenter.payments;

/** Verifies signatures on donation webhooks from our payment relay. */
public final class DonationWebhookConfig {

    /** From the environment only; never in source. The old in-code value is leaked: rotated. */
    public static String signingSecret() {
        String secret = System.getenv("DONATION_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("DONATION_WEBHOOK_SECRET is not set");
        }
        return secret;
    }

    private DonationWebhookConfig() {
    }
}
