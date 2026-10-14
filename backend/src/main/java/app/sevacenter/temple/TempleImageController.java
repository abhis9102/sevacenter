package app.sevacenter.temple;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.donation.DonationService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The temple's photo on its MandirCenter page. Upload: LEADER+, re-encoded by
 * TempleImageProcessor (only its output is stored). Public read on the trust's own host.
 */
@RestController
public class TempleImageController {

    private final TempleImageRepository images;
    private final TempleImageProcessor processor;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.system(DonationService.IST);

    public TempleImageController(TempleImageRepository images, TempleImageProcessor processor, AuditTrail auditTrail) {
        this.images = images;
        this.processor = processor;
        this.auditTrail = auditTrail;
    }

    @PutMapping(value = "/api/v1/temple/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LEADER')")
    @Transactional
    public Map<String, String> upload(@RequestParam("image") MultipartFile file,
                                      @AuthenticationPrincipal StaffUser staff) throws IOException {
        TempleImageProcessor.Processed processed = processor.process(file.getBytes());
        long tenantId = TenantContext.get();
        TempleImage image = images.findById(tenantId).orElseGet(() -> new TempleImage(tenantId));
        image.replace(processed, staff.userId(), OffsetDateTime.now(clock));
        images.save(image);
        auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_image", null, "photo " + processed.version());
        return Map.of("imageVersion", processed.version());
    }

    @DeleteMapping("/api/v1/temple/image")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove() {
        images.findById(TenantContext.get()).ifPresent(i -> {
            images.delete(i);
            auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_image", null, "photo removed");
        });
    }

    /**
     * Public, on the trust's own host. The URL carries the version, so it may be cached a long time;
     * a new photo gets a new URL. Bytes are always our own JPEG: nosniff + image/jpeg + inline.
     */
    @GetMapping("/api/v1/public/temple/image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> publicImage(@RequestParam(name = "v", required = false) String v) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.notFound().build();
        }
        return images.findById(tenantId)
                .map(i -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        // Long-lived only for the versioned URL the page uses; a bare URL revalidates.
                        .cacheControl(i.getVersion().equals(v)
                                ? CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic().immutable()
                                : CacheControl.noCache())
                        .eTag(i.getVersion())
                        .header("Content-Disposition", "inline")
                        .body(i.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
