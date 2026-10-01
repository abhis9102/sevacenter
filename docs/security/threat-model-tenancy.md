# Threat Model: Tenancy, Registration & Auth (M0/M1)

STRIDE threat model for SevaCenter's foundation — the per-temple registration/subdomain flow,
multi-tenancy, and authentication. Written at M0 (before building M1) so the design is shaped
by it, not patched after. Revisit each milestone.

## Assets

- Devotee PII; donation/payment records; donor PAN (80G).
- Tenant isolation boundary (the core security property).
- Admin/leader credentials and sessions.

## Trust boundaries

- Internet → ALB/app (public). Tenant identity derived from the request **Host** (subdomain).
- App → Postgres (RLS boundary). App → Razorpay (external).
- Role boundaries within a tenant: `trust-admin` > `leader` > `member/donor`.

## STRIDE

| Threat | Example in SevaCenter | Mitigation |
|---|---|---|
| **Spoofing** | Forged/ambiguous `Host` header to act as another tenant; subdomain takeover of a dangling tenant | Canonical Host validation + allowlist of provisioned tenants; no wildcard fallthrough; monitor dangling DNS |
| **Tampering** | Manipulating `tenant_id`, IDs, or donation amount in requests | Server-side authz; never trust client IDs; **RLS** enforces tenant scope; server-side payment amounts |
| **Repudiation** | Admin denies a sensitive action (refund, data export) | Audit log of security-relevant actions (who/what/when/tenant) |
| **Information disclosure** | Cross-tenant data read via a query missing `tenant_id`; tenant enumeration via subdomain/login responses; secrets in JS bundle | RLS as the backstop; uniform responses to avoid enumeration; secret scanning of bundle |
| **Denial of service** | Registration/donation/login abuse; expensive endpoints | Rate limiting; captcha on public forms; pagination/query caps |
| **Elevation of privilege** | `member` → `trust-admin`; a DB role with `BYPASSRLS`; reserved subdomain (`admin`, `api`) | `@PreAuthorize` method authz; app DB role is **non-superuser, non-BYPASSRLS**; reserved-subdomain blocklist |

## Key invariants (become tests)

1. Every DB connection has `app.tenant_id` set before any tenant-scoped query runs.
2. The application's DB role **cannot** bypass RLS.
3. No endpoint returns data for a tenant other than the caller's — verified with a two-tenant test.
4. Reserved subdomains (`www`, `api`, `admin`, `mail`, …) cannot be registered by a tenant.
5. Authorization is enforced server-side even when the UI hides the control.

## Open questions

- Public donor access: own login vs. link/OTP (affects the auth surface).
- Custom domains (wave 2): per-tenant cert issuance + domain-ownership verification.
