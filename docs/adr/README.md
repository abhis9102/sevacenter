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
| [0014](0014-events-and-registration-passes.md) | Events with free registration passes: capacity under row lock, unguessable codes, check-in once, gate sees no contacts | Accepted |
| [0015](0015-sevak-signups.md) | Sevak signups: public, rate-limited, contacts LEADER+ only, no deletion | Accepted |
| [0016](0016-puja-bookings-seva-income.md) | Puja bookings: dakshina is seva income (never in the 80G ledger), verified payments only, priest sees no contacts | Accepted |
| [0017](0017-public-temple-page.md) | Public temple page on the trust host: temple-entered content only, links from real service state | Accepted |
| [0018](0018-devotee-login-one-time-codes.md) | Devotee login: one-time codes by SMS/email, HMAC-stored, attempt + send limits, separate portal-scoped session | Accepted |
| [0019](0019-donation-history-in-my-seva.md) | Donation history in "my seva": optional checkout contact, exact-contact matching, receipt copies with PAN masked | Accepted |
| [0020](0020-audit-log.md) | Audit log: append-only (DB grants), same-transaction writes, actor from the session, no personal data, TRUST_ADMIN viewer | Accepted |
| [0021](0021-module-access-limits.md) | Per-module access limits (VIEW/NONE) that only narrow a role, enforced on every API call, admins never limited | Accepted |
| [0022](0022-donation-funds.md) | Earmarked funds: managed per-trust list, active-only for new gifts, reversals net out per fund, online earmarking | Accepted |
| [0023](0023-devotee-activity.md) | Devotee activity on the record: one read, each section gated by its own module and role, net donations count reversals | Accepted |
| [0024](0024-mandircenter-temple-site.md) | MandirCenter temple site: structured darshan hours, same-day status override, aarti timetable, calculated panchang | Accepted |
| [0025](0025-devotee-contacts-and-profile.md) | One devotee, several OTP-verified contacts (merge on link), the devotee's own profile, forms pre-filled | Accepted |
| [0026](0026-counter-bookings.md) | Counter bookings and gate passes for walk-ins: staff-only, contact optional, paid dakshina records its mode | Accepted |
