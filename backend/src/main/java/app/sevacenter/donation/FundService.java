package app.sevacenter.donation;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Earmarked donation funds (ADR 0022). Authorization on the controller; tenancy is RLS. */
@Service
public class FundService {

    private final DonationFundRepository funds;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.systemUTC();

    public FundService(DonationFundRepository funds, AuditTrail auditTrail) {
        this.funds = funds;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    public List<DonationFund> list() {
        return funds.listed();
    }

    @Transactional(readOnly = true)
    public List<DonationFund> active() {
        return funds.active();
    }

    @Transactional
    public DonationFund save(Long id, String rawName, boolean active, long staffId) {
        String name = rawName == null ? "" : rawName.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 80 || name.indexOf('\0') >= 0) {
            throw new InvalidFieldException("name", "a fund needs a name of up to 80 characters");
        }
        if (funds.nameTaken(name, id)) {
            throw new InvalidFieldException("name", "a fund with this name already exists");
        }
        DonationFund f = id == null ? new DonationFund(currentTenant())
                : funds.findById(id).orElseThrow(FundNotFoundException::new);
        f.edit(name, active, staffId, OffsetDateTime.now(clock));
        DonationFund saved;
        try {
            saved = funds.saveAndFlush(f);
        } catch (DataIntegrityViolationException e) {
            // Two saves of the same name at once both pass the check above; the unique index lets
            // one win, and the other gets the same answer as a plain duplicate (found by DAST).
            throw new InvalidFieldException("name", "a fund with this name already exists");
        }
        auditTrail.record(AuditAction.FUND_SAVED, "donation_fund", saved.getId(), active ? "active" : "inactive");
        return saved;
    }

    /** The fund a new donation may be given to: null stays the general fund; inactive or unknown is refused. */
    @Transactional(readOnly = true)
    public Long usable(Long fundId) {
        if (fundId == null) {
            return null;
        }
        return funds.findById(fundId).filter(DonationFund::isActive).map(DonationFund::getId)
                .orElseThrow(() -> new InvalidFieldException("fundId", "choose an active fund"));
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    /** 404: unknown here (RLS hides other trusts' funds). */
    public static class FundNotFoundException extends RuntimeException { }
}
