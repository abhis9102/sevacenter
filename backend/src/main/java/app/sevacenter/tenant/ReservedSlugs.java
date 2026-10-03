package app.sevacenter.tenant;

import java.util.Set;

/**
 * Subdomains that can never belong to a tenant (threat model invariant 4). One list, used by
 * both registration (refuse them) and routing (never resolve them), so the two can't drift.
 */
public final class ReservedSlugs {

    private static final Set<String> RESERVED = Set.of(
            "www", "app", "api", "admin", "mail", "static", "assets", "cdn",
            "auth", "login", "status", "docs", "help", "support", "security", "billing",
            // Auth-flow names: tenant hosts serve login pages, so these would be ready-made
            // phishing hosts (found via a DAST false positive on slug=register).
            "register", "signup", "signin", "logout", "account", "accounts", "password",
            "reset", "verify", "setup", "sso", "oauth");

    private ReservedSlugs() {
    }

    public static boolean isReserved(String slug) {
        return slug != null && RESERVED.contains(slug.toLowerCase());
    }
}
