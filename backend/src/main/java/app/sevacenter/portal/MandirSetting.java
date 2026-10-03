package app.sevacenter.portal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Customizable mandir schedule, darshan hours, sacred panchang, and announcement settings.
 * Managed by temple trustees/staff in SevaCenter to display in Mandir Center.
 */
@Entity
@Table(name = "mandir_setting")
public class MandirSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    @Column(name = "morning_hours", nullable = false)
    private String morningHours;

    @Column(name = "evening_hours", nullable = false)
    private String eveningHours;

    @Column(name = "is_open_override")
    private Boolean isOpenOverride;

    @Column(name = "deity")
    private String deity;

    @Column(name = "address")
    private String address;

    @Column(name = "helpline")
    private String helpline;

    @Column(name = "panchang_tithi")
    private String panchangTithi;

    @Column(name = "nakshatra")
    private String nakshatra;

    @Column(name = "special_announcement")
    private String specialAnnouncement;

    @Column(name = "aartis_json")
    private String aartisJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    protected MandirSetting() { }

    public MandirSetting(Long tenantId, String morningHours, String eveningHours, String deity,
                         String address, String helpline, String panchangTithi, String nakshatra,
                         String specialAnnouncement, String aartisJson) {
        this.tenantId = tenantId;
        this.morningHours = morningHours != null ? morningHours : "05:00 AM – 01:00 PM";
        this.eveningHours = eveningHours != null ? eveningHours : "04:00 PM – 09:30 PM";
        this.deity = deity != null ? deity : "Pradhan Devata";
        this.address = address != null ? address : "Temple Road, Central Sanctum";
        this.helpline = helpline != null ? helpline : "+91 98765 43210";
        this.panchangTithi = panchangTithi != null ? panchangTithi : "Shukla Paksha Ekadashi";
        this.nakshatra = nakshatra != null ? nakshatra : "Pushya Nakshatra";
        this.specialAnnouncement = specialAnnouncement;
        this.aartisJson = aartisJson;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getMorningHours() { return morningHours; }
    public String getEveningHours() { return eveningHours; }
    public Boolean getIsOpenOverride() { return isOpenOverride; }
    public String getDeity() { return deity; }
    public String getAddress() { return address; }
    public String getHelpline() { return helpline; }
    public String getPanchangTithi() { return panchangTithi; }
    public String getNakshatra() { return nakshatra; }
    public String getSpecialAnnouncement() { return specialAnnouncement; }
    public String getAartisJson() { return aartisJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String morningHours, String eveningHours, Boolean isOpenOverride, String deity,
                       String address, String helpline, String panchangTithi, String nakshatra,
                       String specialAnnouncement, String aartisJson) {
        if (morningHours != null && !morningHours.isBlank()) this.morningHours = morningHours;
        if (eveningHours != null && !eveningHours.isBlank()) this.eveningHours = eveningHours;
        this.isOpenOverride = isOpenOverride;
        if (deity != null) this.deity = deity;
        if (address != null) this.address = address;
        if (helpline != null) this.helpline = helpline;
        if (panchangTithi != null) this.panchangTithi = panchangTithi;
        if (nakshatra != null) this.nakshatra = nakshatra;
        this.specialAnnouncement = specialAnnouncement;
        if (aartisJson != null) this.aartisJson = aartisJson;
        this.updatedAt = Instant.now();
    }
}
