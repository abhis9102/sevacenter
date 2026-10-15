package app.sevacenter.event;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.temple.TempleImageProcessor;
import app.sevacenter.tenant.TenantContext;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * An event's cover photo. Upload: LEADER+, re-encoded by TempleImageProcessor. Public read only
 * once the event is published, so a draft's photo never shows before the event does.
 */
@RestController
public class EventImageController {

    private final EventImageRepository images;
    private final EventService events;
    private final TempleImageProcessor processor;
    private final AuditTrail auditTrail;

    public EventImageController(EventImageRepository images, EventService events, TempleImageProcessor processor,
                                AuditTrail auditTrail) {
        this.images = images;
        this.events = events;
        this.processor = processor;
        this.auditTrail = auditTrail;
    }

    @PutMapping(value = "/api/v1/events/{id:\\d+}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LEADER')")
    @Transactional
    public Map<String, String> upload(@PathVariable long id, @RequestParam("image") MultipartFile file,
                                      @AuthenticationPrincipal StaffUser staff) throws IOException {
        events.get(id); // 404 for another trust's or a missing event
        TempleImageProcessor.Processed processed = processor.process(file.getBytes());
        EventImage image = images.findById(id).orElseGet(() -> new EventImage(id, TenantContext.get()));
        image.replace(processed, staff.userId(), OffsetDateTime.now());
        images.save(image);
        auditTrail.record(AuditAction.EVENT_IMAGE_SAVED, "event", id, "photo " + processed.version());
        return Map.of("imageVersion", processed.version());
    }

    @DeleteMapping("/api/v1/events/{id:\\d+}/image")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove(@PathVariable long id) {
        events.get(id);
        images.findById(id).ifPresent(i -> {
            images.delete(i);
            auditTrail.record(AuditAction.EVENT_IMAGE_SAVED, "event", id, "photo removed");
        });
    }

    /** Staff preview, drafts included. Private: never cached by shared caches. */
    @GetMapping("/api/v1/events/{id:\\d+}/image")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> staffImage(@PathVariable long id) {
        events.get(id);
        return images.findById(id).map(i -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.noCache().cachePrivate()).header("Content-Disposition", "inline")
                        .body(i.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/public/events/{id:\\d+}/image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> publicImage(@PathVariable long id, @RequestParam(name = "v", required = false) String v) {
        if (TenantContext.get() == null) {
            return ResponseEntity.notFound().build();
        }
        boolean published = events.list().stream().anyMatch(e -> e.getId() == id && e.getStatus() == EventStatus.PUBLISHED);
        if (!published) {
            return ResponseEntity.notFound().build();
        }
        return images.findById(id).map(i -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(i.getVersion().equals(v)
                                ? CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable()
                                : CacheControl.noCache())
                        .eTag(i.getVersion())
                        .header("Content-Disposition", "inline")
                        .body(i.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
