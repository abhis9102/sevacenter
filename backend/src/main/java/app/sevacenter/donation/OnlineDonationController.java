package app.sevacenter.donation;

import java.time.OffsetDateTime;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Online donations (ADR 0013). Public on the trust's own host (devotees don't sign in): info,
 * order, confirm. TRUST_ADMIN: gateway settings and reconciliation. The key secret is write-only.
 */
@RestController
public class OnlineDonationController {

    private final OnlineDonationService service;
    private final RateLimiter rateLimiter;
    private final TenantRepository tenants;

    public OnlineDonationController(OnlineDonationService service, RateLimiter rateLimiter, TenantRepository tenants) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.tenants = tenants;
    }

    // --- public -------------------------------------------------------------------------------

    /** Which trust this host is, and whether it takes online donations. No tenant: 404. */
    @GetMapping("/api/v1/public/donations/info")
    public ResponseEntity<InfoResponse> info() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.notFound().build();
        }
        String name = tenants.findById(tenantId).map(t -> t.getName()).orElse(null);
        return ResponseEntity.ok(new InfoResponse(name, service.settings().isPresent()));
    }

    @PostMapping("/api/v1/public/donations/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OnlineDonationService.CreatedOrder createOrder(@Valid @RequestBody OrderRequest r, HttpServletRequest request) {
        requireTenant();
        if (!rateLimiter.tryAcquire("order:" + request.getRemoteAddr())) {
            throw new app.sevacenter.auth.LoginThrottle.TooManyAttemptsException();
        }
        return service.createOrder(Money.toPaise("amount", r.amount()), r.donorName(), r.purpose());
    }

    @PostMapping("/api/v1/public/donations/confirm")
    public ConfirmResponse confirm(@Valid @RequestBody ConfirmRequest r) {
        requireTenant();
        Donation d = service.confirm(r.orderId(), r.paymentId(), r.signature());
        return new ConfirmResponse(d.getId(), Money.toRupees(d.getAmountPaise()), d.getDonorName(), d.getReceivedOn().toString());
    }

    // --- staff --------------------------------------------------------------------------------

    @GetMapping("/api/v1/payment-settings")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public ResponseEntity<SettingsResponse> settings() {
        return service.settings().map(s -> ResponseEntity.ok(SettingsResponse.of(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/payment-settings")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public SettingsResponse saveSettings(@Valid @RequestBody SettingsRequest r, @AuthenticationPrincipal StaffUser staff) {
        return SettingsResponse.of(service.saveSettings(r.keyId(), r.keySecret(), staff.userId()));
    }

    @PostMapping("/api/v1/payment-settings/reconcile")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public ReconcileResponse reconcile() {
        return new ReconcileResponse(service.reconcile());
    }

    private static void requireTenant() {
        if (TenantContext.get() == null) {
            throw new OnlineDonationService.PaymentsNotConfiguredException();
        }
    }

    public record InfoResponse(String trustName, boolean onlineDonations) { }

    /** Examples are valid on purpose, so DAST attacks reach the code (ADR 0013). */
    public record OrderRequest(
            @Schema(example = "501") @NotBlank @Size(max = 20) String amount,
            @Schema(example = "Lakshmi Iyer") @NotBlank @Size(max = 120) String donorName,
            @Schema(example = "Annadanam") @Size(max = 120) String purpose) { }

    public record ConfirmRequest(
            @Schema(example = "order_PZ1example00001") @NotBlank @Size(max = 64) String orderId,
            @Schema(example = "pay_PZ1example00001") @NotBlank @Size(max = 64) String paymentId,
            @Schema(example = "0000000000000000000000000000000000000000000000000000000000000000")
            @NotBlank @Size(max = 128) String signature) { }

    public record ConfirmResponse(long donationId, String amount, String donorName, String receivedOn) { }

    public record SettingsRequest(
            @Schema(example = "rzp_test_ExampleKey01") @NotBlank @Size(max = 40) String keyId,
            @Schema(example = "example-secret-value") @NotBlank @Size(max = 64) String keySecret) { }

    /** Never the secret: only whether one is set, the key id and test/live. */
    public record SettingsResponse(String keyId, boolean live, OffsetDateTime updatedAt) {
        static SettingsResponse of(PaymentSettings s) {
            return new SettingsResponse(s.getKeyId(), s.getKeyId().startsWith("rzp_live_"), s.getUpdatedAt());
        }
    }

    public record ReconcileResponse(int settled) { }
}
