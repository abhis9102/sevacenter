package app.sevacenter.donation;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A donor's attempt to pay, created with the server-side amount before Checkout opens. The
 * amount a payment must match always comes from here, never from the browser (ADR 0013).
 */
@Entity
@Table(name = "payment_intent")
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "razorpay_order_id", nullable = false, updatable = false)
    private String razorpayOrderId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "donor_name", nullable = false, updatable = false)
    private String donorName;

    @Column(updatable = false)
    private String purpose;

    /** Optional contact a donor left so "my seva" can find this donation (ADR 0019). */
    @Column(name = "donor_phone", updatable = false)
    private String donorPhone;

    @Column(name = "donor_email", updatable = false)
    private String donorEmail;

    @Column(nullable = false)
    private String status = "CREATED";

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "donation_id")
    private Long donationId;

    /** DONATION settles into the ledger; PUJA confirms a puja booking (seva income, not 80G). */
    @Column(nullable = false, updatable = false)
    private String kind = "DONATION";

    @Column(name = "puja_booking_id", updatable = false)
    private Long pujaBookingId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    protected PaymentIntent() { }

    PaymentIntent(long tenantId, String orderId, long amountPaise, String donorName, String purpose) {
        this.tenantId = tenantId;
        this.razorpayOrderId = orderId;
        this.amountPaise = amountPaise;
        this.donorName = donorName;
        this.purpose = purpose;
    }

    PaymentIntent withContact(String phone, String email) {
        this.donorPhone = phone;
        this.donorEmail = email;
        return this;
    }

    static PaymentIntent forPuja(long tenantId, String orderId, long amountPaise, String name, String purpose,
                                 long bookingId) {
        PaymentIntent i = new PaymentIntent(tenantId, orderId, amountPaise, name, purpose);
        i.kind = "PUJA";
        i.pujaBookingId = bookingId;
        return i;
    }

    void markPaid(String paymentId, Long donationId, OffsetDateTime now) {
        this.status = "PAID";
        this.razorpayPaymentId = paymentId;
        this.donationId = donationId;
        this.paidAt = now;
    }

    boolean isPaid() { return "PAID".equals(status); }

    boolean isPuja() { return "PUJA".equals(kind); }

    public Long getPujaBookingId() { return pujaBookingId; }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public long getAmountPaise() { return amountPaise; }
    public String getDonorName() { return donorName; }
    public String getPurpose() { return purpose; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public Long getDonationId() { return donationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
