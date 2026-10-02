# SevaCenter

Multi-tenant SaaS for Indian temples and religious trusts — a "Planning Center for temples."
Each trust self-registers and gets its own branded space (`yourtemple.mandircenter.app`) to
manage devotees, collect donations (with 80G receipts), run events, and publish announcements.

**Two brands, one platform** (like Planning Center / Church Center): **SevaCenter** is the admin
platform for trust staff (`app.sevacenter.app`); **MandirCenter** is the public devotee site, one
per temple (`yourtemple.mandircenter.app`). See `docs/adr/0006` and `docs/design/`.

**Status:** M0 — foundation scaffolded (2026-10-02). See `docs/roadmap.md`.

> Built as if by a small product company, and as a DevSecOps/Cloud/AppSec learning project.
> Three roles, one maintainer orchestrating coding agents: **Dev** (agents) write code,
> **AppSec** secures every stage, **DevOps** runs CI/CD and AWS. See `docs/roles-and-teams.md`.
>
> Clean-room analog built from Planning Center's *public product shape* only.

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21 + Spring Boot 4.1 (Spring Security 7, Data JPA, Flyway) |
| Database | PostgreSQL + Row-Level Security (multi-tenancy) |
| Frontend | Next.js / TypeScript *(from a later milestone)* |
| Security tooling | Python |
| IaC / Cloud | Terraform → AWS ECS Fargate *(from M6)* |
| CI/CD | GitHub Actions + OIDC to AWS |

See `docs/adr/` for why each was chosen.

## Quickstart (local dev)

Prereqs: JDK 21, Docker, Maven (the `./mvnw` wrapper also works).

```bash
cp .env.example .env     # then set DB_PASSWORD
make db-up               # start Postgres (docker compose)
make hooks               # install pre-commit guardrails
make run                 # start backend
curl localhost:8080/api/v1/ping          # -> {"status":"ok",...}
curl localhost:8080/actuator/health      # -> {"status":"UP"}
make test                # hermetic tests (Testcontainers; needs Docker)
```

Once running, the API documents itself:
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

`make help` lists all targets.

## Repository layout

```
sevacenter/
├── backend/              Spring Boot app (Java)
│   └── src/main/resources/db/migration/   Flyway migrations
├── frontend/             Next.js app            (later milestone)
├── infra/                Terraform              (M6)
├── security/             Python pipeline tooling (grows over roadmap)
├── docs/
│   ├── product-design.md     locked product + stack decisions
│   ├── roles-and-teams.md    the three-role operating model
│   ├── roadmap.md            milestones M0–M8
│   ├── adr/                  architecture decision records
│   └── security/             threat models
├── .github/              PR template, CODEOWNERS, CI workflow
├── docker-compose.yml    local Postgres
├── SECURITY.md · CONTRIBUTING.md
```

## Documentation

- **Product & decisions:** `docs/product-design.md`, `docs/adr/`
- **How we work:** `docs/roles-and-teams.md`, `CONTRIBUTING.md`
- **Security:** `SECURITY.md`, `docs/security/`
- **Plan:** `docs/roadmap.md`
