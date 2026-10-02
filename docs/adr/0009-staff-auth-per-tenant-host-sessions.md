# ADR-0009: Staff authentication — per-tenant admin host, server sessions, setup links

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

M1 slice 2 adds staff login. Three choices shape the auth surface: where staff log in, how new
staff accounts are created before we have an email service, and how much brute-force protection
ships with the first login endpoint.

## Decision

1. **Per-tenant admin host:** staff log in at `<slug>.sevacenter.app`. The tenant comes from the
   Host, exactly as on MandirCenter (`<slug>.mandircenter.app`), so there's one tenant-resolution
   path. A user exists only inside their tenant (RLS), so `findByEmail` on tenant A's host can
   never find tenant B's user. The browser scopes the session cookie to that host.
2. **Host allowlist:** a tenant is resolved only from `<slug>.<base-domain>`, where base domains
   are configured (`sevacenter.app`, `mandircenter.app`). Anything else resolves no tenant.
3. **Server-side sessions + CSRF** (per ADR 0007), using Spring Security's form login with
   JSON handlers, so session-fixation protection, CSRF token rotation and context persistence
   come from the framework rather than hand-written code. Each authenticated request is checked
   against the Host's tenant: a session from tenant A presented on tenant B's host is rejected.
4. **Onboarding via one-time setup links** (slice 2b): an admin creates a user and receives a
   single-use, expiring link to hand over; the user sets their own password. Email delivery later.
5. **Basic brute-force protection now:** per-account and per-IP failure throttling with a
   temporary lockout (HTTP 429), the same generic 401 whether or not the email exists, and
   constant-time password checks for unknown users. Full rate limiting in M4.

## Consequences

- Wildcard DNS/TLS for `*.sevacenter.app` in M6 (already planned for MandirCenter).
- A person working for two trusts has two accounts (one per tenant): accepted.
- Throttling and sessions are in-memory: fine for one instance; move to a shared store
  (Spring Session JDBC / Redis) before running more than one task in M6.
- Login is CSRF-protected (login CSRF); the token rotates after login, so the SPA re-fetches it.
