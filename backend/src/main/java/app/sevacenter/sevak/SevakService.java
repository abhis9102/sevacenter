package app.sevacenter.sevak;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sevak (volunteer) signups (ADR 0015). Authorization on the controllers; tenancy is RLS. */
@Service
public class SevakService {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final SevakSignupRepository signups;
    private final Clock clock = Clock.systemUTC();

    public SevakService(SevakSignupRepository signups) {
        this.signups = signups;
    }

    @Transactional
    public SevakSignup signUp(String fullName, String phone, String email, String sevaAreas, String availability,
                              String notes) {
        String name = required("fullName", fullName, 120);
        String areas = required("sevaAreas", sevaAreas, 300);
        String cleanPhone = phone == null || phone.isBlank() ? null : DevoteeService.phone(phone);
        String cleanEmail = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
        if (cleanPhone == null && cleanEmail == null) {
            throw new InvalidFieldException("phone", "a phone number or an email is required");
        }
        if (cleanEmail != null && (cleanEmail.length() > 254 || !cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
            throw new InvalidFieldException("email", "email is not valid");
        }
        SevakSignup saved = signups.save(new SevakSignup(currentTenant(), name, cleanPhone, cleanEmail, areas,
                optional("availability", availability, 300), optional("notes", notes, 1000)));
        audit.info("event=sevak_signup tenant={} signup={}", currentTenant(), saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SevakSignup> list() {
        return signups.newestFirst(PageRequest.of(0, 500));
    }

    @Transactional
    public SevakSignup review(long id, boolean approve, long staffId) {
        SevakSignup s = signups.findById(id).orElseThrow(SignupNotFoundException::new);
        s.review(approve, staffId, OffsetDateTime.now(clock));
        audit.info("event=sevak_reviewed tenant={} user={} signup={} approved={}", currentTenant(), staffId, id, approve);
        return s;
    }

    private static String required(String field, String value, int max) {
        String v = optional(field, value, max);
        if (v == null) {
            throw new InvalidFieldException(field, field + " is required");
        }
        return v;
    }

    private static String optional(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > max || v.indexOf('\0') >= 0) {
            throw new InvalidFieldException(field, field + " is too long");
        }
        return v;
    }

    private static long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return tenantId;
    }

    /** 404: unknown here (RLS hides other trusts' signups). */
    public static class SignupNotFoundException extends RuntimeException { }
}
