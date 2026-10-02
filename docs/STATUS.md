# Dev Status — resume point

_Last updated: 2026-10-02 (evening session)._

## Where we are

**M0 — foundation: ✅ complete.** Spring Boot 3.5.3 skeleton, secure-by-default Spring Security,
Flyway (V1 baseline), 12-factor config, hermetic Testcontainers tests, docker-compose Postgres,
Makefile, pre-commit guardrails, GitHub Actions CI, PR template + AI-declaration, CODEOWNERS,
CONTRIBUTING, SECURITY, ADRs 0001–0006, design system + styleguide. springdoc/OpenAPI wired up.

**M1 — auth, tenancy, RLS: 🚧 in progress. Slice 1 ✅ runs end to end (2026-10-02).**

### M1 slice 1 — done and verified running
- `V2__tenancy_and_users.sql`: `tenant` (registry, no RLS) + `app_user` (RLS **enabled + forced**,
  fails closed when `app.tenant_id` unset). Entities/repos, `TenantContext`, `TenantResolutionFilter`,
  `RlsTenantAspect`, `POST /api/v1/register`, `GlobalExceptionHandler`, bcrypt `PasswordEncoder`.
- Two-role DB: Flyway as owner, app as least-privilege `sevacenter_app` (`docker/db-init/`).
- **First run found two bugs, both fixed:**
  1. CSRF (Spring default) blocked `/register`. Decision → **ADR 0007: session cookies + CSRF on**,
     SPA double-submit (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header); new `GET /api/v1/csrf`.
  2. `/error` wasn't public, so every 403/400 surfaced as a misleading **401**. Now permitted.
- `make run` now loads `.env` (Spring doesn't read it on its own).
- **Verified manually:** no token → 403; wrong token → 403; valid → 201; duplicate slug → 409;
  bad body → 400. In the DB: hash stored as `{bcrypt}`; as `sevacenter_app` — no tenant → 0 rows,
  tenant 1 → 1 row, tenant 2 → 0 rows. App role is `NOSUPERUSER`, `NOBYPASSRLS`. `make test` green.

Register locally (tenant via `X-Tenant-Slug` header since localhost has no subdomain):
```bash
make db-reset && make run
curl -s -c /tmp/jar localhost:8080/api/v1/csrf          # -> {"token": "..."} + XSRF-TOKEN cookie
curl -s -b /tmp/jar -X POST localhost:8080/api/v1/register -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: <token>" \
  -d '{"slug":"siddheshwar","trustName":"Shri Siddheshwar Seva Trust",
       "adminEmail":"priya@example.org","adminPassword":"change-this-please-123","adminName":"Priya"}'
```

### ⚠️ Next — do first
1. **`TenantIsolationTest`** (AppSec deliverable) — automate the manual RLS check above. Testcontainers'
   default user is a **superuser and bypasses RLS**, so the test must connect as a non-superuser role.
   Also add CSRF tests (no token / bad token → 403).
2. **CI gate 1 — harden existing CI** (see roadmap "Security-gate track"): pin actions to SHAs,
   branch protection + required checks. Abhi writes the YAML; Claude explains + reviews.

## Next up — M1 slice 2
- Login + sessions (cookie session per ADR 0007; set cookie flags HttpOnly/Secure/SameSite); tenant-aware `UserDetailsService` (scope lookup by `TenantContext`).
- `@PreAuthorize` role enforcement (TRUST_ADMIN/LEADER/MEMBER); method security.
- `GET /api/v1/me`; user management endpoints (list users — also proves RLS end to end).
- Resolve tenant from the authenticated user on the admin host (not only from subdomain).
- Update `docs/security/threat-model-tenancy.md` invariants into real tests.

## Then
M2 Devotees → M3 Donations+80G → M4 Events → M5 containerize → M6 Terraform/AWS → M7 full CI
security gates. See `docs/roadmap.md`.

## Open product questions (non-blocking)
- Diya vs lotus logo mark. Any MandirCenter colour too strong (see styleguide artifact).
- Per-tenant admin subdomain (`slug.sevacenter.app`) vs single `app.sevacenter.app` — leaning
  per-tenant for uniform Host-based tenant resolution; revisit when building slice 2 login.
