package app.sevacenter.temple;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The temple's photo on its public page: only what TempleImageProcessor produced. RLS. */
@Entity
@Table(name = "temple_image")
public class TempleImage {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** Changes with the image: cache-busting query parameter and ETag. */
    @Column(nullable = false)
    private String version;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TempleImage() { }

    TempleImage(long tenantId) {
        this.tenantId = tenantId;
    }

    void replace(TempleImageProcessor.Processed image, long staffId, OffsetDateTime now) {
        this.data = image.jpeg();
        this.contentType = "image/jpeg";
        this.width = image.width();
        this.height = image.height();
        this.version = image.version();
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public byte[] getData() { return data; }
    public String getVersion() { return version; }
}
