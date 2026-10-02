# Product Design — v1

Core decisions locked 2026-10-02 (see §8). Remaining open items still marked ❓.

**Decisions locked:** MVP = tenant+domain + Devotees + Donations + **Events/Registrations**;
multi-tenancy = **shared schema + Postgres RLS**; backend = **Java 21 + Spring Boot** (app) with
**Python for all security/pipeline tooling** (polyglot); **80G receipts in MVP**; CI/CD = **GitHub
Actions + OIDC**.

## 1. Vision

A temple / religious trust signs up, gets its own branded space at `yourtemple.mandircenter.app`, and
from there manages its **devotees**, collects **donations**, runs **events/registrations**, and
**publishes** announcements, built for the Indian temple/trust market (Razorpay, DPDP Act,
80G receipts).

## 2. Feature inventory

| Module | What it does | Priority | Security focus |
|---|---|---|---|
| **MandirCenter sites** | **Tenant registration + per-temple subdomain** | **P0 — foundation** | Everything hangs off "which tenant": tenancy and domain security. |
| **Devotees** | Devotee / member directory | **P1** | Multi-tenant PII → BOLA/IDOR, DPDP compliance. |
| **Donations** | Daan/seva via Razorpay, 80G receipts | **P1** | Money → payment authz, business logic, PCI scope. |
| **Events** | Events + registrations | **P1 — in MVP** | Public forms, uploads, rate limiting. |
| **Announcements** | Public page / mini-CMS | P2 (wave 2) | Content authz, stored XSS. |
| Volunteers / check-ins / groups / calendar | — | later | After MVP, driven by customer demand. |

**MVP (locked) = tenant registration + subdomain, Devotees, Donations (with 80G), Events/
Registrations.** Covers multi-tenancy + RBAC + PII + money + public forms. **Announcements are wave 2.**

## 3. The P0 feature in detail: tenant registration + per-temple domain

This is the foundation of MandirCenter and the first thing we build.

**Flow:** someone registers a trust → picks a subdomain `yourtemple` → gets `yourtemple.mandircenter.app`
→ becomes `trust-admin` → invites leaders/members.

**Two phases of "domain":**
1. **Subdomain (MVP):** `*.sevacenter.app` via a wildcard DNS record + wildcard ACM TLS cert. App
   routes by the `Host` header → resolves tenant. Relatively simple to operate.
2. **Custom domain (later):** `donate.sometemple.org`. Harder — per-tenant domain verification,
   per-tenant cert issuance. Defer to wave 2.

**Security surface this one feature creates:**
- Subdomain takeover (dangling DNS, unclaimed tenant subdomains)
- Tenant resolution by Host header → Host-header injection, tenant confusion/spoofing
- Subdomain enumeration / tenant existence disclosure
- Reserved-name abuse (someone registers `admin`, `api`, `www`, `mail` as a subdomain)
- Cross-tenant isolation once routing is by Host

## 4. Multi-tenancy model (key architecture decision)

| Option | Isolation | Complexity | Cost at scale |
|---|---|---|---|
| **Shared DB, shared schema, `tenant_id` column** ⟵ chosen | Row-level (RLS-enforced) | Low | Low |
| Shared DB, schema-per-tenant | Medium | Medium | Medium (migrations × tenants) |
| DB-per-tenant | Strong | High | High (one DB per temple) |

**LOCKED:** shared DB + `tenant_id` + **Postgres Row-Level Security (RLS)** enforcing it at the DB
layer. The most common SaaS model; the work is implementing RLS correctly and testing where app
code could bypass it (e.g. a connection that forgets to `SET app.tenant_id`, or a superuser role
that bypasses RLS).

## 5. Tech stack (proposal)

