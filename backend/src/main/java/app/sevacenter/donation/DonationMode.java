package app.sevacenter.donation;

/** How a donation was received (ADR 0011). Stored as text (V6). */
public enum DonationMode {
    CASH,
    UPI,
    CHEQUE,
    BANK_TRANSFER,
    CARD,
    /** Wallets and other online methods, as Razorpay reports them (ADR 0013). */
    WALLET
}
