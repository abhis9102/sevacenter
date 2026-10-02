# Contributing to SevaCenter

SevaCenter is developed as if by a small product company, with three roles (see
`docs/roles-and-teams.md`): **Development** (coding agents), **AppSec** (maintainer), and
**DevOps**. These conventions keep that model honest.

## Branching & PRs

- `main` is always releasable and protected. No direct pushes.
- Branch per change: `feat/…`, `fix/…`, `chore/…`, `docs/…`.
- Open a PR; the template includes an **AI-assisted code declaration** and a **security
  self-check**. Fill both in.
- CI (build + tests + secret scan) must be green. Sensitive-path changes also get a focused
  manual AppSec review.

## Commit messages — Conventional Commits

```
<type>(<scope>): <summary>

[body]
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`, `security`.
Scopes map to modules: `tenancy`, `auth`, `devotees`, `donations`, `events`, `infra`, `ci`.

## Local setup

```bash
cp .env.example .env     # set DB_PASSWORD
make db-up               # start Postgres
make hooks               # install pre-commit guardrails
make run                 # run backend → http://localhost:8080/api/v1/ping
make test                # hermetic tests (needs Docker)
```

## Database changes

- Schema changes are **Flyway migrations only** — a new `V<n>__description.sql` under
  `backend/src/main/resources/db/migration`. Forward-only; never edit an applied migration.
- Hibernate runs in `validate` mode; it never creates or alters schema.

## Definition of done

- Code + tests; `./mvnw verify` passes.
- Docs updated (ADR for a notable decision; module docs for new features).
- PR template completed; CI green.
