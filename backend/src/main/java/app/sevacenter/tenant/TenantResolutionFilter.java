package app.sevacenter.tenant;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    private final TenantRepository tenants;
    private final boolean allowHeaderOverride;
    private final List<String> baseDomains;

    public TenantResolutionFilter(TenantRepository tenants, boolean allowHeaderOverride,
                                  List<String> baseDomains) {
        this.tenants = tenants;
        this.allowHeaderOverride = allowHeaderOverride;
        this.baseDomains = List.copyOf(baseDomains);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String slug = resolveSlug(request);
            if (slug != null && !ReservedSlugs.isReserved(slug)) {
                tenants.findBySlug(slug).ifPresent(t -> TenantContext.set(t.getId()));
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
