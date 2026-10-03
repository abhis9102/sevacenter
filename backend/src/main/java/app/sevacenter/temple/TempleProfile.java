package app.sevacenter.temple;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** What the trust's public page says about the temple (ADR 0017). One row per tenant; RLS. */
@Entity
@Table(name = "temple_profile")
public class TempleProfile {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    private String deity;
    private String address;
    private String helpline;
    private String timings;
    private String announcement;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TempleProfile() { }

    TempleProfile(long tenantId) {
        this.tenantId = tenantId;
    }

    void edit(String deity, String address, String helpline, String timings, String announcement, long staffId,
              OffsetDateTime now) {
        this.deity = deity;
        this.address = address;
        this.helpline = helpline;
        this.timings = timings;
        this.announcement = announcement;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public String getDeity() { return deity; }
    public String getAddress() { return address; }
    public String getHelpline() { return helpline; }
    public String getTimings() { return timings; }
    public String getAnnouncement() { return announcement; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
