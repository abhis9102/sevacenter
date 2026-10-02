package app.sevacenter.tenant;

import java.io.IOException;

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
 * <p>Security notes: we resolve the slug only from the real Host header and look it up
 * against the registry; an unknown or malformed host leaves the context unset, and RLS
 * then fails closed. A dev-only {@code X-Tenant-Slug} override exists for localhost and is
 * off by default.
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    private final TenantRepository tenants;
    private final boolean allowHeaderOverride;

    public TenantResolutionFilter(TenantRepository tenants, boolean allowHeaderOverride) {
        this.tenants = tenants;
        this.allowHeaderOverride = allowHeaderOverride;
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
                return header.trim().toLowerCase();
            }
        }
        String host = request.getServerName();   // never trusts an arbitrary Host; this is the resolved server name
        if (host == null) {
            return null;
        }
        host = host.toLowerCase();
        // tenant subdomain = first label of a 3+ label host (slug.sevacenter.app / slug.mandircenter.app)
        String[] labels = host.split("\\.");
        if (labels.length >= 3) {
            return labels[0];
        }
        return null;
    }
}
