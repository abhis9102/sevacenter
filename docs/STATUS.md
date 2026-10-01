# Dev Status — resume point

_Last updated: 2026-10-02, end of session._

## Where we are

**M0 — foundation: ✅ complete.** Spring Boot 3.5.3 skeleton, secure-by-default Spring Security,
Flyway (V1 baseline), 12-factor config, hermetic Testcontainers tests, docker-compose Postgres,
Makefile, pre-commit guardrails, GitHub Actions CI, PR template + AI-declaration, CODEOWNERS,
CONTRIBUTING, SECURITY, ADRs 0001–0006, design system + styleguide. springdoc/OpenAPI wired up.

**M1 — auth, tenancy, RLS: 🚧 in progress (slice 1 written, compiles, NOT yet run).**

### M1 slice 1 — done (code compiles via `./mvnw test-compile`)
- `V2__tenancy_and_users.sql`: `tenant` (no RLS, the registry) + `app_user` (RLS **enabled + forced**,
  policy on `current_setting('app.tenant_id')`, fails closed when unset). CRUD granted to the
  least-privilege role.
- Entities/repos: `Tenant`, `AppUser` (+ `Role` enum: TRUST_ADMIN/LEADER/MEMBER), repositories.
- Tenancy plumbing: `TenantContext` (thread-local), `TenantResolutionFilter` (Host subdomain →
  tenant, runs before Spring Security), `RlsTenantAspect` (sets `set_config('app.tenant_id', …, true)`
  per transaction), `TenancyConfig` (`@EnableTransactionManagement(HIGHEST_PRECEDENCE)` + filter reg).
- Registration: `POST /api/v1/register` (create trust + first TRUST_ADMIN, bcrypt hash) — service,
  controller, DTOs, `GlobalExceptionHandler` (400 validation / 409 slug taken).
- Security: `PasswordEncoder` (delegating/bcrypt) bean; `/api/v1/register` permitted; everything else
  still authenticated (HTTP Basic placeholder).
- Two-role DB: `docker/db-init/01-app-role.sh` creates `sevacenter_app` (non-superuser); `local`
  profile (`application-local.yml`) runs Flyway as owner, app as `sevacenter_app`; `.env.example`,
  `docker-compose.yml`, Makefile (`run` uses local profile, new `db-reset`) updated.

### ⚠️ Not yet done / verify first in the morning
1. **Run it** — this slice has only been compiled, never started. First morning task:
   ```bash
   cd ~/sevacenter && cp -n .env.example .env   # set DB_PASSWORD + DB_APP_PASSWORD
   make db-reset && make run
   ```
   Then register a tenant (local uses the `X-Tenant-Slug` header since localhost has no subdomain):
   ```bash
   curl -s -X POST localhost:8080/api/v1/register -H 'Content-Type: application/json' \
     -d '{"slug":"siddheshwar","trustName":"Shri Siddheshwar Seva Trust",
          "adminEmail":"priya@example.org","adminPassword":"change-this-please-123","adminName":"Priya"}'
   ```
   Expect `201`. Likely iteration points: Flyway owner vs app-role grants, the RLS aspect transaction
   ordering, and the `set_config` timing in `RegistrationService`.
2. **Tenant isolation test** — NOT written yet. Add `TenantIsolationTest` that proves a user in
   tenant A cannot read tenant B's rows. Note: Testcontainers' default DB user is a **superuser and
   bypasses RLS**, so the test must create/use a **non-superuser** role (raw JDBC) to prove the policy.
   This is the key AppSec deliverable for M1.

## Next up — M1 slice 2
- Login + sessions; tenant-aware `UserDetailsService` (scope lookup by `TenantContext`).
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
