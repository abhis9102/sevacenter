package app.sevacenter.temple;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.donation.DonationService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photos for the About page's About and History sections. Upload: LEADER+, re-encoded by
 * TempleImageProcessor (only its output is stored), at most {@value #MAX_PER_SECTION} per section.
 * Public read on the trust's own host, by versioned URL.
 */
@RestController
public class GalleryController {

    static final int MAX_PER_SECTION = 12;

    private final GalleryImageRepository gallery;
    private final TempleImageProcessor processor;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.system(DonationService.IST);

    public GalleryController(GalleryImageRepository gallery, TempleImageProcessor processor, AuditTrail auditTrail) {
        this.gallery = gallery;
        this.processor = processor;
        this.auditTrail = auditTrail;
    }

    @PostMapping(value = "/api/v1/temple/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Photo upload(@RequestParam("image") MultipartFile file, @RequestParam("section") GalleryImage.Section section,
                        @RequestParam(name = "caption", required = false) String caption,
                        @AuthenticationPrincipal StaffUser staff) throws IOException {
        if (gallery.countBySection(section.name()) >= MAX_PER_SECTION) {
            throw new InvalidFieldException("image", "At most " + MAX_PER_SECTION + " photos per section");
        }
        TempleImageProcessor.Processed processed = processor.process(file.getBytes());
        GalleryImage g = gallery.save(new GalleryImage(TenantContext.get(), section, caption(caption), processed,
                staff.userId(), OffsetDateTime.now(clock)));
        auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_gallery_image", g.getId(), "photo added");
        return Photo.of(g);
    }

    @PutMapping("/api/v1/temple/gallery/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    @Transactional
    public Photo recaption(@PathVariable long id, @Valid @RequestBody CaptionRequest r) {
        GalleryImage g = gallery.findById(id).orElseThrow(PhotoNotFoundException::new);
        g.caption(caption(r.caption()));
        auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_gallery_image", id, "caption");
        return Photo.of(g);
    }

    @DeleteMapping("/api/v1/temple/gallery/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove(@PathVariable long id) {
        gallery.findById(id).ifPresent(g -> {
            gallery.delete(g);
            auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_gallery_image", id, "photo removed");
        });
    }

    /** Staff and public alike: the list (no bytes). */
    @GetMapping("/api/v1/public/temple/gallery")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Photo>> list() {
        if (TenantContext.get() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gallery.summaries().stream().map(Photo::of).toList());
    }

    /** Our own JPEG; long-cached only at the versioned URL the page uses. */
    @GetMapping("/api/v1/public/temple/gallery/{id:\\d+}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> image(@PathVariable long id, @RequestParam(name = "v", required = false) String v) {
        if (TenantContext.get() == null) {
            return ResponseEntity.notFound().build();
        }
        return gallery.findById(id).map(g -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(g.getVersion().equals(v)
                                ? CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable()
                                : CacheControl.noCache())
                        .eTag(g.getVersion())
                        .header("Content-Disposition", "inline")
                        .body(g.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String caption(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String c = raw.strip();
        if (c.length() > 200 || c.indexOf('\0') >= 0) {
            throw new InvalidFieldException("caption", "caption must be at most 200 characters");
        }
        return c;
    }

    public record CaptionRequest(@Schema(example = "The garbhagriha after the 2019 renovation") @Size(max = 200) String caption) { }

    public record Photo(long id, GalleryImage.Section section, String caption, int width, int height, String version) {
        static Photo of(GalleryImage g) {
            return new Photo(g.getId(), g.getSection(), g.getCaption(), g.getWidth(), g.getHeight(), g.getVersion());
        }

        static Photo of(GalleryImageRepository.Summary s) {
            return new Photo(s.getId(), GalleryImage.Section.valueOf(s.getSection()), s.getCaption(), s.getWidth(),
                    s.getHeight(), s.getVersion());
        }
    }

    /** 404: no such photo on this trust. */
    public static class PhotoNotFoundException extends RuntimeException { }
}
