# SevaCenter — project guide for Claude Code

**Read `docs/STATUS.md` first** — it's the running dev log and says exactly where we are and
what to do next.

## What this is

A multi-tenant SaaS for Indian temples / religious trusts — a "Planning Center for temples."
It is also Abhi's **DevSecOps / Cloud / AppSec learning project**, built as if by a small
product company. (Broader career context lives in `~/job/CLAUDE.md`.)

Two brands, one platform (like Planning Center / Church Center):
- **SevaCenter** — admin platform for trust staff (`app.sevacenter.app`) — restrained palette.
- **MandirCenter** — public per-temple sites (`yourtemple.mandircenter.app`) — vibrant Hindu palette.

## How we work — three roles, one maintainer

- **Dev** = coding agents write app code and open PRs.
- **AppSec** = Abhi: threat models, secure review, pipeline security gates, governs AI-written code.
- **DevOps** = CI/CD + Terraform + AWS (cloud lives here).

Full detail: `docs/roles-and-teams.md`. Decisions: `docs/adr/`. Design: `docs/design/`.

## Stack

Java 21 + **Spring Boot 3.5** (Security, Data JPA, Flyway, springdoc) · PostgreSQL + **Row-Level
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
- Clean-room: this is built from Planning Center's public product shape only — nothing from any
  prior security testing of the real Planning Center goes here.
