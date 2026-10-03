package app.sevacenter.donation;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * One ledger entry (ADR 0011): a donation (positive paise) or the reversal of one (negative,
 * pointing at it). Immutable twice over: Hibernate never issues an UPDATE for it, and the app's
 * database role has no UPDATE or DELETE grant on the table.
 */
@Entity
@Immutable
@Table(name = "donation")
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "devotee_id")
    private Long devoteeId;

    @Column(name = "donor_name", nullable = false)
    private String donorName;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationMode mode;

    private String reference;
    private String purpose;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(name = "reverses_id")
    private Long reversesId;

    @Column(name = "reversal_reason")
    private String reversalReason;

    /** The staff member who recorded it; null only for ONLINE entries (V12 CHECK). */
    @Column(name = "recorded_by")
    private Long recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationChannel channel = DonationChannel.STAFF;

    /** The gateway payment id of an ONLINE entry (unique: a payment is never counted twice). */
    @Column(name = "payment_ref")
    private String paymentRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Donation() { }

    static Donation received(long tenantId, Long devoteeId, String donorName, long amountPaise, DonationMode mode,
                             String reference, String purpose, LocalDate receivedOn, long recordedBy) {
        Donation d = new Donation();
        d.tenantId = tenantId;
        d.devoteeId = devoteeId;
        d.donorName = donorName;
        d.amountPaise = amountPaise;
        d.mode = mode;
        d.reference = reference;
        d.purpose = purpose;
        d.receivedOn = receivedOn;
        d.recordedBy = recordedBy;
        return d;
    }

    /** A donation paid online and verified with the trust's gateway (ADR 0013). */
    static Donation online(long tenantId, String donorName, long amountPaise, DonationMode mode, String purpose,
                           LocalDate receivedOn, String paymentRef) {
        Donation d = new Donation();
        d.tenantId = tenantId;
        d.donorName = donorName;
        d.amountPaise = amountPaise;
        d.mode = mode;
        d.reference = paymentRef;
        d.purpose = purpose;
        d.receivedOn = receivedOn;
        d.channel = DonationChannel.ONLINE;
        d.paymentRef = paymentRef;
        return d;
    }

    /** The reversing entry for this donation: same donor and mode, negated amount. */
    Donation reversal(String reason, LocalDate on, long recordedBy) {
        Donation r = received(tenantId, devoteeId, donorName, -amountPaise, mode, reference, purpose, on, recordedBy);
        r.reversesId = id;
        r.reversalReason = reason;
        return r;
    }

    public boolean isReversal() { return reversesId != null; }

    public Long getId() { return id; }
    public Long getDevoteeId() { return devoteeId; }
    public String getDonorName() { return donorName; }
    public long getAmountPaise() { return amountPaise; }
    public DonationMode getMode() { return mode; }
    public String getReference() { return reference; }
    public String getPurpose() { return purpose; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public Long getReversesId() { return reversesId; }
    public String getReversalReason() { return reversalReason; }
    public Long getRecordedBy() { return recordedBy; }
    public DonationChannel getChannel() { return channel; }
    public String getPaymentRef() { return paymentRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
