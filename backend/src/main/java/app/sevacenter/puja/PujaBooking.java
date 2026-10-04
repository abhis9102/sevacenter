package app.sevacenter.puja;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A devotee's puja booking (ADR 0016). Paid bookings become CONFIRMED only with a verified payment
 * (V15 CHECK); the dakshina is seva income and never enters the 80G donation ledger.
 */
@Entity
@Table(name = "puja_booking")
public class PujaBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "puja_id", nullable = false, updatable = false)
    private Long pujaId;

    @Column(name = "booking_code", nullable = false, updatable = false)
    private String bookingCode;

    @Column(name = "puja_name", nullable = false, updatable = false)
    private String pujaName;

    @Column(name = "devotee_name", nullable = false, updatable = false)
    private String devoteeName;

    @Column(updatable = false)
    private String gotra;

    @Column(updatable = false)
    private String nakshatra;

    @Column(updatable = false)
    private String rashi;

    @Column(name = "family_names", updatable = false)
    private String familyNames;

    @Column(name = "puja_date", nullable = false, updatable = false)
    private LocalDate pujaDate;

    @Column(updatable = false)
    private String phone;

    @Column(updatable = false)
    private String email;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(nullable = false)
    private String status;

    @Column(name = "payment_ref")
    private String paymentRef;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "performed_at")
    private OffsetDateTime performedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "priest_id")
    private Long priestId;

    @Column(name = "booked_by", updatable = false)
    private Long bookedBy;

    @Column(name = "counter_mode", updatable = false)
    private String counterMode;

    @Column(name = "counter_reference", updatable = false)
    private String counterReference;

    protected PujaBooking() { }

    PujaBooking(long tenantId, Puja puja, String code, String devoteeName, String gotra, String nakshatra, String rashi,
                String familyNames, LocalDate pujaDate, String phone, String email) {
        this.tenantId = tenantId;
        this.pujaId = puja.getId();
        this.pujaName = puja.getName();
        this.amountPaise = puja.getDakshinaPaise();
        this.bookingCode = code;
        this.devoteeName = devoteeName;
        this.gotra = gotra;
        this.nakshatra = nakshatra;
        this.rashi = rashi;
        this.familyNames = familyNames;
        this.pujaDate = pujaDate;
        this.phone = phone;
        this.email = email;
        this.status = amountPaise == 0 ? "CONFIRMED" : "AWAITING_PAYMENT";
    }

    /** Booked by staff at the counter (ADR 0026): confirmed at once, the dakshina taken in person. */
    void bookedAtCounter(long staffId, String mode, String reference) {
        this.bookedBy = staffId;
        this.status = "CONFIRMED";
        if (getAmountPaise() > 0) {
            this.counterMode = mode;
            this.counterReference = reference;
            this.paymentRef = "COUNTER-" + bookingCode;
        }
    }

    void assignPriest(Long priestId) {
        this.priestId = priestId;
    }

    void confirmPaid(String paymentRef) {
        this.status = "CONFIRMED";
        this.paymentRef = paymentRef;
    }

    void perform(long staffId, OffsetDateTime now) {
        this.status = "PERFORMED";
        this.performedBy = staffId;
        this.performedAt = now;
    }

    void cancel() {
        this.status = "CANCELLED";
    }

    public Long getId() { return id; }
    public String getBookingCode() { return bookingCode; }
    public String getPujaName() { return pujaName; }
    public String getDevoteeName() { return devoteeName; }
    public String getGotra() { return gotra; }
    public String getNakshatra() { return nakshatra; }
    public String getRashi() { return rashi; }
    public String getFamilyNames() { return familyNames; }
    public LocalDate getPujaDate() { return pujaDate; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public long getAmountPaise() { return amountPaise; }
    public String getStatus() { return status; }
    public String getPaymentRef() { return paymentRef; }
    public Long getBookedBy() { return bookedBy; }
    public Long getPriestId() { return priestId; }
    public String getCounterMode() { return counterMode; }
    public OffsetDateTime getPerformedAt() { return performedAt; }
}
