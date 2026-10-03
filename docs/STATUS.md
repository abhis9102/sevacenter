# Dev Status — resume point

_Last updated: 2026-10-03 (M2 in progress)._

## Where we are

**M0 — foundation: ✅ complete.** Spring Boot 3.5.3 skeleton, secure-by-default Spring Security,
Flyway (V1 baseline), 12-factor config, hermetic Testcontainers tests, docker-compose Postgres,
Makefile, pre-commit guardrails, GitHub Actions CI, PR template + AI-declaration, CODEOWNERS,
CONTRIBUTING, SECURITY, ADRs 0001–0006, design system + styleguide. springdoc/OpenAPI wired up.

**M1 — auth, tenancy, RLS: ✅ complete (2026-10-03).**

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

- **Validated:** jar from the main run verifies (SLSA provenance: repo, `ci.yml`, `main`, commit;
  SBOM: 113 components). Tampered jar, wrong repo, wrong signer workflow → all rejected. Deploy (M6)
  must verify with `--signer-workflow … --source-ref refs/heads/main`, not repo alone.

**G5 — DAST: ✅ (this PR).**
- `tools/security/dast.sh` (CI + `make dast`): throwaway Postgres → built jar → ZAP 2.17 **active API
  scan** from the OpenAPI spec → **coverage check** (scan must create tenants). ~2 min.
- `dast_policy.py`: Medium+ fails, Low → ticket on main; `.zap/accepted.toml` entries must be scoped
  (rule + param/uri), reasoned, expiring. Invalid entries suppress nothing (bug caught in testing).
- `tickets.py dast`; `dast-tickets` job on main. Required check `DAST (ZAP)`.
- Findings fixed: XSRF cookie `SameSite=Strict`, `Cross-Origin-Resource-Policy: same-origin`, injected
  `CsrfToken` hidden from the spec. **"SQL injection" false positive** traced to HashMap ordering in
  validation errors → sorted map. First "clean" scan had tested nothing (0 tenants, all 403) → CSRF
  replacer + valid spec examples. All locked in `SecurityRegressionTest` (mutation-checked).
- Follow-up (G3): osv-scanner applies `osv-scanner.toml` entries even when our policy rejects them,
  so an invalid ignore hides the finding from code scanning (the job still fails). Make SCA scan
  without the config for reporting.

