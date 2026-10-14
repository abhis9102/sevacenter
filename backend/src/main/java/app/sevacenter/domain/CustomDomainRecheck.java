package app.sevacenter.domain;

import java.util.Set;
import java.util.stream.Collectors;

import app.sevacenter.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily: every active custom domain must still carry its TXT proof, or it is disconnected (no
 * routing, no new certificates). Stops a domain that changed hands, or whose CNAME was left
 * dangling, from serving a temple's pages. 10:15 IST, while staging is awake.
 */
@Component
class CustomDomainRecheck {

    private static final Logger log = LoggerFactory.getLogger(CustomDomainRecheck.class);

    private final TenantDomainRepository routes;
    private final CustomDomainService service;

    CustomDomainRecheck(TenantDomainRepository routes, CustomDomainService service) {
        this.routes = routes;
        this.service = service;
    }

    @Scheduled(cron = "0 15 10 * * *", zone = "Asia/Kolkata")
    void run() {
        Set<Long> tenantIds = routes.findAll().stream().map(TenantDomain::getTenantId).collect(Collectors.toSet());
        int off = 0;
        for (Long tenantId : tenantIds) {
            TenantContext.set(tenantId); // RLS: each trust's claims in its own context
            try {
                off += service.recheckActive();
            } catch (RuntimeException e) {
                log.warn("custom domain recheck failed for tenant {}: {}", tenantId, e.getClass().getSimpleName());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("custom domain recheck: {} trust(s), {} deactivated", tenantIds.size(), off);
    }
}
