package app.sevacenter.donation;

import java.util.List;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Earmarked donation funds (ADR 0022): LEADER+ (who see donations) list them, TRUST_ADMIN manages
 * them, and the donate page lists the active ones by name.
 */
@RestController
public class FundController {

    private final FundService service;

    public FundController(FundService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/donation-funds")
    @PreAuthorize("hasRole('LEADER')")
    public List<FundResponse> list() {
        return service.list().stream().map(FundResponse::of).toList();
    }

    @PostMapping("/api/v1/donation-funds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public FundResponse create(@Valid @RequestBody FundRequest r, @AuthenticationPrincipal StaffUser staff) {
        return FundResponse.of(service.save(null, r.name(), r.active() == null || r.active(), staff.userId()));
    }

    @PutMapping("/api/v1/donation-funds/{id:\\d+}")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public FundResponse update(@PathVariable long id, @Valid @RequestBody FundRequest r,
                               @AuthenticationPrincipal StaffUser staff) {
        return FundResponse.of(service.save(id, r.name(), r.active() == null || r.active(), staff.userId()));
    }

    /** Active funds for the donate page: names and ids only. */
    @GetMapping("/api/v1/public/donation-funds")
    public ResponseEntity<List<PublicFund>> publicList() {
        if (TenantContext.get() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(service.active().stream().map(f -> new PublicFund(f.getId(), f.getName())).toList());
    }

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record FundRequest(@Schema(example = "Annadanam fund") @NotBlank @Size(max = 80) String name,
                              @Schema(example = "true") Boolean active) { }

    public record FundResponse(long id, String name, boolean active) {
        static FundResponse of(DonationFund f) {
            return new FundResponse(f.getId(), f.getName(), f.isActive());
        }
    }

    public record PublicFund(long id, String name) { }
}
