package app.sevacenter.temple;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Column(name = "morning_open")
    private LocalTime morningOpen;
    @Column(name = "morning_close")
    private LocalTime morningClose;
    @Column(name = "evening_open")
    private LocalTime eveningOpen;
    @Column(name = "evening_close")
    private LocalTime eveningClose;

    @Column(name = "status_override")
    private String statusOverride;
    @Column(name = "override_on")
    private LocalDate overrideOn;
    @Column(name = "status_note")
    private String statusNote;

    @Column(nullable = false)
    private String calendar = "AMANTA";

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
        touch(staffId, now);
    }

    void schedule(LocalTime morningOpen, LocalTime morningClose, LocalTime eveningOpen, LocalTime eveningClose,
                  String calendar) {
        this.morningOpen = morningOpen;
        this.morningClose = morningClose;
        this.eveningOpen = eveningOpen;
        this.eveningClose = eveningClose;
        this.calendar = calendar;
    }

    /** status null clears it; otherwise it holds for {@code on} only. */
    void override(String status, LocalDate on, String note) {
        this.statusOverride = status;
        this.overrideOn = status == null ? null : on;
        this.statusNote = status == null ? null : note;
    }

    private void touch(long staffId, OffsetDateTime now) {
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public String getDeity() { return deity; }
    public String getAddress() { return address; }
    public String getHelpline() { return helpline; }
    public String getTimings() { return timings; }
    public String getAnnouncement() { return announcement; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public LocalTime getMorningOpen() { return morningOpen; }
    public LocalTime getMorningClose() { return morningClose; }
    public LocalTime getEveningOpen() { return eveningOpen; }
    public LocalTime getEveningClose() { return eveningClose; }
    public String getCalendar() { return calendar; }

    /** The override, only on the day it was set for. */
    public String overrideFor(LocalDate today) {
        return today.equals(overrideOn) ? statusOverride : null;
    }

    public String noteFor(LocalDate today) {
        return today.equals(overrideOn) ? statusNote : null;
    }
}
