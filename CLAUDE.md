# SevaCenter — project guide for Claude Code

**Read `docs/STATUS.md` first** — it's the running dev log and says exactly where we are and
what to do next.

## What this is

A multi-tenant SaaS for Indian temples and religious trusts: devotee management, donations with
80G receipts, events, and a public site for every temple. Built to production standards as a
company, security-first from the first commit.

Two brands, one platform:
- **SevaCenter** — admin platform for trust staff (`app.sevacenter.app`) — restrained palette.
- **MandirCenter** — public per-temple sites (`yourtemple.mandircenter.app`) — vibrant Hindu palette.

## How we work — three roles

- **Dev** = coding agents write app code and open PRs.
- **AppSec** = Abhi: threat models, secure review, pipeline security gates, governs AI-written code.
- **DevOps** = CI/CD + Terraform + AWS (cloud lives here).

Full detail: `docs/roles-and-teams.md`. Decisions: `docs/adr/`. Design: `docs/design/`.

## Stack

Java 21 + **Spring Boot 4.1** (Security, Data JPA, Flyway, springdoc) · PostgreSQL + **Row-Level
Security** for tenant isolation · Python for security/pipeline tooling · Next.js/TS frontend (later)
· Terraform → AWS ECS Fargate (M6) · GitHub Actions + OIDC. Build tool: Maven (`./mvnw`).

## Run it (local)

```bash
cp -n .env.example .env            # set DB_PASSWORD and DB_APP_PASSWORD
make db-up                         # Postgres + db-init creates the least-privilege app role
make run                           # starts on :8080 with the 'local' profile (2-role DB)
make test                          # hermetic Testcontainers tests (needs Docker)
```
API docs when running: `/swagger-ui.html`. Health: `/actuator/health`, `/api/v1/ping`.

## Conventions

- Conventional Commits; end commit messages with the Co-Authored-By trailer for the model in use.
- Schema changes = Flyway migrations only (`backend/src/main/resources/db/migration`), forward-only.
- Secrets from env only; pre-commit (gitleaks/trufflehog/semgrep) + CI enforce it. `make hooks` to install.
- Every change reaches `main` through a PR that passes all required security checks (no bypass).
