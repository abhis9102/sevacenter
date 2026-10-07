# Custom Semgrep rules

Part of gate **G2 (SAST)**. The registry rulesets (`p/java`, `p/secrets`…) know generic bug
patterns: SQL built from strings, weak crypto, hardcoded keys. They can't know **our** conventions,
for example "every staff endpoint declares a role". Breaking one of those conventions compiles,
passes the tests and looks normal in review, and it's exactly the mistake a coding agent makes when
it copies a nearby method and drops one annotation. Custom rules turn those conventions into
checks that block the merge.

## Why, when, where

| | |
|---|---|
| **Risk covered** | Our own security invariants, written as code: today, a staff endpoint without a role check (broken access control) |
| **Risk not covered** | Whether the role in the check is the *right* one (`hasRole('VOLUNTEER')` on a TRUST_ADMIN action passes); object-level access inside the method (IDOR, tenant scoping): that's RLS + `authz_probe.py` (G5) |
| **When** | On commit (pre-commit hook, changed files) and on every PR, push and daily run (CI) |
| **Where** | Rules and their tests in `.semgrep/`; the `sast` job runs the rule tests, then the scan with the registry rulesets |
| **Blocks** | Rules are `severity: ERROR`, which lands as an error-level code-scanning alert; the `protect-main` ruleset blocks a merge on those |

## Rule: `endpoint-missing-role-check`

**The convention it enforces.** Authorization has two layers (`SecurityConfig`):

1. URL level: everything outside `PUBLIC_ENDPOINTS` needs a staff login: `anyRequest().authenticated()`.
2. Method level: **which role** may call it: `@PreAuthorize("hasRole('...')")` on the endpoint or
   its controller.

Layer 1 alone means *any* signed-in user, the lowest role included. So a staff endpoint without
layer 2 is CWE-862 (missing authorization), OWASP A01. The rule flags any
`@Get/Post/Put/Patch/Delete/RequestMapping` method of a `@RestController` with no `@PreAuthorize`
on the method or the class, except:

| Exception | How | Why it needs no role |
|---|---|---|
| `/api/v1/public/**` | the method's own path | Anonymous by design (donate page, puja booking, sevak signup, temple page); rate-limited where it writes. Same prefix as `PUBLIC_ENDPOINTS`, so the rule and the server agree. |
| `/api/v1/portal/**` | the method's own path | Devotees, signed in with their own cookie checked in `PortalController` (ADR 0018), never a staff session |
| `HealthController`, `CsrfController`, `ApiErrorController` | class allowlist | Must answer before any login: probes, the CSRF token bootstrap (login itself needs one), error dispatch |
| `RegistrationController`, `SetupController`, `PasswordResetController` | class allowlist | The caller has no session yet |
| `MeController`, `ProfileController` | class allowlist | Self-service for any role: the user comes from the session (`@AuthenticationPrincipal`), never from a parameter, so there's no other user's data to reach |

**Changing the allowlist is a security decision.** It lives in the rule file, so it goes through a
PR and review like the rule itself, not through an inline `// nosemgrep` that hides it in a
feature diff. (Inline suppressions still work in Semgrep; reviewers treat one on this rule as a
finding.)

**Known limits.**
- The path exception reads the method's own mapping. A controller with a class-level
  `@RequestMapping("/api/v1/public")` and short method paths would be flagged (a false positive,
  in the safe direction). Our public endpoints use full paths.
- Paths given as `@GetMapping(path = "...")` / `value = "..."` don't match the path exception
  either (also a safe-direction false positive). We use the positional form.
- `@PreAuthorize("permitAll()")` passes. Semgrep checks that a decision exists, not which one;
  reviewing the expression is the human part.

## Rules are code, so they're tested

A rule that silently stops matching (a typo, a Semgrep upgrade) looks the same as clean code: 0
findings. So each rule has a test file next to it with `// ruleid:` (must flag) and `// ok:` (must
not flag) lines, and CI runs the tests **before** the scan:

```bash
semgrep --test --config .semgrep/endpoint-missing-role-check.yaml .semgrep/endpoint-missing-role-check.java
```

(`semgrep --test .semgrep/` would skip the folder because its name starts with a dot, so the rule
and its test are named explicitly.) The test file is vulnerable on purpose, so the scan excludes
it (`--exclude .semgrep` in CI, `exclude:` in the hook). Using `--exclude` rather than a
`.semgrepignore` matters: creating a `.semgrepignore` replaces Semgrep's default ignore list.

## Validation (2026-10-07)

| Check | Result |
|---|---|
| Rule tests: 6 must-flag cases (no args, extra annotations, `@RequestMapping(method=…)`, look-alike `/publicity`, `public` mid-path) and 8 must-not-flag cases | **Pass** |
| Test of the test: one `ok:` flipped to `ruleid:` | **Fails** (`missed lines: [38]`, exit 1): the harness really checks |
| Real backend (143 files) | **0 findings.** First draft found 19; every one was a `/public/` or `/portal/` endpoint or an allowlisted controller, which is how the path exception got designed |
| Planted bug: `@GetMapping("/export-all")` with no role, added to a copy of `DonationController` | **Blocked**: 1 finding, exit 1, message names the method and the fix |
| Repo-wide scan includes the test file? | It did (6 alerts) until `--exclude .semgrep` was added; now 0 |
