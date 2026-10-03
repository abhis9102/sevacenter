# Architecture Decision Records

Each ADR captures one significant decision: the context, the choice, and the consequences.
Format: [MADR](https://adr.github.io/madr/)-lite. ADRs are immutable once accepted — to
change a decision, add a new ADR that supersedes the old one.

| # | Decision | Status |
|---|---|---|
| [0001](0001-polyglot-java-app-python-security-tooling.md) | Polyglot: Java/Spring Boot app, Python security tooling | Accepted |
| [0002](0002-multitenancy-shared-schema-postgres-rls.md) | Multi-tenancy: shared schema + Postgres RLS | Accepted |
| [0003](0003-cicd-github-actions-oidc.md) | CI/CD: GitHub Actions + OIDC to AWS | Accepted |
| [0004](0004-schema-via-flyway.md) | Schema managed by Flyway, Hibernate validate-only | Accepted |
| [0005](0005-pin-spring-boot-3.5-lts.md) | Pin backend to Spring Boot 3.5 (not 4.x) | Superseded by 0008 |
| [0006](0006-two-brands-sevacenter-mandircenter.md) | Two brands: SevaCenter (admin) & MandirCenter (public), two domains | Accepted |
| [0007](0007-session-cookies-with-csrf.md) | Session cookies + CSRF on (not bearer tokens) | Accepted |
| [0008](0008-migrate-to-spring-boot-4.1.md) | Migrate to Spring Boot 4.1 (3.5 is end-of-life) | Accepted |
| [0009](0009-staff-auth-per-tenant-host-sessions.md) | Staff auth: per-tenant admin host, server sessions, setup links, basic throttling | Accepted |
| [0010](0010-devotee-records-pii-and-access.md) | Devotee records: PII scope, tiered access with server-side masking, consent, erasure | Accepted |
| [0011](0011-donations-ledger-and-80g.md) | Donations: append-only ledger (DB-enforced), exact paise, reversals, FY in IST, donor erasure = anonymise | Accepted |
| [0012](0012-80g-receipts-and-pan-protection.md) | 80G receipts: gapless per-FY numbers, snapshots, cancellation; donor PAN AES-GCM (tenant-bound) + blind index | Accepted |
| [0013](0013-online-donations-razorpay.md) | Online donations into each trust's own Razorpay account: server-side orders, signature + API verification, idempotent settlement | Accepted |
