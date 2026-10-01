# Product Design — v1

Core decisions locked 2026-10-02 (see §8). Remaining open items still marked ❓.

**Decisions locked:** MVP = tenant+domain + Devotees + Donations + **Events/Registrations**;
multi-tenancy = **shared schema + Postgres RLS**; backend = **Java 21 + Spring Boot** (app) with
**Python for all security/pipeline tooling** (polyglot); **80G receipts in MVP**; CI/CD = **GitHub
Actions + OIDC**.

## 1. Vision

A temple / religious trust signs up, gets its own branded space at `yourtemple.sevacenter.app`, and
from there manages its **devotees**, collects **donations**, runs **events/registrations**, and
**publishes** announcements — the way Planning Center does for churches, reframed for the Indian
temple/trust market (Razorpay, DPDP Act, 80G receipts).

## 2. Feature inventory (mapped from Planning Center)

Mapped to Planning Center's modules, then prioritized for *our* build.

| PC module | SevaCenter equivalent | Priority | Why |
|---|---|---|---|
| **Church Center** (per-church site/domain) | **Tenant registration + per-temple subdomain** | **P0 — foundation** | Everything hangs off "which tenant." Richest tenancy/domain security surface. |
| **People** | **Devotees / Members** | **P1** | Multi-tenant PII directory → BOLA/IDOR/PII surface. |
| **Giving** | **Donations (daan/seva)** via Razorpay | **P1** | Money → payment authz + business logic. PCI story. |
| **Registrations** | **Events + Registrations** | **P1 — in MVP** | Public forms, uploads, rate limiting. |
| **Publishing** | **Announcements / public page** | P2 (wave 2) | Mini-CMS; content authz, stored XSS surface. |
| Services / Check-Ins / Groups / Calendar | — | later | Little *new* security surface for the effort. |

**MVP (locked) = tenant registration + subdomain, Devotees, Donations (with 80G), Events/
Registrations.** Covers multi-tenancy + RBAC + PII + money + public forms. **Publishing is wave 2.**

## 3. The P0 feature in detail: tenant registration + per-temple domain

This is the Church Center analog and the first thing we build.

**Flow:** someone registers a trust → picks a subdomain `yourtemple` → gets `yourtemple.sevacenter.app`
→ becomes `trust-admin` → invites leaders/members.

**Two phases of "domain":**
1. **Subdomain (MVP):** `*.sevacenter.app` via a wildcard DNS record + wildcard ACM TLS cert. App
   routes by the `Host` header → resolves tenant. Relatively easy; teaches Host-based tenancy.
2. **Custom domain (later):** `donate.sometemple.org`. Harder — per-tenant domain verification,
   per-tenant cert issuance. Great DevOps/cloud learning; defer to wave 2.

**Security surface this one feature creates (your specialty):**
- Subdomain takeover (dangling DNS, unclaimed tenant subdomains)
- Tenant resolution by Host header → Host-header injection, tenant confusion/spoofing
- Subdomain enumeration / tenant existence disclosure
- Reserved-name abuse (someone registers `admin`, `api`, `www`, `mail` as a subdomain)
- Cross-tenant isolation once routing is by Host

## 4. Multi-tenancy model (key architecture decision)

| Option | Isolation | Complexity | Training value |
|---|---|---|---|
| **Shared DB, shared schema, `tenant_id` column** ⟵ proposed | Row-level | Low | **High** — this is where tenant-isolation bugs live (your BOLA specialty) |
| Shared DB, schema-per-tenant | Medium | Medium | Medium |
| DB-per-tenant | Strong | High | Low (isolation handed to you by infra) |

**LOCKED:** shared DB + `tenant_id` + **Postgres Row-Level Security (RLS)** enforcing it at the DB
layer. Most common real-world SaaS model, and implementing RLS correctly (plus finding where app
code can bypass it — e.g. a connection that forgets to `SET app.tenant_id`, or a superuser role that
bypasses RLS) is a strong, demonstrable security story.

## 5. Tech stack (proposal)

