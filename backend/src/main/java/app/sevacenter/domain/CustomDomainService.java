package app.sevacenter.domain;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.tenant.PublicUrls;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.web.InvalidFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A temple's MandirCenter on its own domain (ADR 0036). A claim becomes active only when DNS
 * proves ownership: a TXT record {@code _sevacenter.<hostname>} holding the claim's secret token.
 * Only then does the routing registry (tenant_domain) map the name to this trust, so the edge
 * may get a certificate for it and the public pages answer on it.
 */
@Service
public class CustomDomainService {

    static final int MAX_DOMAINS = 3;
    static final String TXT_PREFIX = "_sevacenter.";

    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomDomainRepository claims;
    private final TenantDomainRepository routes;
    private final TenantRepository tenants;
    private final PublicUrls urls;
    private final DnsLookup dns;
    private final AuditTrail auditTrail;
    private final Clock clock = Clock.systemUTC();

    public CustomDomainService(CustomDomainRepository claims, TenantDomainRepository routes, TenantRepository tenants,
                               PublicUrls urls, DnsLookup dns, AuditTrail auditTrail) {
        this.claims = claims;
        this.routes = routes;
        this.tenants = tenants;
        this.urls = urls;
        this.dns = dns;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    public List<CustomDomain> list() {
        return claims.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public CustomDomain add(String rawHostname, long staffId) {
        long tenantId = currentTenant();
        String hostname = Hostnames.normalise(rawHostname, urls.baseDomains());
        if (claims.findByHostname(hostname).isPresent()) {
            throw new InvalidFieldException("hostname", "You've already added this domain");
        }
        if (claims.count() >= MAX_DOMAINS) {
            throw new InvalidFieldException("hostname", "At most " + MAX_DOMAINS + " domains per temple");
        }
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String token = "sc-verify-" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        CustomDomain claim = claims.save(new CustomDomain(tenantId, hostname, token, staffId, now()));
        auditTrail.record(AuditAction.CUSTOM_DOMAIN_ADDED, "custom_domain", claim.getId(), hostname);
        return claim;
    }

    /** Checks DNS now: activates on proof of ownership; reports whether the name points here yet. */
    @Transactional
    public CustomDomain verify(long id) {
        CustomDomain claim = claims.findById(id).orElseThrow(NotFoundException::new);
        boolean owned = owns(claim);
        claim.checked(pointsHere(claim.getHostname()), now());
        if (!owned) {
            if (claim.getStatus() == CustomDomain.Status.ACTIVE) {
                deactivate(claim, "The TXT record is gone");
            } else {
                claim.fail("TXT record " + TXT_PREFIX + claim.getHostname() + " not found or doesn't match");
            }
            return claim;
        }
        Optional<TenantDomain> route = routes.findById(claim.getHostname());
        if (route.isPresent() && !route.get().getTenantId().equals(claim.getTenantId())) {
            claim.fail("This domain is already connected to another temple");
            return claim;
        }
        if (route.isEmpty()) {
            routes.save(new TenantDomain(claim.getHostname(), claim.getTenantId(), now()));
        }
        if (claim.getStatus() != CustomDomain.Status.ACTIVE) {
            claim.activate(now());
            auditTrail.record(AuditAction.CUSTOM_DOMAIN_VERIFIED, "custom_domain", claim.getId(), claim.getHostname());
        }
        return claim;
    }

    @Transactional
    public void remove(long id) {
        CustomDomain claim = claims.findById(id).orElseThrow(NotFoundException::new);
        routeOf(claim).ifPresent(routes::delete);
        claims.delete(claim);
        auditTrail.record(AuditAction.CUSTOM_DOMAIN_REMOVED, "custom_domain", claim.getId(), claim.getHostname());
    }

    /** Re-check of this tenant's active domains (CustomDomainRecheck, daily): a lost proof deactivates. */
    @Transactional
    public int recheckActive() {
        int deactivated = 0;
        for (CustomDomain claim : claims.findAllByStatus(CustomDomain.Status.ACTIVE.name())) {
            claim.checked(pointsHere(claim.getHostname()), now());
            if (!owns(claim)) {
                deactivate(claim, "The TXT record was removed; verify again to reconnect");
                deactivated++;
            }
        }
        return deactivated;
    }

    /** The routing registry: the trust serving this hostname, if it's a verified custom domain. */
    @Transactional(readOnly = true)
    public Optional<Long> tenantFor(String hostname) {
        return routes.findById(hostname).map(TenantDomain::getTenantId);
    }

    /** The DNS name the temple points its domain at: the trust's own MandirCenter host. */
    public String target() {
        String slug = tenants.findById(currentTenant()).orElseThrow().getSlug();
        return URI.create(urls.temple(slug)).getHost();
    }

    private void deactivate(CustomDomain claim, String reason) {
        routeOf(claim).ifPresent(routes::delete);
        claim.fail(reason);
        auditTrail.record(AuditAction.CUSTOM_DOMAIN_DEACTIVATED, "custom_domain", claim.getId(), claim.getHostname());
        audit.info("event=custom_domain_deactivated tenant={} domain={}", claim.getTenantId(), claim.getHostname());
    }

    private Optional<TenantDomain> routeOf(CustomDomain claim) {
        return routes.findById(claim.getHostname()).filter(r -> r.getTenantId().equals(claim.getTenantId()));
    }

    private boolean owns(CustomDomain claim) {
        return dns.txt(TXT_PREFIX + claim.getHostname()).stream().anyMatch(claim.getToken()::equals);
    }

    /** CNAME to our host, or the same A records as our host (apex domains can't CNAME). */
    private boolean pointsHere(String hostname) {
        String target = target();
        if (dns.cname(hostname).contains(target)) {
            return true;
        }
        Set<String> ours = new HashSet<>(dns.a(target));
        List<String> theirs = dns.a(hostname);
        return !ours.isEmpty() && !theirs.isEmpty() && ours.containsAll(theirs);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static long currentTenant() {
        Long t = TenantContext.get();
        if (t == null) {
            throw new IllegalStateException("custom domains need a tenant");
        }
        return t;
    }

    /** 404: not one of this trust's domains. */
    public static class NotFoundException extends RuntimeException { }
}
