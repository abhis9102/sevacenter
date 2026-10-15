package app.sevacenter.tenant;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves the tenant for each request from the Host subdomain
 * (e.g. {@code siddheshwar.sevacenter.app} or {@code siddheshwar.mandircenter.app})
 * and pins it in {@link TenantContext} for the rest of the request. Runs before Spring
 * Security so tenant-scoped authentication can use it.
 *
 * <p>Security notes: the slug comes only from a Host of exactly {@code <slug>.<base-domain>}
 * with a configured base domain, and is looked up in the registry; any other, unknown or
 * malformed host leaves the context unset, and RLS then fails closed. A dev-only {@code X-Tenant-Slug} override exists for localhost and is
 * off by default.
 *
 * <p>A temple's own verified domain (ADR 0036) also names its tenant, but only for the public
 * MandirCenter API ({@link #CUSTOM_DOMAIN_PATHS}): staff sign-in and the staff API never answer on
 * a domain we don't control, so no staff session cookie can live there. Behind the frontend
 * proxy the original host arrives in {@code X-Tenant-Host}, trusted like {@code X-Tenant-Slug}.
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    /** The only paths a custom domain resolves a tenant for: the public site and devotee portal. */
    static final List<String> CUSTOM_DOMAIN_PATHS = List.of("/api/v1/public/", "/api/v1/portal/", "/api/v1/csrf");

    private final TenantRepository tenants;
    private final boolean allowHeaderOverride;
    private final List<String> baseDomains;
    private final Function<String, Optional<Long>> customDomains;

    public TenantResolutionFilter(TenantRepository tenants, boolean allowHeaderOverride,
                                  List<String> baseDomains, Function<String, Optional<Long>> customDomains) {
        this.tenants = tenants;
        this.allowHeaderOverride = allowHeaderOverride;
        this.baseDomains = List.copyOf(baseDomains);
        this.customDomains = customDomains;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String slug = resolveSlug(request);
            if (slug != null && !ReservedSlugs.isReserved(slug)) {
                tenants.findBySlug(slug).ifPresent(t -> TenantContext.set(t.getId()));
            } else if (slug == null && customDomainPath(request)) {
                String host = customHost(request);
                if (host != null) {
                    customDomains.apply(host).ifPresent(TenantContext::set);
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();   // never let a pooled thread keep a tenant
        }
    }

    private String resolveSlug(HttpServletRequest request) {
        if (allowHeaderOverride) {
            String header = request.getHeader("X-Tenant-Slug");
            if (header != null && !header.isBlank()) {
                return header.trim().toLowerCase(Locale.ROOT);
            }
        }
        return slugFromHost(request.getServerName(), baseDomains);
    }

    private String customHost(HttpServletRequest request) {
        String h = null;
        if (allowHeaderOverride) {
            h = request.getHeader("X-Tenant-Host");
        }
        if (h == null || h.isBlank()) {
            h = request.getServerName();
        }
        if (h == null) {
            return null;
        }
        h = h.strip().toLowerCase(Locale.ROOT);
        return h.length() <= 253 && h.matches("[a-z0-9.-]+") ? h : null;
    }

    static boolean customDomainPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.contains("..") || uri.contains(";") || uri.contains("%")) {
            return false;
        }
        return CUSTOM_DOMAIN_PATHS.stream().anyMatch(p -> p.endsWith("/") ? uri.startsWith(p) : uri.equals(p));
    }

    /**
     * The tenant slug, only for a host of exactly {@code <slug>.<base-domain>} with a
     * configured base domain. Any other host (an attacker's domain, extra labels, a bare base
     * domain) resolves no tenant: a request with {@code Host: siddheshwar.attacker.example}
     * must not act as the siddheshwar tenant (ADR 0009).
     */
    static String slugFromHost(String host, List<String> baseDomains) {
        if (host == null) {
            return null;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String base : baseDomains) {
            String suffix = "." + base.toLowerCase(Locale.ROOT);
            if (h.endsWith(suffix)) {
                String label = h.substring(0, h.length() - suffix.length());
                return label.isEmpty() || label.contains(".") ? null : label;
            }
        }
        return null;
    }
}
