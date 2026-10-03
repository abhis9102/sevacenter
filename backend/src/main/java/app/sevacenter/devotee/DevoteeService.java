package app.sevacenter.devotee;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Devotee records (ADR 0010). Authorization is on the controller; tenancy is RLS. */
@Service
public class DevoteeService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_PAGE = 10_000;
    private static final Sort ORDER = Sort.by("fullName").and(Sort.by("id"));

    private final DevoteeRepository devotees;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.systemUTC();

    public DevoteeService(DevoteeRepository devotees, AuditTrail auditTrail) {
        this.devotees = devotees;
        this.auditTrail = auditTrail;
    }

    /** {@code contactSearch}: may the caller search by phone/email (roles that see them in full). */
    @Transactional(readOnly = true)
    public Page<Devotee> search(String q, int page, int size, boolean contactSearch) {
        PageRequest pageable = PageRequest.of(Math.clamp(page, 0, MAX_PAGE), Math.clamp(size, 1, MAX_PAGE_SIZE), ORDER);
        String term = q == null ? "" : q.strip();
        return contactSearch ? devotees.searchAll(term, pageable) : devotees.searchByName(term, pageable);
    }

    /** Every devotee of the tenant, for the admin-only CSV export. */
    @Transactional
    public java.util.List<Devotee> exportAll() {
        java.util.List<Devotee> all = devotees.findAllByErasedAtIsNull(ORDER);
        auditTrail.record(AuditAction.DEVOTEES_EXPORTED, "devotee", null, all.size() + " rows");
        return all;
    }

    @Transactional(readOnly = true)
    public Devotee get(long id) {
        return devotees.findByIdAndErasedAtIsNull(id).orElseThrow(DevoteeNotFoundException::new);
    }

    @Transactional
    public Devotee create(Devotee.Details details, ConsentSource consent, long staffId) {
        Devotee saved = createUnaudited(details, consent, staffId);
        auditTrail.record(AuditAction.DEVOTEE_CREATED, "devotee", saved.getId(), null);
        return saved;
    }

    /** For the CSV import, which records one DEVOTEES_IMPORTED entry instead of one per row. */
    Devotee createUnaudited(Devotee.Details details, ConsentSource consent, long staffId) {
        return devotees.save(new Devotee(currentTenant(), normalise(details), consent, staffId, now()));
    }

    @Transactional
    public Devotee update(long id, Devotee.Details details, long staffId) {
        Devotee devotee = get(id);
        devotee.update(normalise(details), staffId, now());
        auditTrail.record(AuditAction.DEVOTEE_UPDATED, "devotee", id, null);
        return devotee;
    }

    /**
     * Right to erasure (DPDP). A hard delete, unless donations reference the devotee: then every
     * personal field is removed and the row is kept for the ledger (ADR 0011).
     */
    @Transactional
    public void erase(long id, long staffId) {
        Devotee devotee = get(id);
        auditTrail.record(AuditAction.DEVOTEE_ERASED, "devotee", id, null);
        if (devotees.hasDonations(id)) {
            devotee.anonymise(staffId, now());
        } else {
            devotees.delete(devotee);
        }
    }

    static Devotee.Details normalise(Devotee.Details d) {
        // Every write path (API and CSV import) passes here; Postgres can't store NUL in text.
        noNul("fullName", d.fullName());
        noNul("phone", d.phone());
        noNul("email", d.email());
        noNul("addressLine", d.addressLine());
        noNul("city", d.city());
        noNul("state", d.state());
        noNul("pincode", d.pincode());
        String email = blankToNull(d.email());
        return new Devotee.Details(d.fullName().strip(), phone(d.phone()),
                email == null ? null : email.toLowerCase(Locale.ROOT), blankToNull(d.addressLine()),
                blankToNull(d.city()), blankToNull(d.state()), blankToNull(d.pincode()), d.dateOfBirth());
    }

    /**
     * Indian numbers as typed ("98765 43210", "098765-43210", "+91 98765 43210") become E.164
     * ("+919876543210"); other countries must already start with "+".
     */
    public static String phone(String raw) {
        String s = blankToNull(raw);
        if (s == null) {
            return null;
        }
        String digits = s.replaceAll("[\\s\\-().]", "");
        if (digits.matches("0?[6-9]\\d{9}")) {
            digits = "+91" + digits.substring(digits.length() - 10);
        }
        if (!digits.matches("\\+[1-9]\\d{7,14}")) {
            throw new InvalidFieldException("phone", "phone must be a 10-digit Indian mobile or +<country code><number>");
        }
        return digits;
    }

    private static void noNul(String field, String value) {
        if (value != null && value.indexOf('\0') >= 0) {
            throw new InvalidFieldException(field, field + " contains a NUL character");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    /** 404: unknown here, including another tenant's devotee (RLS hides it). */
    public static class DevoteeNotFoundException extends RuntimeException { }
}
