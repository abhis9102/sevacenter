package app.sevacenter.domain;

import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.auth.StaffUser;
import app.sevacenter.donation.RateLimiter;
import app.sevacenter.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** MandirCenter on the temple's own domain (ADR 0036). Managing domains: TRUST_ADMIN only. */
@RestController
public class CustomDomainController {

    private final CustomDomainService service;
    private final RateLimiter rateLimiter;

    public CustomDomainController(CustomDomainService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/api/v1/custom-domains")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public Domains list() {
        return new Domains(service.target(), service.list().stream().map(DomainResponse::of).toList());
    }

    @PostMapping("/api/v1/custom-domains")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DomainResponse add(@Valid @RequestBody AddRequest r, @AuthenticationPrincipal StaffUser staff) {
        return DomainResponse.of(service.add(r.hostname(), staff.userId()));
    }

    /** Looks the records up now. Rate-limited: each check makes several DNS-over-HTTPS queries. */
    @PostMapping("/api/v1/custom-domains/{id:\\d+}/verify")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public DomainResponse verify(@PathVariable long id) {
        if (!rateLimiter.tryAcquire("domain-verify:" + TenantContext.get())) {
            throw new LoginThrottle.TooManyAttemptsException();
        }
        return DomainResponse.of(service.verify(id));
    }

    @DeleteMapping("/api/v1/custom-domains/{id:\\d+}")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) {
        service.remove(id);
    }

    /**
     * For the edge's on-demand TLS (Caddy "ask"): 200 only for a verified, active custom domain,
     * so nobody can make us request certificates for names that aren't connected. Public data.
     */
    @GetMapping("/api/v1/public/custom-domains/allowed")
    public ResponseEntity<Void> allowed(@RequestParam String domain) {
        String h = domain.strip().toLowerCase(java.util.Locale.ROOT);
        return h.length() <= 253 && service.tenantFor(h).isPresent()
                ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    public record AddRequest(@Schema(example = "www.shrisiddhivinayak.org") @NotBlank @Size(max = 253) String hostname) { }

    public record Domains(@Schema(description = "Point your domain here (CNAME), or at its address (A)")
                         String target, List<DomainResponse> domains) { }

    public record DomainResponse(long id, String hostname, CustomDomain.Status status, boolean pointsHere,
                                 String lastError, OffsetDateTime verifiedAt, OffsetDateTime lastCheckedAt,
                                 @Schema(description = "TXT record name to create") String txtName,
                                 @Schema(description = "TXT record value: proves you own the domain") String txtValue) {
        static DomainResponse of(CustomDomain d) {
            return new DomainResponse(d.getId(), d.getHostname(), d.getStatus(), d.isPointsHere(), d.getLastError(),
                    d.getVerifiedAt(), d.getLastCheckedAt(), CustomDomainService.TXT_PREFIX + d.getHostname(), d.getToken());
        }
    }
}
