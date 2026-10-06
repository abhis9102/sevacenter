# Container image scan policy

Gate **G6**. The backend and the frontend ship as container images (ADR 0031), so what reaches
production is our code **plus an operating system we didn't write**. SCA (G3) and the SBOM (G4) see the jar's
libraries. Only an image scan sees the OS packages (libc, libexpat, zlib…), and only a Dockerfile
scan catches build mistakes such as running as root. Semgrep doesn't read Dockerfiles.

## Why, when, where

| | |
|---|---|
| **Risk covered** | Known-vulnerable OS packages and libraries in the shipped image; insecure Dockerfiles (root user, `:latest` / unpinned base, secrets in `ENV`, `ADD` from URLs…) |
| **Risk not covered** | Unknown (0-day) bugs; what the app does at runtime (G5 DAST); a malicious package with no CVE yet (M7 reputation check); the ECS task settings (M6 IaC scan) |
| **When** | Every PR and push to `main`, and **daily** on `main` (scheduled CI): a clean image today gains CVEs tomorrow, and base images get fixes we should pick up |
| **Where** | Job `image` in `.github/workflows/ci.yml`, required check **Image scan (Trivy)**. Same script locally: `make image-scan` |

## How it runs (`tools/security/image_scan.sh`)

1. **Accepted risks** (`.trivy/accepted.toml`) are validated, then rendered to the ignore file Trivy reads.
2. **Dockerfile / IaC misconfigurations**, scanned from the repo root: **Medium and above block**,
   reported as annotations on the exact file and line, so the reason shows on the PR diff.
   The root-level scan will also cover the frontend Dockerfile and the M6 Terraform.
3. **Build** each image (backend, frontend), then `docker save` it to a tar. Trivy reads the tar and **never gets the
   Docker socket**: mounting `/var/run/docker.sock` into a scanner gives it root on the runner, and
   scanners are a supply-chain target like anything else.
4. **One scan, every severity, every package.** That single result feeds:
   - **coverage**: the OS was detected, and both OS packages and the app's own libraries were
     listed (Java for the backend, Node for the frontend).
     A scan that analysed nothing would otherwise report "0 vulnerabilities" (the G3 lesson).
   - **gate**: a **fixable** Critical or High fails the job.
   - **SARIF** to code scanning (Security tab), unfixable findings included.
   - **image SBOM** (CycloneDX, CI artifact `image-scan`), OS packages included.

Trivy itself is pinned by digest (`aquasec/trivy@sha256:e2b2…` = 0.67.2).

## Why "fixable Critical/High" and not "any Critical/High"

Blocking on findings nobody can fix (no patched package exists yet) stops every deploy for
something no one can act on. Teams then learn to bypass the gate. So:

- **fixable** Critical/High → **block**. Rebuild on a patched base, bump the library, or accept it
  with a reason and expiry.
- **unfixable** → reported in code scanning and re-checked daily. When a fix ships, the same
  finding becomes fixable and starts blocking.

Raw counts mislead. Triage asks: is it **reachable** (is the vulnerable binary or function even
used)? Is there a **fix**? Does the package **need to be in the image**? The best fix is not
shipping the package. Severity also differs by distro (Ubuntu rates many bugs lower than
Debian/NVD), so compare base images by their findings, not their totals. See ADR 0031.

## Accepted risks (`.trivy/accepted.toml`)

Same rules as G3 (`osv-scanner.toml`) and G5 (`.zap/accepted.toml`), enforced by
`tools/security/image_policy.py`:

- One `id` per entry and a `kind` (`vulnerability` or `misconfiguration`). A misconfiguration is
  scoped to its file(s) with `paths`.
- A `reason` of at least 20 characters: who decided, and why it's acceptable.
- An `ignoreUntil` no more than 90 days out. Expired entries fail the policy step.
- An invalid entry **suppresses nothing**.

## Base image updates

Both base images are pinned by digest, so a build never changes underneath us. That also means
**they never patch themselves**. Dependabot (`docker` ecosystem, `/backend`) proposes digest bumps
weekly. Each bump is a PR, and this gate judges it.

## Validation (the gate was made to fail on purpose, 2026-10-05)

| Planted | Result |
|---|---|
| `USER root` in the Dockerfile | **Blocked** at step 2: `AVD-DS-0002 (HIGH): Last USER command in Dockerfile should not be 'root'` |
| Runtime base swapped to the stale distroless Debian 12 | **Blocked**: 8 fixable High (libexpat1 2.5.0-1+deb12u2, fixed in deb12u4…); 6 unfixable reported, not blocking |
| Accepted risks with no paths, short reason, 2-year expiry, no expiry | **Rejected**, 4 errors; the rendered ignore file was empty |
| **In CI**: `USER root` on a throwaway PR (#64) | **Image scan (Trivy)** failed with AVD-DS-0002, and the `protect-main` ruleset **refused the merge, even for the repo owner** ("the base branch policy prohibits the merge"). Closed unmerged. The failure reason was only in the log, so findings are now annotated on file and line |
| The real images | **Pass**. Backend: Debian 13.7, 25 OS + 113 Java packages, 0 fixable Critical/High, 8 unfixable reported. Frontend: 14 OS + 23 Node packages, 0 Critical/High |
| `USER root` in `frontend/Dockerfile` (2026-10-06) | **Blocked**: `::error file=frontend/Dockerfile,line=28::AVD-DS-0002 (HIGH)` |

## Not yet

- Local pre-commit hooks don't check Dockerfiles yet (Semgrep skips them), so `USER root` is only
  caught in CI. A pre-commit Trivy config hook would catch it before the push.

- Image signing and pushing to a registry (ECR), with the scan result attested, come in M6.