| Layer | Choice | Notes |
|---|---|---|
| Backend / API | **Java 21 (LTS) + Spring Boot 4.1** (LOCKED; was 3.x, see ADR 0008) | Spring Web + **Spring Security** + Spring Data JPA/Hibernate. Mature, secure-by-default, large hiring pool |
| Build tool | **Maven** (LOCKED) | Ubiquitous in enterprise, explicit to review |
| DB | **PostgreSQL** + **RLS** for tenancy | Tenant set per-request via a session variable; RLS enforces at DB layer |
| Frontend | **Next.js / TypeScript** (admin + public), subdomain routing via middleware | FE is secondary; keep minimal |
| Auth | **Spring Security** (don't hand-roll); session or JWT | Effort goes into authz (method-level `@PreAuthorize`, tenant checks), not reinventing auth |
| Payments | **Razorpay** (Java SDK) | Indian rail; webhook signature verification |
| **Security / pipeline tooling** | **Python** | Custom checks, CI glue, AI-assisted triage, secret-scanner-style tools. Owned by the security function |
| Containers | Docker + compose (local) | JVM base image → distroless/jlink to shrink + harden |
| IaC | **Terraform** (HCL) | Authoring, not just scanning |
| Cloud | AWS **ap-south-1**: ECS **Fargate** + RDS Postgres + ALB + Route53 + wildcard **ACM** cert + Secrets Manager | Skip EKS early; Fargate first |
| CI/CD | **GitHub Actions + OIDC to AWS** (LOCKED) | No static keys |
| Pipeline security | Semgrep (+ Java rules), **OWASP Dependency-Check / osv-scanner** (Maven SCA), gitleaks+trufflehog, Checkov, Trivy, syft, cosign | Scans a Java + JS + HCL polyglot repo |

**Why this stack (decision rationale):**
- **Polyglot by design** — Java app + Python security tooling mirrors a real org (app engineers in
  Java, security engineering in Python).
- **Java/Spring Boot over Python/FastAPI** — Spring Security is where enterprise-grade authn/authz
  lives, and Java offers a large hiring pool. Known cost: a slower initial build.
- **GitHub Actions over Jenkins/GitLab** — code is on GitHub, OIDC gives keyless AWS deploy, native
  SARIF/code-scanning.

## 6. Architecture sketch (MVP)

```
            Route53 (*.sevacenter.app)
                   │
            ALB (wildcard ACM TLS)
                   │  routes by Host header → tenant
         ┌─────────┴─────────┐
         │  ECS Fargate      │   FastAPI app (+ Next.js, or separate service)
         │  (app containers) │   - resolves tenant from Host
         └─────────┬─────────┘   - enforces RBAC + RLS
                   │
         ┌─────────┴─────────┐        ┌──────────────────┐
         │ RDS PostgreSQL    │        │ Razorpay (webhook)│
         │ (RLS by tenant)   │        └──────────────────┘
         └───────────────────┘
         Secrets Manager · CloudWatch (logs/metrics)
```

## 7. Compliance targets

- **DPDP Act 2023** (India privacy) — consent, data minimization, deletion.
- **PCI DSS** — via Razorpay (we never store card data; stay in the lowest SAQ scope — state that).
- **ISO 27001** — control mapping, for institutional and enterprise customers.
- **80G** — temple/trust donation tax-exemption receipts. **IN MVP.** Pulls in **donor PAN capture**
  (sensitive PII — extra care), the trust's 80G registration number in trust settings, a compliant
  receipt format, and financial-year reporting.

## 8. Decisions

**Locked (2026-10-02):**
1. MVP = tenant+domain, Devotees, Donations (with 80G), **Events/Registrations**. Publishing = wave 2.
2. Multi-tenancy = shared schema + `tenant_id` + **Postgres RLS**.
3. **80G receipts in the MVP** (brings donor PAN handling into scope).
4. Backend = **Java 21 + Spring Boot** (app); **Python** for all security/pipeline tooling (polyglot).
5. CI/CD = **GitHub Actions + OIDC to AWS**.
6. Build tool = **Maven**.

**Still open ❓:**
7. Final product name (SevaCenter is a placeholder).
8. Custom domains (`donate.sometemple.org`) — confirmed wave 2, subdomain-only in MVP.
9. Public member/donor access — own login vs link/OTP in v1.
