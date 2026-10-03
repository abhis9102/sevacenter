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
 * An issued 80G receipt (ADR 0012). Immutable: the trust and donor details are snapshots taken at
 * issue time, and the app role can only insert. The donor PAN is stored only as ciphertext,
 * last 4 characters and a blind index.
 */
@Entity
@Immutable
@Table(name = "receipt")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "donation_id", nullable = false)
    private Long donationId;

    @Column(name = "fy_start", nullable = false)
    private int fyStart;

    @Column(nullable = false)
    private int seq;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "donor_name", nullable = false)
    private String donorName;

    @Column(name = "donor_address", nullable = false)
    private String donorAddress;

    @Column(name = "donor_pan_enc", nullable = false)
    private String donorPanEnc;

    @Column(name = "donor_pan_last4", nullable = false)
    private String donorPanLast4;

    @Column(name = "donor_pan_index", nullable = false)
    private String donorPanIndex;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationMode mode;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(name = "trust_legal_name", nullable = false)
    private String trustLegalName;

    @Column(name = "trust_address", nullable = false)
    private String trustAddress;

    @Column(name = "trust_pan", nullable = false)
    private String trustPan;

    @Column(name = "trust_registration_80g", nullable = false)
    private String trustRegistration80g;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Receipt() { }

    static Receipt issue(long tenantId, Donation donation, int fyStart, int seq, LocalDate issuedOn, String donorAddress,
                         String panEnc, String panLast4, String panIndex, TrustProfile trust, long staffId) {
        Receipt r = new Receipt();
        r.tenantId = tenantId;
        r.donationId = donation.getId();
        r.fyStart = fyStart;
        r.seq = seq;
        r.issuedOn = issuedOn;
        r.donorName = donation.getDonorName();
        r.donorAddress = donorAddress;
        r.donorPanEnc = panEnc;
        r.donorPanLast4 = panLast4;
        r.donorPanIndex = panIndex;
        r.amountPaise = donation.getAmountPaise();
        r.mode = donation.getMode();
        r.receivedOn = donation.getReceivedOn();
        r.trustLegalName = trust.getLegalName();
        r.trustAddress = trust.getAddress();
        r.trustPan = trust.getPan();
        r.trustRegistration80g = trust.getRegistration80g();
        r.issuedBy = staffId;
        return r;
    }

    /** "2026-27/000123" */
    public String number() {
        return new DonationService.FinancialYear(fyStart).label() + "/" + String.format("%06d", seq);
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getDonationId() { return donationId; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public String getDonorName() { return donorName; }
    public String getDonorAddress() { return donorAddress; }
    public String getDonorPanEnc() { return donorPanEnc; }
    public String getDonorPanLast4() { return donorPanLast4; }
    public long getAmountPaise() { return amountPaise; }
    public DonationMode getMode() { return mode; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public String getTrustLegalName() { return trustLegalName; }
    public String getTrustAddress() { return trustAddress; }
    public String getTrustPan() { return trustPan; }
    public String getTrustRegistration80g() { return trustRegistration80g; }
    public Long getIssuedBy() { return issuedBy; }
}
