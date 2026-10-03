package app.sevacenter.donation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.auth.StaffUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 80G receipts and the trust profile they print (ADR 0012). LEADER: view the profile, issue and
 * view receipts. TRUST_ADMIN: also edit the profile. MEMBER: nothing. The full donor PAN appears
 * only in a single receipt's response (for printing), never in lists, and is never cached.
 */
@RestController
public class ReceiptController {

    private final ReceiptService service;
    private final DonationService donations;

    public ReceiptController(ReceiptService service, DonationService donations) {
        this.service = service;
        this.donations = donations;
    }

    @GetMapping("/api/v1/trust-profile")
    @PreAuthorize("hasRole('LEADER')")
    public ResponseEntity<ProfileResponse> profile() {
        return service.profile().map(p -> ResponseEntity.ok(ProfileResponse.of(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/trust-profile")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public ProfileResponse saveProfile(@Valid @RequestBody ProfileRequest r, @AuthenticationPrincipal StaffUser staff) {
        return ProfileResponse.of(service.saveProfile(r.legalName(), r.address(), r.pan(), r.registration80g(),
                r.validFrom(), r.validTo(), staff.userId()));
    }

    @PostMapping("/api/v1/donations/{id}/receipt")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public ResponseEntity<ReceiptResponse> issue(@PathVariable long id, @Valid @RequestBody IssueRequest r,
                                                 @AuthenticationPrincipal StaffUser staff) {
        Receipt receipt = service.issue(id, r.donorPan(), r.donorAddress(), staff.userId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(full(receipt));
    }

    @GetMapping("/api/v1/receipts/{id}")
    @PreAuthorize("hasRole('LEADER')")
    public ResponseEntity<ReceiptResponse> get(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(full(service.get(id)));
    }

    @GetMapping("/api/v1/receipts")
    @PreAuthorize("hasRole('LEADER')")
    public ReceiptPage list(@RequestParam(required = false) Integer fy,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "25") int size) {
        int year = fy == null ? donations.currentFinancialYear().startYear() : Math.clamp(fy, 2000, 2100);
        Page<Receipt> result = service.list(year, page, size);
        return new ReceiptPage(result.map(this::masked).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    private ReceiptResponse full(Receipt r) {
        return ReceiptResponse.of(r, service.donorPan(r), service.cancellation(r.getId()).orElse(null));
    }

    private ReceiptResponse masked(Receipt r) {
        return ReceiptResponse.of(r, PanProtection.masked(r.getDonorPanLast4()), service.cancellation(r.getId()).orElse(null));
    }

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record ProfileRequest(
            @Schema(example = "Shri Siddheshwar Seva Trust") @NotBlank @Size(max = 200) String legalName,
            @Schema(example = "1 Temple Road, Pune 411001") @NotBlank @Size(max = 400) String address,
            @Schema(example = "AAATS1234F") @NotBlank @Size(max = 20) String pan,
            @Schema(example = "AAATS1234FF20214") @NotBlank @Size(min = 5, max = 60) String registration80g,
            @Schema(example = "2021-04-01") @NotNull LocalDate validFrom,
            @Schema(example = "2030-03-31") @NotNull LocalDate validTo) {
    }

    public record ProfileResponse(String legalName, String address, String pan, String registration80g,
                                  LocalDate validFrom, LocalDate validTo, OffsetDateTime updatedAt) {
        static ProfileResponse of(TrustProfile p) {
            return new ProfileResponse(p.getLegalName(), p.getAddress(), p.getPan(), p.getRegistration80g(),
                    p.getValidFrom(), p.getValidTo(), p.getUpdatedAt());
        }
    }

    public record IssueRequest(
            @Schema(example = "ABCPE1234F") @NotBlank @Size(max = 20) String donorPan,
            @Schema(example = "12 Temple Street, Pune 411001") @NotBlank @Size(max = 400) String donorAddress) {
    }

    public record ReceiptResponse(long id, String number, long donationId, LocalDate issuedOn, String donorName,
                                  String donorAddress, String donorPan, String amount, DonationMode mode,
                                  LocalDate receivedOn, String trustLegalName, String trustAddress, String trustPan,
                                  String trustRegistration80g, boolean cancelled, String cancellationReason) {
        static ReceiptResponse of(Receipt r, String pan, ReceiptCancellation c) {
            return new ReceiptResponse(r.getId(), r.number(), r.getDonationId(), r.getIssuedOn(), r.getDonorName(),
                    r.getDonorAddress(), pan, Money.toRupees(r.getAmountPaise()), r.getMode(), r.getReceivedOn(),
                    r.getTrustLegalName(), r.getTrustAddress(), r.getTrustPan(), r.getTrustRegistration80g(),
                    c != null, c == null ? null : c.getReason());
        }
    }

    public record ReceiptPage(List<ReceiptResponse> items, int page, int size, long total) { }
}
