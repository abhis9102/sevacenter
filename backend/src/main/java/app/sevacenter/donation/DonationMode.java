package app.sevacenter.donation;

/** How a donation was received (ADR 0011). Stored as text (V6). */
public enum DonationMode {
    CASH,
    UPI,
    CHEQUE,
    BANK_TRANSFER,
    CARD
}
