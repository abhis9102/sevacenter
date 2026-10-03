package app.sevacenter.donation;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A devotee's own donations and receipts, for "my seva" (ADR 0019). Callers pass a contact the
 * devotee has proven they own (ADR 0018); nothing here is reachable without one. Receipts come
 * back with the donor PAN masked: the portal is a convenience copy, the trust holds the original.
 */
@Service
public class DonationHistory {

    private final DonationRepository donations;
    private final ReceiptService receipts;

    public DonationHistory(DonationRepository donations, ReceiptService receipts) {
        this.donations = donations;
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public List<Entry> forContact(String contact) {
        List<Donation> mine = donations.forContact(contact, PageRequest.of(0, 200));
        Set<Long> reversed = mine.isEmpty() ? Set.of()
                : new HashSet<>(donations.reversedAmong(mine.stream().map(Donation::getId).toList()));
        return mine.stream().map(d -> {
            Optional<Receipt> r = receipts.findByDonationId(d.getId());
            boolean receiptValid = r.isPresent() && receipts.cancellation(r.get().getId()).isEmpty();
            return new Entry(d.getId(), d.getReceivedOn(), Money.toRupees(d.getAmountPaise()), d.getMode(),
                    d.getPurpose(), reversed.contains(d.getId()), r.map(Receipt::number).orElse(null), receiptValid);
        }).toList();
    }

    /** The receipt for one of this contact's own donations; empty for anyone else's (same 404). */
    @Transactional(readOnly = true)
    public Optional<ReceiptController.ReceiptResponse> receipt(String contact, long donationId) {
        if (!donations.isFor(contact, donationId)) {
            return Optional.empty();
        }
        return receipts.findByDonationId(donationId).map(r -> ReceiptController.ReceiptResponse.of(r,
                PanProtection.masked(r.getDonorPanLast4()), receipts.cancellation(r.getId()).orElse(null)));
    }

    public record Entry(long id, LocalDate receivedOn, String amount, DonationMode mode, String purpose,
                        boolean reversed, String receiptNumber, boolean receiptValid) { }
}
