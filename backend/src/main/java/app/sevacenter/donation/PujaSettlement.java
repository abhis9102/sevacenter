package app.sevacenter.donation;

/**
 * Confirms a puja booking once its payment is verified (ADR 0016). Owned here, implemented by the
 * puja module, so the payment code never depends on it and puja fees never touch the 80G ledger.
 */
public interface PujaSettlement {

    /** Marks the booking paid and confirmed; returns its booking code. */
    String confirmPaid(long bookingId, String paymentRef);

    String bookingCode(long bookingId);
}