| Layer | Choice | Notes |
|---|---|---|
| Backend / API | **Java 21 (LTS) + Spring Boot 3.x** (LOCKED) | Spring Web + **Spring Security** + Spring Data JPA/Hibernate. Serves the SWE/Java track; Spring Security doubles as enterprise AppSec depth |
| Build tool | **Maven** (proposed) | Ubiquitous in enterprise, explicit to review; Gradle is the modern alt. ❓ |
| DB | **PostgreSQL** + **RLS** for tenancy | Tenant set per-request via a session variable; RLS enforces at DB layer |
| Frontend | **Next.js / TypeScript** (admin + public), subdomain routing via middleware | FE is secondary; keep minimal |
| Auth | **Spring Security** (don't hand-roll); session or JWT | Effort goes into authz (method-level `@PreAuthorize`, tenant checks), not reinventing auth |
| Payments | **Razorpay** (Java SDK) | Indian rail; webhook signature verification |
| **Security / pipeline tooling** | **Python** | Custom checks, CI glue, AI-assisted triage, secret-scanner-style tools. Your strength; what AppSec JDs want |
| Containers | Docker + compose (local) | JVM base image → container-security lesson (distroless/jlink to shrink + harden) |
| IaC | **Terraform** (HCL) | Authoring, not just scanning |
| Cloud | AWS **ap-south-1**: ECS **Fargate** + RDS Postgres + ALB + Route53 + wildcard **ACM** cert + Secrets Manager | Skip EKS early; Fargate first |
| CI/CD | **GitHub Actions + OIDC to AWS** (LOCKED) | No static keys. Jenkins = wave-2 security side-quest |
| Pipeline security | Semgrep (+ Java rules), **OWASP Dependency-Check / osv-scanner** (Maven SCA), gitleaks+trufflehog, Checkov, Trivy, syft, cosign | Reuse `~/appsec-pipeline-lab`; now scans a Java + JS + HCL polyglot repo |

**Why this stack (decision rationale):**
- **Polyglot by design** — Java app + Python security tooling mirrors a real org (app devs in Java,
  security engineer writes Python) and serves both of Abhi's tracks at once.
- **Java/Spring Boot over Python/FastAPI** — chosen to feed the SWE/Java track and because Spring
  Security is where enterprise authn/authz lives (most apps Abhi would later secure are Java/Spring).
  Known cost: slower build + reviewing agent-written Java while gaining fluency.
- **Not Ruby/Rails** (Planning Center's stack) — we copy PC's product *shape*, not its stack; Ruby
  serves neither of Abhi's tracks.
- **GitHub Actions over Jenkins/GitLab** — code is on GitHub, OIDC gives keyless AWS deploy, native
  SARIF/code-scanning. Jenkins is a wave-2 side-quest (enterprise relevance + Jenkins hardening is
  itself an AppSec exercise).

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
- **ISO 27001** — control mapping for the GRC story.
- **80G** — temple/trust donation tax-exemption receipts. **IN MVP.** Pulls in **donor PAN capture**
  (sensitive PII — extra care), the trust's 80G registration number in trust settings, a compliant
  receipt format, and financial-year reporting. Good PII + compliance surface.

## 8. Decisions

**Locked (2026-10-02):**
1. MVP = tenant+domain, Devotees, Donations (with 80G), **Events/Registrations**. Publishing = wave 2.
2. Multi-tenancy = shared schema + `tenant_id` + **Postgres RLS**.
3. Backend = **FastAPI**.
4. **80G receipts in the MVP** (brings donor PAN handling into scope).

5. Backend = **Java 21 + Spring Boot** (app); **Python** for all security/pipeline tooling (polyglot).
6. CI/CD = **GitHub Actions + OIDC to AWS**. Jenkins = wave-2 side-quest.

**Still open ❓:**
7. Build tool: Maven (proposed) vs Gradle.
8. Final product name (SevaCenter is a placeholder).
9. Custom domains (`donate.sometemple.org`) — confirmed wave 2, subdomain-only in MVP.
10. Public member/donor access — own login vs link/OTP in v1.
