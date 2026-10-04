package app.sevacenter.donation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.donation.DonationService.FinancialYear;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The donation ledger (ADR 0011). LEADER: record, list, summarise. TRUST_ADMIN: also reverse.
 * MEMBER: nothing; donation history is financial data about named people. No update or delete
 * endpoints exist: corrections are reversals.
 */
@RestController
@RequestMapping("/api/v1/donations")
public class DonationController {

    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    private final DonationService service;

    public DonationController(DonationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public DonationResponse record(@Valid @RequestBody RecordDonationRequest request,
                                   @AuthenticationPrincipal StaffUser staff) {
        if (request.receivedOn().isBefore(EARLIEST)) {
            throw new app.sevacenter.web.InvalidFieldException("receivedOn", "receivedOn is too far in the past");
        }
        return DonationResponse.of(service.record(request.devoteeId(), request.donorName(),
                Money.toPaise("amount", request.amount()), request.mode(), request.reference(), request.purpose(),
                request.fundId(), request.receivedOn(), staff.userId()));
    }

    @GetMapping
    @PreAuthorize("hasRole('LEADER')")
    public PageResponse search(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Long devoteeId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size) {
        FinancialYear fy = service.currentFinancialYear();
        Page<Donation> result = service.search(from == null ? fy.start() : from, to == null ? fy.end() : to,
                devoteeId, page, size);
        return new PageResponse(result.map(DonationResponse::of).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('LEADER')")
    public DonationResponse get(@PathVariable long id) {
        return DonationResponse.of(service.get(id));
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public DonationResponse reverse(@PathVariable long id, @Valid @RequestBody ReverseRequest request,
                                    @AuthenticationPrincipal StaffUser staff) {
        return DonationResponse.of(service.reverse(id, request.reason(), staff.userId()));
    }

    /** Net totals per mode for a financial year (default: the current one, in IST). */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('LEADER')")
    public SummaryResponse summary(@RequestParam(required = false) Integer fy) {
        FinancialYear year = fy == null ? service.currentFinancialYear() : new FinancialYear(Math.clamp(fy, 2000, 2100));
        List<DonationRepository.ModeTotal> totals = service.totals(year);
        List<ModeSummary> modes = totals.stream()
                .map(t -> new ModeSummary(t.getMode(), Money.toRupees(t.getNetPaise()), t.getDonations(), t.getReversals()))
                .toList();
        long net = totals.stream().mapToLong(DonationRepository.ModeTotal::getNetPaise).sum();
        List<FundSummary> funds = service.fundTotals(year).stream()
                .map(f -> new FundSummary(f.getFundId(), f.getFundName() == null ? "General fund" : f.getFundName(),
                        Money.toRupees(f.getNetPaise()), f.getDonations()))
                .toList();
        return new SummaryResponse(year.label(), year.start(), year.end(), Money.toRupees(net), modes, funds);
    }

    /** Examples are valid on purpose, so DAST attacks reach the database. */
    public record RecordDonationRequest(
            @Schema(example = "Lakshmi Iyer", description = "Required without devoteeId; ignored with one")
            @Size(max = 120) String donorName,
            @Schema(description = "Optional: links the donation to a devotee record") Long devoteeId,
            @Schema(example = "1500.50", description = "Rupees as a decimal string, at most 2 decimals")
            @NotBlank @Size(max = 20) String amount,
            @Schema(example = "UPI") @NotNull DonationMode mode,
            @Schema(example = "UTR 412345678901") @Size(max = 64) String reference,
            @Schema(example = "Annadanam") @Size(max = 120) String purpose,
            @Schema(description = "Optional earmarked fund (ADR 0022); omit for the general fund") Long fundId,
            @Schema(example = "2026-04-14") @NotNull LocalDate receivedOn) {
    }

    public record ReverseRequest(
            @Schema(example = "Entered twice by mistake") @NotBlank @Size(min = 10, max = 300) String reason) {
    }

    public record DonationResponse(long id, Long devoteeId, String donorName, String amount, DonationMode mode,
                                   String reference, String purpose, LocalDate receivedOn, Long reversesId,
                                   String reversalReason, Long recordedBy, DonationChannel channel,
                                   String paymentRef, OffsetDateTime createdAt, Long fundId) {
        static DonationResponse of(Donation d) {
            return new DonationResponse(d.getId(), d.getDevoteeId(), d.getDonorName(), Money.toRupees(d.getAmountPaise()),
                    d.getMode(), d.getReference(), d.getPurpose(), d.getReceivedOn(), d.getReversesId(),
                    d.getReversalReason(), d.getRecordedBy(), d.getChannel(), d.getPaymentRef(), d.getCreatedAt(),
                    d.getFundId());
        }
    }

    public record PageResponse(List<DonationResponse> items, int page, int size, long total) { }

    public record ModeSummary(DonationMode mode, String net, long donations, long reversals) { }

    public record FundSummary(Long fundId, String fund, String net, long donations) { }

    public record SummaryResponse(String financialYear, LocalDate from, LocalDate to, String net, List<ModeSummary> byMode,
                                  List<FundSummary> byFund) { }
}
