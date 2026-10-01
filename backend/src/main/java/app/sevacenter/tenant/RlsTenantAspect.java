package app.sevacenter.tenant;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Pins the Postgres RLS tenant for the current transaction.
 *
 * <p>At the start of every transactional service call, if a tenant is set in
 * {@link TenantContext}, run {@code set_config('app.tenant_id', <id>, true)} — the
 * {@code true} makes it transaction-local ({@code SET LOCAL}), so it is scoped to this
 * transaction and never leaks across pooled connections. The RLS policies read this GUC.
 *
 * <p>Ordering matters: {@code @EnableTransactionManagement} is set to HIGHEST_PRECEDENCE
 * (see {@link TenancyConfig}) so the transaction is opened BEFORE this advice runs, and
 * the {@code SET LOCAL} lands on the transaction's own connection.
 */
@Aspect
@Component
public class RlsTenantAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public void setTenantForTransaction() {
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            entityManager
                    .createNativeQuery("select set_config('app.tenant_id', :tid, true)")
                    .setParameter("tid", tenantId.toString())
                    .getSingleResult();
        }
    }
}
