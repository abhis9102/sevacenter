package app.sevacenter.portal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Puja and Sankalpam booking for devotees at this mandir.
 */
@Entity
@Table(name = "puja_booking")
public class PujaBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "devotee_id")
    private Long devoteeId;

    @Column(name = "booking_number", nullable = false)
    private String bookingNumber;

    @Column(name = "puja_code", nullable = false)
    private String pujaCode;

    @Column(name = "puja_name", nullable = false)
    private String pujaName;

    @Column(name = "puja_date", nullable = false)
    private LocalDate pujaDate;

    @Column(name = "time_slot", nullable = false)
    private String timeSlot;

    @Column(name = "devotee_name", nullable = false)
    private String devoteeName;

    @Column(name = "gotra")
    private String gotra;

    @Column(name = "nakshatra")
    private String nakshatra;

    @Column(name = "rashi")
    private String rashi;

    @Column(name = "family_members")
    private String familyMembers;

    @Column(name = "contact")
    private String contact;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PujaBooking() { }

    public PujaBooking(Long tenantId, Long devoteeId, String bookingNumber, String pujaCode, String pujaName,
                       LocalDate pujaDate, String timeSlot, String devoteeName, String gotra, String nakshatra,
                       String rashi, String familyMembers, String contact, Long amountPaise, String paymentMode) {
        this.tenantId = tenantId;
        this.devoteeId = devoteeId;
        this.bookingNumber = bookingNumber;
        this.pujaCode = pujaCode;
        this.pujaName = pujaName;
        this.pujaDate = pujaDate;
        this.timeSlot = timeSlot;
        this.devoteeName = devoteeName;
        this.gotra = gotra;
        this.nakshatra = nakshatra;
        this.rashi = rashi;
        this.familyMembers = familyMembers;
        this.contact = contact;
        this.amountPaise = amountPaise != null ? amountPaise : 0L;
        this.paymentMode = paymentMode;
        this.status = "CONFIRMED";
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getDevoteeId() {
        return devoteeId;
    }

    public String getBookingNumber() {
        return bookingNumber;
    }

    public String getPujaCode() {
        return pujaCode;
    }

    public String getPujaName() {
        return pujaName;
    }

    public LocalDate getPujaDate() {
        return pujaDate;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public String getDevoteeName() {
        return devoteeName;
    }

    public String getGotra() {
        return gotra;
    }

    public String getNakshatra() {
        return nakshatra;
    }

    public String getRashi() {
        return rashi;
    }

    public String getFamilyMembers() {
        return familyMembers;
    }

    public String getContact() {
        return contact;
    }

    public Long getAmountPaise() {
        return amountPaise;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
