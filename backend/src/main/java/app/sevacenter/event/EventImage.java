package app.sevacenter.event;

import java.time.OffsetDateTime;

import app.sevacenter.temple.TempleImageProcessor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An event's cover photo: TempleImageProcessor output only. RLS. */
@Entity
@Table(name = "event_image")
public class EventImage {

    @Id
    @Column(name = "event_id")
    private Long eventId;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false)
    private byte[] data;
    @Column(nullable = false)
    private int width;
    @Column(nullable = false)
    private int height;
    @Column(nullable = false)
    private String version;
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;
    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    protected EventImage() { }

    EventImage(long eventId, long tenantId) {
        this.eventId = eventId;
        this.tenantId = tenantId;
    }

    void replace(TempleImageProcessor.Processed image, long staffId, OffsetDateTime now) {
        this.data = image.jpeg();
        this.width = image.width();
        this.height = image.height();
        this.version = image.version();
        this.uploadedBy = staffId;
        this.uploadedAt = now;
    }

    public byte[] getData() { return data; }
    public String getVersion() { return version; }
}
