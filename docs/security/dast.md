# DAST (dynamic testing) policy

Gate **G5**. Every other gate reads code or dependencies. DAST **attacks the running app** over HTTP
the way an outsider would, and catches what only exists at runtime: headers, cookies, error
handling, exposed endpoints, injection that really executes.

## How it runs

| Piece | What |
|---|---|
| `tools/security/dast.sh` | Throwaway Postgres (same least-privilege init as dev) → the **built jar** (the one G4 signs) → OWASP ZAP **API scan, active**, driven by our OpenAPI spec → coverage check. Same script in CI and locally (`make dast`). |
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

## Known gaps

- Unauthenticated scan only. Authenticated scanning (per role, per tenant, including cross-tenant
  attempts) comes with login in M1 slice 2.
- The scan runs the `local` profile (header-based tenant override enabled). A production-like
  profile comes with M6.
- After the first registration, later attacks on the same slug get `409 slug_taken`, so the insert
  path is exercised once per scan, not per payload.
- Once ZAP sends the CSRF cookie, the server stops re-issuing it, so passive cookie-flag checks no
  longer see it. Cookie and header flags are therefore asserted in `SecurityRegressionTest`.
