# Dev Status — resume point

_Last updated: 2026-10-02 (late session)._

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

## Security-gate track
**G1 — harden CI: ✅ (PR #1).** Repo is public at github.com/abhis9102/sevacenter.
- Actions SHA-pinned (verified by zizmor's online impostor-commit audit), `permissions: {}` +
  per-job `contents: read`, `persist-credentials: false`, timeouts, gitleaks gets `GITHUB_TOKEN`.
- New `workflow-lint` job (zizmor, pass/fail, no SARIF yet). Old workflow had 6 findings → 0.
- Dependabot (`github-actions`, weekly, 7-day cooldown).
- Repo: secret scanning + push protection, Dependabot alerts, private vuln reporting, squash-only,
  auto-delete merged branches.
- Ruleset `protect-main` (id 24377427): PR required (0 approvals — solo; can't self-approve),
  3 required checks + up-to-date, linear history, no force-push/deletion, **no bypass actors**.
  Proven: direct push to main → `GH013` rejected.
- Pre-commit hooks installed locally (were never installed); first-run trufflehog false positives
  triaged per line.

**G2 — SAST + tickets: ✅ (PRs #2, #4).**
- `sast` job: Semgrep (same rulesets as pre-commit), digest-pinned image via `docker run` (Alpine
  can't be a job container for JS actions) → SARIF → code scanning. Reports only.
- Ruleset now also requires `SAST (Semgrep)` + a **code_scanning rule** (Semgrep OSS: errors /
  high+ security alerts block merge).
- `tools/security/tickets.py` (stdlib only) + policy `docs/security/ticketing.md`: secret → critical
  ticket immediately, human-closed only; SAST on main → ticket, auto-close/reopen. Weekly rescan.
- **Gate validation exercise (PR #3, closed unmerged), red-teaming our own pipeline:** hook blocked Stripe key → `--no-verify` → push protection
  blocked it → in-house secret passed push protection → CI gitleaks failed → ticket #5 auto-created
  (redacted) → SQLi alerts blocked merge → fix commit auto-fixed alerts but secret still in history →
  squash branch → green → **rejected in manual review** (cross-tenant tenant enumeration — invisible
  to SAST). Ticket #5 closed by hand with rotation note.
- Bugs found by the exercise: ticket job assumed SARIF at artifact root (fixed, PR #4); re-runs reuse the
  original workflow definition → update the PR branch instead.
- Known limitation: secret-ticket dedup key includes the commit SHA, so a **rebase** of a leaking
  branch can open a duplicate ticket. Fine for now; revisit if it gets noisy.

**G3 — SCA + Spring Boot 4.1 migration: ✅ (this PR).**
- Baseline on Boot 3.5.3: **82 vulns (9 critical)**, and **Boot 3.5 is EOL since 2026-06-30** →
  migrated to **Boot 4.1.1** (ADR 0008 supersedes 0005). Starter renames, `starter-flyway` (bare
  flyway-core silently skips migrations in Boot 4), Testcontainers 2, springdoc 3, Security 7.
  Behavior-preserving: all live checks identical. 3 patch-level CVE overrides (Tomcat, Jackson 2/3).
- `sca` job: osv-scanner (digest-pinned) → SARIF → code scanning; exits other than 0/1 fail.
  Ruleset blocks **high/critical**. Daily schedule (CVEs land daily).
- `tools/security/sca_policy.py`: **coverage** (every declared dep resolved to a version; catches
  the "0 vulns because 15/183 packages resolved" false-clean we hit) + **ignores** (reason + ≤ 90-day
  expiry on every accepted risk in `osv-scanner.toml`).
- Dependabot: maven version updates (7-day cooldown, 30 for majors) + security updates enabled.
- Tickets: `code-scanning-tickets` now syncs SAST + SCA alerts on main (`sca` label).
- Policy: `docs/security/sca.md`.
- **Gate validation exercise (PR #8, closed unmerged):** `commons-text` 1.9 (CVE-2022-42889, CVSS
  9.8) → **gate failed open**: alert raised but PR mergeable, because GitHub's code scanning merge check
  only blocks alerts on changed lines and osv-scanner reports at `pom.xml:1` → fixed in PR #9
  (`sca_policy.py gate`, CVSS ≥ 7 or unscored fails the required SCA job) → re-run blocked. Ignore
  without reason/expiry → rejected. Justified 14-day acceptance → allowed. Non-existent (agent-
  invented) dependency → build fails + osv-scanner exit 127. Upgrade to 1.15.0 → alert auto-fixed.
- Open: a **registered** malicious look-alike package would pass build + SCA (no CVEs yet). That's
  the M7 package-reputation check. Optional `pre-push` osv-scanner hook for earlier feedback.

**G4 — SBOM + attestations: ✅ (this PR).**
- `cyclonedx-maven-plugin` (pre-configured by the Boot parent): CycloneDX 1.6 from Maven's real
  resolution, embedded in the jar. 113 runtime components, all with purl/version/hashes/licenses.
  `/actuator/sbom` deliberately unexposed.
- `build` uploads jar + SBOM; new required `sbom` job: validate, scan shipped components, CVSS ≥ 7
  gate, **drift** vs the G3 scan (by full group:artifact; artifactIds collide). Today: 0 drift.
- `attest` job (main only, signs but never builds): SLSA provenance + SBOM attestations for the jar
  via `actions/attest` (keyless Sigstore). Least privilege: no `artifact-metadata` (registry-only).
- Policy: `docs/security/sbom.md`. `tools/security/sbom_check.py` tested on pass + fail cases.

**Next: G4 validation (verify attestation; tampered jar must fail), then M1 `TenantIsolationTest`.**

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
