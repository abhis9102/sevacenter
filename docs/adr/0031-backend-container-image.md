# ADR 0031: Backend container image

- Status: Accepted (2026-10-05)
- Context: M5 packages the backend as a container image for staging and production (ECS Fargate,
  M6). The image is what runs in production, so its contents and defaults are part of the attack
  surface, and the build must be reproducible.

## Decision

- **Multi-stage build from clean source** (`backend/Dockerfile`): JDK and Maven build the jar in
  stage 1; only the jar reaches the runtime image. A local build once packaged a migration left in
  `target/` by another branch, so `.dockerignore` keeps `target/`, `.git`, `.env*` and keys out of
  the build context.
- **Runtime base: distroless Java 21 on Debian 13**, chosen by AppSec (Abhi) after scanning three
  candidates with Trivy:

  | Base | C | H | Fixable, not shipped | Size | Shell / apt / curl |
  |---|---|---|---|---|---|
  | Temurin JRE on Ubuntu 26.04 | 0 | 0 | 0 | 395 MB | yes |
  | Distroless Debian 12 | 1 | 13 | 35 (no longer rebuilt) | 260 MB | no |
  | **Distroless Debian 13** | 0 | 8 | 1 | 260 MB | no |

  The Debian 13 Highs were unreachable (util-linux tools that aren't in the image; libexpat, which
  Java doesn't use) and had no fix yet. No shell, package manager or download tool means little to
  work with after a code-execution bug. That weighed more than a cleaner report.
- **Both base images pinned by digest**, with Dependabot (docker ecosystem) proposing bumps, since a
  pinned image never picks up OS fixes by itself.
- **Runs as the image's unprivileged user (65532)**; the jar is root-owned and read-only. Exec-form
  entrypoint, so java is PID 1 and shuts down gracefully on SIGTERM. Heap sized from the container
  limit (`MaxRAMPercentage=75`); `ExitOnOutOfMemoryError`. No `HEALTHCHECK` (no shell): the
  platform checks `/actuator/health`.
- **Runs under a read-only root filesystem** (`/tmp` as tmpfs), all capabilities dropped and
  `no-new-privileges`. Verified locally; the ECS task definition must set the same (M6).
- **Image scan policy (G6, next):** Trivy fails the build on *fixable* Critical/High; unfixable ones
  are tracked and the image is re-scanned daily.

## Consequences

- No `docker exec` shell for debugging. Use the distroless `:debug-nonroot` variant locally, never
  in a deployed image.
- **Findings for M6:**
  - **Secrets are passed as an explicit allowlist**, never a whole `.env` (it carried keys the app
    never reads).
  - **Migrations run as a separate one-off task.** Today Flyway runs at app start-up with the
    database owner's credentials, so the long-running web app holds a password that can drop
    every table. The app should receive only the least-privilege `sevacenter_app` role.
