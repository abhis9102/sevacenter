package app.sevacenter.temple;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A photo in the About or History section of the About page: TempleImageProcessor output only. RLS. */
@Entity
@Table(name = "temple_gallery_image")
public class GalleryImage {

    public enum Section { ABOUT, HISTORY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false)
    private String section;
    private String caption;
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

    protected GalleryImage() { }

    GalleryImage(long tenantId, Section section, String caption, TempleImageProcessor.Processed image, long staffId,
                 OffsetDateTime now) {
        this.tenantId = tenantId;
        this.section = section.name();
        this.caption = caption;
        this.data = image.jpeg();
        this.width = image.width();
        this.height = image.height();
        this.version = image.version();
        this.uploadedBy = staffId;
        this.uploadedAt = now;
    }

    void caption(String caption) {
        this.caption = caption;
    }

    public Long getId() { return id; }
    public Section getSection() { return Section.valueOf(section); }
    public String getCaption() { return caption; }
    public byte[] getData() { return data; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getVersion() { return version; }
}
