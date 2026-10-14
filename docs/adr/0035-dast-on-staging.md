# 0035 — DAST runs against staging after each deploy, with a dedicated scan trust

- **Status:** Accepted (2026-10-14)
- **Deciders:** AppSec (Abhi); rollout delegated to Claude
- **Relates to:** G5 (`docs/security/dast.md`), ADR 0018 (devotee login), ADR 0034 (staging), CR-1

## Context

G5 runs an active ZAP scan of the backend jar in a throwaway environment on every PR. It never
sees what users actually reach: the Caddy edge (TLS, HSTS, redirects), the Next.js frontend and
its `/api` proxy, or staging's real configuration. It also adds minutes to every merge.

## Decision

1. **Where:** DAST scans **staging after every deploy** (and on a schedule), the way most
   organisations scan their test environment. Active attacks on staging are accepted: it holds
   only test and demo data. The PR-time scan was planned to stay until the staging scan was
   proven; on 2026-10-15 AppSec removed it from CI first (merge friction, and its ZAP start-up
   was flaky), accepting a short window until the planted-flaw proof. `make dast` stays for local
   runs.
2. **What it is now:** a *detect* gate, not a *prevent* gate. A finding means a red run and a
   ticket; the change is already on staging and is fixed forward or reverted. Prevention comes
   back at promotion: when production exists, a staging scan with unaccepted findings blocks it.
   Known issues still can't return: every fixed DAST finding is a `SecurityRegressionTest` in the
   backend build, which stays a required PR check.
3. **Scan trust:** ZAP works in a dedicated `dast-scan` trust with its own staff admin and devotees,
   so the attacks write junk there and not into the demo trusts. Attempts to reach other trusts'
   data are part of the test.
4. **Devotee login for the scanner:** a fixed login code for the scan trust only
   (`ScanLoginCode`), so the scanner can sign in to the MandirCenter portal without an inbox.
   It is fenced like a test account, not a backdoor:
   - no default: both `SEVACENTER_SCAN_TRUST` and `SEVACENTER_SCAN_OTP` come from SSM on staging,
     and unset means off;
   - the app **refuses to start** with either set outside the `staging`/`local` profiles (relaxed
     binding means an environment variable could set it in production);
   - it works **only on the scan trust**, and only in place of a code that was really requested,
     so the send limits and the five-attempt cap per code still apply;
   - every use is audited (`event=devotee_login_scan_code`), and the code is single-use like any
     other.

## Consequences

- Faster merges; the scan covers the edge, frontend and real configuration for the first time.
- A new runtime flaw is found after merge, on staging, instead of before it.
- The scan code is a secret to rotate like the others; guessing it is no easier than guessing a
  real code, but a leaked one keeps working until it is rotated.
- Tests: `ScanLoginCodeTest` (start-up guard), `DevoteeScanLoginTest` (scan trust only, needs a
  requested code, attempt cap, single use). Both were mutation-checked: a code that worked on any
  trust, and a missing production guard, each failed them.
