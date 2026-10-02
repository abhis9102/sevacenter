# SCA (dependency scanning) policy

Gate **G3**. Our code is a few hundred lines; our *dependencies* are ~170 packages written by
other people. SCA answers: does anything we ship have a published vulnerability?

## Moving parts

| Piece | Kind | When | Where |
|---|---|---|---|
| `osv-scanner` (`sca` job) | detective: finds known-vulnerable versions | every PR, push to main, **daily** | CI → SARIF → Security → Code scanning |
| Ruleset `code_scanning` rule (osv-scanner) | gate | every PR | blocks merge on **high/critical** (CVSS ≥ 7) |
| `sca_policy.py coverage` | integrity of the scan itself | every run | CI step, fails the job |
| `sca_policy.py ignores` | governance of accepted risk | every run | CI step, fails the job |
| Dependabot security updates | corrective: opens fix PRs for known CVEs | when an advisory lands | repo setting |
| Dependabot version updates (maven) | preventive: stay current, so fixes are small | weekly, 7-day cooldown | `.github/dependabot.yml` |
| Tickets (`code-scanning-tickets`) | ownership | open alerts on `main` | GitHub Issues, label `sca` |

## Why daily

SAST findings only change when *code or rules* change. SCA findings change when **the world**
does: a CVE published tonight makes yesterday's clean build vulnerable without a single commit.
Boot 4.1.1 was clean on release (2026-08-20) and had 3 critical Tomcat CVEs six weeks later.

## Severity policy

- **Critical/High (CVSS ≥ 7):** blocks PRs. On main: ticket, fix within days.
- **Medium/Low:** visible in the Security tab; ticketed on main; doesn't block a PR, since blocking on
  everything trains people to bypass the gate.

## How to fix a finding (in order of preference)

1. **Bump the parent** (Spring Boot) if a release includes the fix.
2. **Override the managed version** with a property (`tomcat.version`, …), patch-level only,
   annotated with the advisory IDs. Remove it when the parent catches up.
3. **Accept the risk**, only with a reason and an expiry (below).

## Accepting a risk (`osv-scanner.toml`)

Use this when there's no fix, or the vulnerable code path is provably unreachable. Each entry
needs `reason` (≥ 20 chars: who decided and why) and `ignoreUntil` ≤ 90 days out. CI enforces both.
When the date passes, the finding comes back and the gate goes red. An ignore with no expiry is
a permanent blind spot, so it isn't allowed.

## "0 vulnerabilities" can be a lie

During the Boot 4 evaluation, a test scan reported **0 vulnerabilities**. It had resolved
**15 of 183 packages**, because the pom still used Boot 3 starter names that don't exist in 4.x.
So the job also fails when:
- osv-scanner exits **128** (found nothing to scan) or any code other than 0/1, and
- any `<dependency>` declared in a `pom.xml` wasn't **resolved to a concrete version**
  (`sca_policy.py coverage`).

The check was itself tested against that broken pom. Its first version passed it, because declared
deps appear in the output even when unresolved. A check that's never been seen to fail isn't a check.

## Known gaps (next gates)

- osv-scanner resolves Maven's tree **itself**; it can differ from what Maven actually builds.
  G4 (SBOM) generates the dependency list from the real build and scans that.
- Reachability: findings aren't filtered by whether we call the vulnerable code. That's
  deliberate for now (simpler, conservative); revisit if the noise grows.
- Malicious / hallucinated packages (not CVEs) are a different problem: M7.
