package app.sevacenter.portal;

/**
 * Delivers a login code over one channel (ADR 0018). Implementations never log the code. A sender
 * that isn't configured reports {@link #available()} false, and its channel is offered to no one.
 */
public interface OtpSender {

    OtpChannel channel();

    boolean available();

    /** {@code to} is a normalised email or E.164 phone; throws if the provider refused it. */
    void send(String to, String code, String trustName);
}
