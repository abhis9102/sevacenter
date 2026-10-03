# DAST (dynamic testing) policy

Gate **G5**. Every other gate reads code or dependencies. DAST **attacks the running app** over HTTP
the way an outsider would, and catches what only exists at runtime: headers, cookies, error
handling, exposed endpoints, injection that really executes.

## How it runs

| Piece | What |
|---|---|
| `tools/security/dast.sh` | Throwaway Postgres (same least-privilege init as dev) → the **built jar** (the one G4 signs) → OWASP ZAP **API scan, active, full Default Policy** (via `.zap/rules.tsv`), driven by our OpenAPI spec → coverage check. Same script in CI and locally (`make dast`). ~4 min. |
| `.zap/rules.tsv` | Passing any `-c` file switches `zap-api-scan.py` from **API-Minimal (23 active rules)** to the **Default Policy (all 48 installed)**. Without it we were running the minimal set. |
| `dast` job (required check) | Runs the script on every PR, push and daily; uploads `dast-report` (HTML + JSON + app log). |
| `tools/security/dast_policy.py gate` | **Medium/High → fail.** Low → warning (and a ticket on main). Informational → ignored. |
| `.zap/accepted.toml` | Accepted risks: scoped to rule **and** param/uri, reason ≥ 20 chars, expiry ≤ 90 days. |
| `dast-tickets` job (main) | Low+ unaccepted findings → tickets; auto-close when a later scan no longer finds them. |
| `SecurityRegressionTest` | Every DAST finding we fix becomes a test, so it can't silently regress. |

Active scanning sends real attack payloads, so it **only ever targets this ephemeral environment**,
never a shared or production deployment.

## Coverage: a scan that only sees 403s tests nothing

The first scan reported 115 passes and looked clean. The database showed it had created **zero
tenants**: every attack on `POST /register` stopped at the CSRF filter. Two fixes:
- ZAP attaches a valid CSRF cookie + header to every request (replacer rules), like a real client.
- The OpenAPI spec carries **valid example values**, so each attack varies one field while the
  others pass validation and the request reaches the database.

`dast.sh` now **fails** if the scan created no tenants. A green DAST run proves the attacks got past
CSRF and validation to the code that writes to the database.

## Findings from the first scans (all fixed, all covered by tests)

| Finding | Root cause | Fix |
|---|---|---|
| CSRF cookie without `SameSite` | Spring default | `SameSite=Strict` (cookie still readable by our SPA, ADR 0007) |
| No `Cross-Origin-Resource-Policy` | not configured | `same-origin` |
| `/api/v1/csrf?token=` in the spec | springdoc documented the injected `CsrfToken` as a query param, inviting tokens in URLs | `@Parameter(hidden = true)` |
| "SQL Injection" (High) on all register fields | **False positive.** Validation errors were built in a `HashMap`, so identical requests returned fields in different orders; ZAP's boolean test saw different bodies | Sorted map: identical requests now get byte-identical responses |

Triage rule: **reproduce before you believe or dismiss.** The SQLi alert was replayed by hand,
proved false, and still produced a real fix (deterministic responses).

## Gate validation (2026-10-03): what DAST can and can't see

Three runtime-only flaws were planted locally. Each was confirmed live before judging the scanner.

| Planted flaw | DAST | What caught it instead |
|---|---|---|
| Stack traces in error responses | ❌ ZAP's API scan sends only well-formed JSON, so it never reaches Spring's default error page | `ErrorDisclosureTest` (real server; MockMvc never renders `/error`) |
| CORS: any origin, with credentials | ❌ No `Origin` header is sent, and the active CORS rule (40040) isn't installed in the official image | `CorsPolicyTest` |
| (side finding) Boot 4 silently ignores `server.error.*` | n/a | `spring-boot-properties-migrator`: our hardening was dead config; renamed to `spring.web.error.*` |

The widened policy then found real issues: Spring's **Whitelabel page** served to browsers
(framework fingerprint), so errors are now JSON regardless of `Accept` (`ApiErrorController`),
and **`/error` called directly returned 500 / status 999**, now 404. Tomcat's bare HTML 400 for
malformed request lines (no version shown) is an accepted, scoped, expiring risk.

**Lesson:** a gate is only as good as what it actually exercises. Know each scanner's blind spots
and cover them at the right layer. Here that's real-server regression tests, which run before DAST.

## Authenticated DAST + authorization probe (M1 slice 2c)

`dast.sh` runs three stages against the same jar and database:

| Stage | What | Why this order |
|---|---|---|
| 1. `authz_probe.py` | Cross-tenant + role matrix: 27 checks over anonymous, member, leader, admin A, admin B. BOLA by id, list leaks, mass assignment, setup links on the wrong host, CSRF, Host spoofing, session replay on another tenant. Uses the real `Host` header, like production. | First: the per-IP login throttle allows 5 failures, and stage 3 attacks the login endpoint. |
| 2. ZAP as a logged-in TRUST_ADMIN | Session cookie + CSRF + tenant header on every request. Spec minus login/logout (would rotate or kill the session) and register (public). | Needs a fresh, unthrottled login. |
| 3. ZAP unauthenticated | The G5 scan, including login/logout/register. | Last: it trips the login throttle. |

