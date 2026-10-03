package app.sevacenter.audit;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the trust's audit log (ADR 0020). MANDATORY propagation: an entry is written in the same
 * transaction as the action it records, so it commits exactly when the action does. An action that
 * rolls back leaves no entry, and an entry that can't be written rolls the action back: nothing
 * happens unrecorded. The actor comes from the security context, never from the caller.
 */
@Component
public class AuditTrail {

    private final AuditEntryRepository entries;

    AuditTrail(AuditEntryRepository entries) {
        this.entries = entries;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String targetType, Long targetId, String detail) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("audit outside a tenant");
        }
        entries.save(new AuditEntry(tenantId, currentStaff(), action, targetType, targetId, detail));
    }

    private static Long currentStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof StaffUser s ? s.userId() : null;
    }
}
