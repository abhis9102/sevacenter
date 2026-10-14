# 0036 — MandirCenter on a temple's own domain

- **Status:** Accepted (2026-10-15)
- **Deciders:** AppSec (Abhi); built by Claude
- **Relates to:** ADR 0009 (tenant from the host), ADR 0030 (two brand domains), ADR 0032 (DNS
  Firewall), ADR 0034 (edge, on-demand TLS)

## Context

Every temple's public site lives at `<slug>.<mandir domain>`. Temples want their own address
(`www.shrisiddhivinayak.org`). Serving a domain we don't control raises four questions: who owns
it, which trust it belongs to, when the edge may get a certificate for it, and what it may serve.

## Decision

1. **Ownership is proven in DNS.** The trust admin adds the domain and gets a random token; the
   domain activates only when `TXT _sevacenter.<domain>` holds it. Several trusts may *claim* a
   name; only the proof activates one, and an active name can't be taken over (one owner, enforced
   by the registry's primary key).
2. **Two tables.** `custom_domain` (RLS) holds each trust's claims and tokens. `tenant_domain` is
   the routing registry (verified hostname → trust), read before any tenant is known: anyone may
   read it (its data is public: it's in DNS), and forced RLS lets a trust insert or delete only its
   own routes.
3. **Public pages only.** A custom host resolves a trust only for `/api/v1/public/**`,
   `/api/v1/portal/**` and `/api/v1/csrf`; staff pages 404 on it and the staff API resolves no
   trust. No staff session cookie ever lives on a domain we don't control.
4. **Certificates only for verified names.** Caddy's on-demand TLS `ask` allows our base domains,
   and otherwise asks the backend, which says yes only for active custom domains.
5. **Daily re-check** (10:15 IST, while staging is awake): an active domain whose TXT proof is gone
   is disconnected, so a domain that changed hands or a dangling CNAME stops serving the temple.
6. **DNS over HTTPS for the checks.** The DNS Firewall answers only allowlisted names, so the
   app can't resolve customers' domains; it asks `cloudflare-dns.com` (allowlisted) over HTTPS.
7. Hostnames are normalised (lowercase, punycode, no trailing dot) and must be public: not ours,
   not an IP or `localhost`, not a reserved TLD (`.local`, `.internal`, `.example`…). At most 3 per
   trust; managing them is TRUST_ADMIN only, audited, and the check is rate-limited.

## Consequences

- A temple can use its own domain with two DNS records (CNAME, or A for an apex domain, plus TXT).
- The frontend treats any public non-our host as a possible custom domain; the backend decides,
  and the edge never gets a certificate for an unverified one.
- A new trust boundary: X-Tenant-Host from the frontend proxy, trusted like X-Tenant-Slug only
  behind it (the client can't send it: the proxy forwards an allowlist of headers).
- Tests: `CustomDomainTest` (proof required, public pages only, no takeover, refused names,
  disconnect on lost proof, admin only), mutation-checked: resolving every path on a custom host
  and activating without proof each fail it.