- **G5 validation (PR #14):** planted stack-trace leak and permissive CORS were **invisible to DAST**
  (well-formed JSON only; no Origin header; CORS rule not installed). Covered instead by real-server
  `ErrorDisclosureTest` and `CorsPolicyTest` (both mutation-checked). Side finding: Boot 4 ignores
  `server.error.*`, so the hardening was dead config → `spring.web.error.*`. ZAP now runs the full Default
  Policy (48 vs 23 active rules; `.zap/rules.tsv`), which found the Whitelabel page (→ JSON-only
  `ApiErrorController`) and `/error` direct = 500 (→ 404). One scoped/expiring accepted risk (Tomcat's
  bare 400 for malformed request lines).

## M1 slice 1 — tenant isolation proven ✅ (this PR)
- Tests now run like production: Flyway as owner, app as `sevacenter_app` (connection-details beans;
  dynamic properties applied too late for the real-server test context, which then silently
  reached the local dev DB).
- `TenantIsolationTest` (10 tests, threat-model invariants 1–4, all mutation-checked).
- Found + fixed: (1) **all tests had run as a superuser** (RLS untested); (2) **RLS policy crashed** on
  reused connections (`''::bigint`) → V3 `nullif`; (3) **repositories were never pinned** (aspect only hit
  our `@Transactional` classes) → `TenantPinningDataSource` pins on every checkout, `RlsTenantAspect`
  and the AspectJ starter removed; (4) **reserved subdomains registrable** → `ReservedSlugs`.
- Lesson: Maven doesn't delete stale resources in `target/`; a removed migration kept running until
  `clean`. Use `./mvnw clean verify` when deleting/renaming migrations.

## M1 slice 2a — staff login ✅ (this PR, ADR 0009)
- Per-tenant admin host; **Host allowlist** (`<slug>.<base-domain>` only; any other host resolves no
  tenant). HTTP Basic placeholder removed.
- Framework form login (JSON handlers) at `POST /api/v1/auth/login`: session fixation protection, CSRF
  rotation. `POST /api/v1/auth/logout`, `GET /api/v1/me`. `TenantBindingFilter` kills sessions replayed
  on another tenant's host. `LoginThrottle`: per-account + per-IP lockout, 429.
- `SC_SESSION` cookie: HttpOnly, Secure (off only in the local profile), SameSite=Lax, 30-min idle.
  Unauthenticated requests get JSON 401 (no Basic challenge). Method security + role hierarchy wired.
- Login published in the OpenAPI spec (`springdoc.show-login-endpoint`), so DAST attacks it; `/error`
  hidden from the spec. DAST: 171 URLs, clean.
- Bugs caught by tests before shipping: throttle matched the login URL by servlet path and **never
  fired** → same matcher as Spring Security; spring-security-test's `csrf()` swaps the shared CSRF
  repository, so MockMvc cookie checks were order-dependent → cookie flags tested over a real server.
- 42 tests; auth controls mutation-checked.

## M1 slice 2b — user management + setup links ✅ (this PR)
- `V4`: user lifecycle PENDING → ACTIVE → DISABLED (DB-checked: active ⇒ password); `user_setup_token`
  (SHA-256 only, forced RLS, 72 h, single use).
- `/api/v1/users`: list (LEADER+), create / change role / deactivate / reissue link (TRUST_ADMIN) via
  `@PreAuthorize` (**invariant 5 now tested**). `POST /api/v1/auth/setup` is public + CSRF. Link
  token goes in the URL fragment, so it's never logged or sent as Referer.
- Last-admin rule, counted under a row lock (deterministic lock test; a "race" test passed without it).
- `TenantBindingFilter` → `StaffSessionFilter`: also re-reads the user on every request, so
  deactivation ends sessions and role changes apply immediately.
- **Mutation testing: 22 mutations, 6 survived at first** — all defence-in-depth layers masked by
  another layer (reusable token ↔ PENDING check, RLS ↔ filter tenant check, missing token lock).
  Each now has a test that defeats the other layers. Lesson: a passing end-to-end test proves the
  stack holds, not that each layer does.
- 64 tests. Setup links are returned to the admin for now (no email yet).

## M1 slice 2c — authenticated DAST + authorization probe ✅ (this PR) — **M1 complete**
- `dast.sh` now has 3 stages: `authz_probe.py` (27 cross-tenant/role checks, real `Host` header) →
  ZAP logged in as TRUST_ADMIN → ZAP unauthenticated (G5). Gate reads both ZAP reports.
- Coverage checks: the session must survive the authenticated scan, and that scan must create users.
- **Caught a false-clean scan on its first run**: `zap-api-scan.py` splits `-z` on spaces, so
  `a=1; b=2` cookies lost the CSRF token → all 403, ZAP said 0 failures. Fixed.
- **Validated:** 4 planted flaws (missing `@PreAuthorize`, loose Host match, CSRF off, `USING (true)`
  RLS policy) → all caught, 11/27 checks red.
- DAST false positive (path traversal, `slug=register`) root-caused to scan ordering; fixed at the
  cause. Auth-flow names (`register`, `signup`, `password`, …) are now reserved slugs (phishing).
- Policy: `docs/security/dast.md`.

## M2 slice 1 — devotee records ✅ (this PR, ADR 0010)
- `V5`: `devotee` (forced RLS). Fields: name; optional phone (E.164), email, address, DOB; consent
  record (source required; time + recorder set by the server, immutable); `created_by`/`updated_by`.
- `/api/v1/devotees`: search/list/get (MEMBER+), create/update (LEADER+), erase (TRUST_ADMIN).
  **Members get masked data from the server** (phone last 4, email first letter, no address/DOB) and
  search by name only, so search can't be used to confirm a phone number the mask hides.
- Phones typed Indian-style are normalised to `+91…`; every DB check has a matching request rule
  (400, never a 500). `LIKE` wildcards escaped; page size ≤ 100; fixed sort.
- `DevoteeTest` (14) + `authz_probe.py` devotee rows. **Mutation-checked: 15/15** after adding 2
  tests for the first-round survivors (wildcards via the member query; `updated_by` on edit).
- `TestStaff` test fixture: tenants + staff of any role through the real flows. Its own login IP
  range: sharing `StaffAuthTest`'s range made 3 tests fail with 429 in the full suite only.
- **DAST found 2 real 500s** (gate green, but 8 ERROR lines in the app log): NUL bytes in text and
  Tomcat 11's `InvalidParameterException`. Fixed globally (`RequestSanityFilter`, `NulRejectingStrings`),
  tests written first and seen failing. Authenticated DAST must now also create devotees.

## M2 slice 2 — devotee CSV export/import ✅ (this PR)
- `GET /api/v1/devotees/export` + `POST /api/v1/devotees/import` (multipart): **TRUST_ADMIN only**
  (bulk PII). Export: `Cache-Control: no-store`, attachment, audit-logged (`audit` logger, no PII).
- **CSV/formula injection:** cells starting `= + - @ \t \r` get a leading `'` on export; import strips it,
  so export → import round-trips exactly.
- Import: same rules as the API (shared `normalise`), **all-or-nothing**, errors name line + field and
  never echo cell values; strict header (unknown/duplicate/missing columns → reject: no CSV mass
  assignment); consent required per row; Excel BOM handled; ≤ 5,000 rows, ≤ 2 MB (container limit).
- Apache Commons CSV 1.14.1 (new dependency, SCA-scanned) instead of a hand-rolled parser.
- NUL check moved into the shared write path, so CSV cells are covered too.
- `DevoteeCsvTest` (11) + oversize-upload real-server test; **mutation-checked 12/12**; probe +7 rows.
- DAST: "persistent XSS" on the CSV export reproduced → not exploitable as served; added an API-wide
  `CSP: default-src 'none'; sandbox` anyway, then a scoped accepted risk. Three 500s fixed (bad
  multipart → 400, oversize → 413, concurrent delete → 409). 96 tests.

## Fix: login throttle behind a proxy ✅ (#23)
- Found while connecting the frontend: behind any proxy (Next dev proxy, the M6 load balancer)
  every login came from the proxy's IP, so 5 wrong passwords from anyone locked out everyone.
- Now: `server.forward-headers-strategy: native` + `TRUSTED_PROXIES` (loopback by default; LB
  subnets in M6). Only a trusted peer's `X-Forwarded-For` counts, rightmost hop first (unspoofable).
  Per-IP limit raised to 50 (shared carrier NAT); per-account stays 5. Mutation-checked.

## Delete staff (this PR)
- `DELETE /api/v1/users/{id}` (TRUST_ADMIN, not yourself) + a Delete button with a confirm dialog.
- A PENDING invitation is removed outright. Anyone who could have acted is **tombstoned** (`V7`:
  `deleted_at`; email freed, no password, DISABLED): signed out on the next request, gone from the
  list, can't log in; the display name stays so records they made still say who made them.
  Mutation-checked 7/7; probe +2 rows.

## Frontend track (started 2026-10-03)
- Next.js + TS admin app (`frontend/`), being built in parallel: login, account setup, staff,
  devotees + CSV, design-system theme, strict CSP. Same-origin `/api` proxy; `<slug>.localhost`
  locally.
- Gates to add with it: Semgrep TS/React/Next rules, `npm ci --ignore-scripts`, `npm audit
  signatures`, SCA coverage for `package.json`, ZAP baseline on the UI.

## M3.1 — donation ledger ✅ (this PR, ADR 0011)
- `V6`: `donation` (forced RLS), signed paise, CHECK ties negative amounts to reversals, `UNIQUE (reverses_id)`.
  **App role granted only `SELECT, INSERT`**: the ledger is append-only at the database.
- `/api/v1/donations`: record/list/get/summary (LEADER+), reverse (TRUST_ADMIN, reason required).
  MEMBER: no access. Amounts are decimal strings → exact paise. FY in IST (1 Apr – 31 Mar).
- Devotee erasure: anonymised (row kept, PII removed, `erased_at`) when donations reference them.
- Audit log lines for record/reverse. `DonationTest` (13), probe +14 checks.

## M3.2 — 80G receipts + PAN protection ✅ (this PR, ADR 0012)
- `V8`: `trust_profile`, `receipt_counter`, `receipt`, `receipt_cancellation` (all forced RLS; receipts
  insert-only). `PUT/GET /api/v1/trust-profile`, `POST /api/v1/donations/{id}/receipt`, `GET /api/v1/receipts[/{id}]`.
- **Donor PAN:** AES-256-GCM with the tenant bound as associated data + HMAC blind index under a
  second key; DB never sees it in clear. Keys `SEVACENTER_PAN_KEY` / `SEVACENTER_PAN_INDEX_KEY` from env:
  the app refuses to start without real ones (`.env.example`; `dast.sh` generates per-run keys).
- **Receipt numbers** `2026-27/000123`: gapless per trust per FY under concurrency (upsert row lock).
- 80G rules: no cash > ₹2,000 (80G(5D)), one per donation, none for reversals, registration valid on
  the donation date. Reversal cancels the receipt (number never reused). Trust/donor snapshots.
- `ReceiptTest` (12) + `PanProtectionTest` (6).

## Review batch: receipts, profile/avatar/Hindi, registration, password reset (this PR)
Work from parallel sessions, reviewed before merge. Fixed in review:
- **Critical:** `/forgot-password` returned the reset token in the response whenever the Spring profile
  was `local`/`test`/**`default`** (production without a profile) → account takeover of any staff.
  Now: no token-returning endpoint; admin-issued reset links (Staff → "Reset password link").
- Reset links die on deactivate/delete; redeeming needs an ACTIVE user (each layer mutation-tested).
- Avatar bytes no longer load with every request (`UserAvatar`); change-password throttled like login;
  no "opt out of the activity log"; open redirect in the portal box; lint errors that would fail CI;
  Flyway `out-of-order` only in the local profile.

**Held back (not merged): devotee portal** (`4ebe069`, still on `feat/m3-80g-receipts`):
- **Critical:** public `POST /portal/donations` writes any amount/mode to the ledger with an "official
  receipt", no payment and no login. **Critical:** `/portal/auth/register` creates a devotee and a
  session for any phone/email **without OTP verification**.
- V12 drops `NOT NULL` on staff attribution (`recorded_by`, `issued_by`, ...) for all paths; OTPs can be
  requested without limit (brute force); no SMS/email delivery, so it only works via the dev flag.
- Rebuild with M3.3 (Razorpay, payment verified server-side), real OTP delivery + throttling,
  verified registration, attribution kept NOT NULL for staff paths (`created_via` instead).

## M3.3 — online donations via Razorpay ✅ (this PR, ADR 0013)
- **Each trust's own Razorpay account** (money never passes through the platform). TRUST_ADMIN connects
  key id + secret on **Payments**; the secret is verified with Razorpay, AES-GCM encrypted under
  `SEVACENTER_SECRETS_KEY`, write-only.
- Public **/donate** page on the trust's host: server-side order → Razorpay Checkout → confirm =
  signature (constant time) **and** payment re-fetched from Razorpay (captured, same order, exact
  amount, INR) → one ONLINE ledger entry, idempotent. Reconciliation recovers closed-browser payments.
- `V12`: `payment_settings`, `payment_intent` (RLS); `donation.channel` + `payment_ref` with a CHECK
  that keeps staff attribution mandatory; `WALLET` mode.
- Tests: `OnlineDonationTest` (13, fake gateway), `RazorpayGatewayLiveTest` (opt-in, real test mode, passes);
  mutation-checked 12/12 (one survivor found first: order match, masked by amount match).

## M4 slice 1 — events with registration passes ✅ (this PR, ADR 0014)
- **MVP scope extended (user decision, 2026-10-03):** the MandirCenter operations set from the parallel
  session (public temple page, events/darshan passes, pujas, sevak signups) is MVP; rebuilt slice by
  slice with reviews. See `docs/roadmap.md`.
- `V13`: `event`, `event_pass` (forced RLS, no DELETE). Staff **Events** screen (create/publish/cancel,
  registrations, gate check-in); public **/upcoming** page on the trust host returns a pass code.
- Capacity under row lock; 50-bit pass codes; check-in once; gate sees no contacts; rate-limited.
- `EventTest` (12), **mutation-checked 13/13**; probe +9 rows.

## Sevak signups ✅ (this PR, ADR 0015)
- `V14`: `sevak_signup` (forced RLS, no DELETE). Public **/sevak** form; staff **Volunteers** screen
  (LEADER+, approve/decline). `SevakTest` (6), mutation-checked 6/6; probe +4 rows.

## Pujas ✅ (this PR, ADR 0016)
- `V15`: `puja`, `puja_booking` (forced RLS, no DELETE); `payment_intent.kind` DONATION|PUJA.
- **Dakshina is seva income, never a ledger donation** (no 80G receipt possible); DB CHECKs enforce it.
- Public **/book-puja** (Razorpay Checkout for paid pujas; free ones confirmed at once); staff **Pujas**
  screen: day's schedule (priest sees no contacts, marks performed), catalog, cancel (LEADER+).
- `PujaTest` (11), mutation-checked 12/12 (incl. "puja payment into the ledger").

## Public temple page ✅ (this PR, ADR 0017)
- `V16`: `temple_profile` (forced RLS). **`/` on a trust host is now the public temple page**: the
  temple's own timings/announcement/contact (no placeholder data) and links only to services that
  exist (donate, events, pujas, seva). Staff **Temple page** editor (LEADER+). `TempleTest` (4),
  mutation-checked 4/4.

## Devotee login + "my seva" ✅ (this PR, ADR 0018)
- `V17`: `devotee_otp` (HMAC'd contact + code, never the values), `devotee_account` (created only on
  verification), `devotee_session` (SHA-256 of a 256-bit token). All forced RLS, no DELETE.
- One-time codes by **email** (SMTP: Mailpit locally via `make db-up`, SES in M6) and **SMS** (MSG91;
  needs `MSG91_AUTH_KEY` + `MSG91_OTP_TEMPLATE_ID`, offered only when set). New key `SEVACENTER_OTP_KEY`.
- 10-min codes, single use, 5 guesses; 3/15 min + 10/day per contact, 20/15 min per IP, daily
  per-trust caps; SMS only to Indian mobiles; same answer for every contact.
- `SC_DEVOTEE` cookie (HttpOnly, `Path=/api/v1/portal`) is not a Spring Security login: no staff
  API reachable with it, and no portal API with a staff session. Public **/my-seva** page.
- `DevoteeLoginTest` (15), **mutation-checked 18/18** (found first: dead "supersede" code, removed; expiry untested, added).
- **Local DAST found:** the mail health indicator made `/actuator/health` DOWN whenever SMTP was unreachable (an SES outage would have taken every task out of the load balancer), and JavaMail had no timeouts. Mail health is off, SMTP timeouts are 5/10 s; regression test added. Probe +5 rows.

## Donation history + receipts in "my seva" ✅ (this PR, ADR 0019)
- `V18`: optional donor phone/email on `payment_intent` (the donate page asks, never requires).
- "My seva" lists the verified contact's donations: online by the checkout contact, staff-recorded via
  the linked devotee; reversed ones marked. Receipt copies with the donor PAN masked, own donations only
  (others' 404, no session 401, `no-store`). Public **/donate** gains optional mobile/email.
- `DonationHistoryTest` (6), **mutation-checked 11/11** (found first: cancelled receipts untested, added); probe +1 row.

## Audit log ✅ (this PR, ADR 0020)
- `V19`: `audit_log`, append-only (app role: SELECT/INSERT only), forced RLS, no free text.
- `AuditTrail` writes in the **same transaction** as the action (`MANDATORY`): rolled-back actions leave
  no entry, a failed entry rolls the action back; actor from the session. Covers user management (which
  had no record at all before), devotee create/update/erase/import/export, donations, reversals,
  receipts, trust profile, payment settings, events, pujas, sevak reviews, temple page.
- TRUST_ADMIN **Audit log** screen (filter by action). `AuditTest` (8), mutation-checked 10/11 + 1
  equivalent (found first: the same-transaction guarantee was untested, and a second annotated entry
  point could bypass it; removed); probe +4 rows.

## Module access limits ✅ (this PR, ADR 0021)
- `V20`: `app_user.module_limits` (DB CHECK on format). Per user, per module (Devotees, Donations,
  Events, Pujas, Volunteers, Temple): VIEW (read-only) or NONE. **Only narrows the role**; admins are
  never limited (API refuses, promotion clears, stored limits ignored).
- `ModuleAccessInterceptor` enforces it on every API call; one prefix table; `ModuleCoverageTest` fails
  if any endpoint is unmapped. Applies on the next request (principal rebuilt from the DB). Audited.
- Staff screen "Access" editor; nav hides closed modules. `ModuleAccessTest` (7) + coverage (2),
  **mutation-checked 11/11** (found first: stored limits on an admin were only ignored by accident of
  promotion clearing them; now tested directly); probe +7 rows.
- Replaces a UI-only proposal that hid menus while every API stayed open.

Next (user decisions 2026-10-04): donation funds → staff dashboard → dark mode + logo.

**MVP features are now complete.** Then: M5 containers → M6 AWS → M7 gates.

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
- ~~Per-tenant admin subdomain vs single `app.sevacenter.app`~~ → decided: per-tenant (ADR 0009).
