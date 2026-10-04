package app.sevacenter.donation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The donation ledger (ADR 0011). Authorization is on the controller; tenancy is RLS. */
@Service
public class DonationService {

    /** Indian financial years and "today" are in IST, whatever the server's zone. */
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "receivedOn").and(Sort.by(Sort.Direction.DESC, "id"));

    private final DonationRepository donations;
    private final DevoteeService devotees;
    private final ReceiptService receipts;
    private final AuditTrail auditTrail;
    private final FundService funds;
    private final Clock clock = Clock.system(IST);

    public DonationService(DonationRepository donations, DevoteeService devotees, ReceiptService receipts,
                           AuditTrail auditTrail, FundService funds) {
        this.auditTrail = auditTrail;
        this.funds = funds;
        this.donations = donations;
        this.devotees = devotees;
        this.receipts = receipts;
    }

    /**
     * Records a donation. With a devotee, the donor name is taken from their record (a snapshot,
     * kept with the ledger); without one, it's required.
     */
    @Transactional
    public Donation record(Long devoteeId, String donorName, long amountPaise, DonationMode mode, String reference,
                           String purpose, Long fundId, LocalDate receivedOn, long staffId) {
        LocalDate today = LocalDate.now(clock);
        if (receivedOn.isAfter(today)) {
            throw new InvalidFieldException("receivedOn", "receivedOn can't be in the future");
        }
        String name;
        if (devoteeId != null) {
            // RLS: another tenant's devotee id is simply not found.
            name = devotees.get(devoteeId).getFullName();
        } else if (donorName == null || donorName.isBlank()) {
            throw new InvalidFieldException("donorName", "donorName is required without a devotee");
        } else {
            name = donorName.strip();
        }
        Donation saved = donations.save(Donation.received(currentTenant(), devoteeId, name, amountPaise, mode,
                blankToNull(reference), blankToNull(purpose), receivedOn, staffId).toFund(funds.usable(fundId)));
        audit.info("event=donation_recorded tenant={} user={} donation={} paise={}", currentTenant(), staffId,
                saved.getId(), amountPaise);
        auditTrail.record(AuditAction.DONATION_RECORDED, "donation", saved.getId(), "Rs " + Money.toRupees(amountPaise));
        return saved;
    }

    /** Corrections never edit the ledger: they add the negated entry (TRUST_ADMIN, with a reason). */
    @Transactional
    public Donation reverse(long donationId, String reason, long staffId) {
        Donation original = get(donationId);
        if (original.isReversal()) {
            throw new LedgerConflictException("cannot_reverse_a_reversal");
        }
        if (donations.existsByReversesId(donationId)) {
            throw new LedgerConflictException("already_reversed");
        }
        try {
            Donation reversal = donations.saveAndFlush(original.reversal(reason.strip(), LocalDate.now(clock), staffId));
            receipts.cancelForDonation(donationId, reason.strip(), staffId); // same transaction
            auditTrail.record(AuditAction.DONATION_REVERSED, "donation", donationId, "reversal " + reversal.getId());
            audit.info("event=donation_reversed tenant={} user={} donation={} reversal={}", currentTenant(), staffId,
                    donationId, reversal.getId());
            return reversal;
        } catch (DataIntegrityViolationException e) {
            // Two admins reversing at once: the unique constraint on reverses_id lets one win.
            throw new LedgerConflictException("already_reversed");
        }
    }

    @Transactional(readOnly = true)
    public Page<Donation> search(LocalDate from, LocalDate to, Long devoteeId, int page, int size) {
        return donations.search(from, to, devoteeId,
                PageRequest.of(Math.clamp(page, 0, 10_000), Math.clamp(size, 1, 100), NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public Donation get(long id) {
        return donations.findById(id).orElseThrow(DonationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<DonationRepository.ModeTotal> totals(FinancialYear fy) {
        return donations.totalsByMode(fy.start(), fy.end());
    }

    /** Net per fund (ADR 0022); reversals carry their donation's fund, so they net out. */
    @Transactional(readOnly = true)
    public List<DonationRepository.FundTotal> fundTotals(FinancialYear fy) {
        return donations.totalsByFund(fy.start(), fy.end());
    }

    FinancialYear currentFinancialYear() {
        return FinancialYear.containing(LocalDate.now(clock));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    /** Indian FY: 1 April to 31 March. {@code startYear} 2026 is FY 2026-27. */
    public record FinancialYear(int startYear) {
        static FinancialYear containing(LocalDate day) {
            return new FinancialYear(day.getMonthValue() >= 4 ? day.getYear() : day.getYear() - 1);
        }

        LocalDate start() {
            return LocalDate.of(startYear, 4, 1);
        }

        LocalDate end() {
            return LocalDate.of(startYear + 1, 3, 31);
        }

        public String label() {
            return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
        }
    }

    /** 404: unknown here, including another tenant's donation (RLS hides it). */
    public static class DonationNotFoundException extends RuntimeException { }

    /** 409: a reversal that the ledger's rules don't allow. */
    public static class LedgerConflictException extends RuntimeException {
        public LedgerConflictException(String reason) {
            super(reason);
        }
    }
}
