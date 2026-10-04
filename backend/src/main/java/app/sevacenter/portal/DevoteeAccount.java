package app.sevacenter.portal;

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

/** A contact a devotee proved they own by entering a code sent to it (ADR 0018). RLS-scoped. */
@Entity
@Table(name = "devotee_account")
public class DevoteeAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OtpChannel channel;

    @Column(nullable = false, updatable = false)
    private String contact;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private OffsetDateTime verifiedAt;

    @Column(name = "last_login_at", nullable = false)
    private OffsetDateTime lastLoginAt;

    /** Set when this account was folded into another one (ADR 0025); sessions follow it. */
    @Column(name = "merged_into")
    private Long mergedInto;

    @Column(name = "full_name")
    private String fullName;
    private String gotra;
    private String nakshatra;
    private String rashi;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "family_names")
    private String familyNames;
    @Column(name = "address_line")
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    @Column(name = "profile_updated_at")
    private OffsetDateTime profileUpdatedAt;

    protected DevoteeAccount() { }

    DevoteeAccount(long tenantId, OtpChannel channel, String contact, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.channel = channel;
        this.contact = contact;
        this.verifiedAt = now;
        this.lastLoginAt = now;
    }

    void loggedIn(OffsetDateTime now) {
        lastLoginAt = now;
    }

    void mergeInto(long accountId) {
        this.mergedInto = accountId;
    }

    /** Fills only what this account hasn't got, so a merge never overwrites the devotee's own words. */
    void adoptProfileGaps(DevoteeAccount other) {
        if (fullName == null) fullName = other.fullName;
        if (gotra == null) gotra = other.gotra;
        if (nakshatra == null) nakshatra = other.nakshatra;
        if (rashi == null) rashi = other.rashi;
        if (dateOfBirth == null) dateOfBirth = other.dateOfBirth;
        if (familyNames == null) familyNames = other.familyNames;
        if (addressLine == null) addressLine = other.addressLine;
        if (city == null) city = other.city;
        if (state == null) state = other.state;
        if (pincode == null) pincode = other.pincode;
    }

    void editProfile(String fullName, String gotra, String nakshatra, String rashi, LocalDate dateOfBirth,
                     String familyNames, String addressLine, String city, String state, String pincode,
                     OffsetDateTime now) {
        this.fullName = fullName;
        this.gotra = gotra;
        this.nakshatra = nakshatra;
        this.rashi = rashi;
        this.dateOfBirth = dateOfBirth;
        this.familyNames = familyNames;
        this.addressLine = addressLine;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.profileUpdatedAt = now;
    }

    public Long getId() { return id; }
    public Long getMergedInto() { return mergedInto; }
    Long getTenantId() { return tenantId; }
    public String getFullName() { return fullName; }
    public String getGotra() { return gotra; }
    public String getNakshatra() { return nakshatra; }
    public String getRashi() { return rashi; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getFamilyNames() { return familyNames; }
    public String getAddressLine() { return addressLine; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPincode() { return pincode; }
    public OtpChannel getChannel() { return channel; }
    public String getContact() { return contact; }
}