**Why a separate probe:** ZAP can't judge access control. A `200` on `PATCH /users/7/role` is
normal unless you know user 7 belongs to another tenant. The probe knows who owns what, so
it asserts the expected status for each identity.

**Coverage checks (each a hard failure):**
- probe: any check off its expected status;
- authenticated pass: the session must still be valid afterwards, and the scan must have
  created at least one user (it got past authz, CSRF and validation to the database);
- unauthenticated pass: it must have created a tenant itself (stages 1-2 create tenants too, so
  only the difference counts).

### Validation (2026-10-03)

- **The coverage check caught a false "clean" scan on its first run.** ZAP reported 117 passes
  and 0 failures for the authenticated pass, yet it created no users. `zap-api-scan.py` splits
  `-z` options on whitespace, so the cookie `SC_SESSION=…; XSRF-TOKEN=…` lost its CSRF half
  and every state-changing request got 403. Fixed (no spaces in replacer values).
- **Planted flaws, one jar, probe run against it:** a missing `@PreAuthorize` (member promotes
  themselves), a loose Host match (`<slug>.attacker.example` accepted), CSRF disabled on
  `/users`, and a permissive RLS policy (`USING (true)`). **All 4 caught, 11/27 checks red.**
- **False positive, root-caused:** "Path Traversal (High, low confidence)" on `register.slug`
  with value `register`. Reproduced by hand: `register` behaves like any fresh slug (201) and real
  traversal payloads get 400. It appeared because the authenticated pass had already taken the
  example slug, so the unauthenticated baseline was a 409. Fix: the authenticated pass no longer
  attacks public endpoints. Side effect worth keeping: auth-flow names (`register`, `signup`,
  `password`, …) are now reserved slugs, since tenant hosts serve login pages and those names
  would be ready-made phishing hosts.

### First findings on M2 (devotees): two ways to turn input into a 500

The gate passed (500s are Low), but the app log had 8 errors. Both were real bugs:

| Finding | Root cause | Fix (global, not per endpoint) |
|---|---|---|
| 500 on `POST /devotees` and on search with `q=…%00…` | NUL (`\u0000`) passes bean validation; Postgres rejects it in text | `NulRejectingStrings` (Jackson: every JSON string) + `RequestSanityFilter` (every parameter) → 400 |
| 500 on `GET /devotees?=x` | Tomcat 11 throws `InvalidParameterException` when the parameters are first read | `RequestSanityFilter` reads them first and answers 400 |

They first appeared with devotees only because those are the first endpoints with free-text fields
and query parameters. The user and register endpoints rejected those values through stricter
format rules (email, slug), by luck rather than by design. Regression tests: `DevoteeTest.nulBytesAreABadRequestNotAServerError`,
`ErrorDisclosureTest.malformedQueryParametersAreABadRequest` (real server; MockMvc has no Tomcat
parameter parsing). Both failed before the fix.

**Lesson:** read the app's ERROR log, not only the gate verdict. `dast.sh` prints the count.

### M2 slice 2 (CSV export/import): one false positive, three 500s

| Finding | Verdict | Action |
|---|---|---|
| Persistent XSS (High, **low confidence**) on `GET /devotees/export`: stored `<script>` names come back in the CSV | Reproduced by hand: `text/csv` + `attachment` + `nosniff`, even for `Accept: text/html`. Downloaded, never rendered. | Defence in depth: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; sandbox` on every `/api/**` response. Then a scoped, expiring accepted risk. Headers pinned by tests. |
| 500 on `POST /devotees/import`: malformed multipart part (`MultipartException`) | Real | → 400 `malformed_upload`; oversize → 413 |
| 500 on `PUT /devotees/{id}`: row deleted by a concurrent request (`StaleStateException`) | Real (race between ZAP's own requests) | → 409 `concurrent_modification` |

Final run: probe 49/49, both passes 0 failures, **0 app ERROR lines**.

## Known gaps

- No beta/alpha ZAP rules: fetching add-ons at scan time would pull unpinned code into CI. If
  needed, build a pinned ZAP image with them instead.
- ZAP scans as a TRUST_ADMIN only. Lower roles and cross-tenant cases are covered by the probe,
  which must grow with every new endpoint (M2+: devotees, donations, events).
- The authenticated ZAP pass sends the tenant in the dev-only `X-Tenant-Slug` header (ZAP can't
  rewrite `Host` and the app rejects `127.0.0.1`). Host-based resolution is covered by the probe.
- The scan runs the `local` profile (header-based tenant override enabled). A production-like
  profile comes with M6.
- After the first registration, later attacks on the same slug get `409 slug_taken`, so the insert
  path is exercised once per scan, not per payload.
- Once ZAP sends the CSRF cookie, the server stops re-issuing it, so passive cookie-flag checks no
  longer see it. Cookie and header flags are therefore asserted in `SecurityRegressionTest`.
