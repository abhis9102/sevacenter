# Product Design — v0 (brainstorm, open for discussion)

Everything here is a **proposal to argue with**, not a locked decision. Open questions are marked ❓.

## 1. Vision

A temple / religious trust signs up, gets its own branded space at `yourtemple.sevasetu.app`, and
from there manages its **devotees**, collects **donations**, runs **events/registrations**, and
**publishes** announcements — the way Planning Center does for churches, reframed for the Indian
temple/trust market (Razorpay, DPDP Act, 80G receipts).

## 2. Feature inventory (mapped from Planning Center)

Mapped to Planning Center's modules, then prioritized for *our* build.

| PC module | SevaSetu equivalent | Priority | Why |
|---|---|---|---|
| **Church Center** (per-church site/domain) | **Tenant registration + per-temple subdomain** | **P0 — foundation** | Everything hangs off "which tenant." Richest tenancy/domain security surface. |
| **People** | **Devotees / Members** | **P1** | Multi-tenant PII directory → BOLA/IDOR/PII surface. |
| **Giving** | **Donations (daan/seva)** via Razorpay | **P1** | Money → payment authz + business logic. PCI story. |
| **Registrations** | **Events + Registrations** | P2 | Public forms, uploads, rate limiting. |
| **Publishing** | **Announcements / public page** | P2 | Mini-CMS; content authz, stored XSS surface. |
| Services / Check-Ins / Groups / Calendar | — | later | Little *new* security surface for the effort. |

**Proposed MVP = P0 + P1:** tenant registration with subdomain, Devotees, Donations. That is the
"security-maximal minimal" core: multi-tenancy + RBAC + PII + money. P2 (Events, Publishing) is the
next wave.

❓ Agree MVP = P0+P1, with Events/Publishing as wave 2? Or do you want Publishing in the MVP?

## 3. The P0 feature in detail: tenant registration + per-temple domain

This is the Church Center analog and the first thing we build.

**Flow:** someone registers a trust → picks a subdomain `yourtemple` → gets `yourtemple.sevasetu.app`
→ becomes `trust-admin` → invites leaders/members.

**Two phases of "domain":**
1. **Subdomain (MVP):** `*.sevasetu.app` via a wildcard DNS record + wildcard ACM TLS cert. App
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

**Proposed:** shared DB + `tenant_id` + **Postgres Row-Level Security (RLS)** enforcing it at the DB
layer. Most common real-world SaaS model, and implementing RLS correctly (plus finding where app
code can bypass it) is a strong, demonstrable security story.

❓ Agree on shared-schema + RLS?

## 5. Tech stack (proposal)

| Layer | Choice | Notes |
|---|---|---|
| Backend / API | **Python + FastAPI** | You know Python; API-first; clean for security demos |
| DB | **PostgreSQL** (RLS for tenancy) | — |
| Frontend | **Next.js** (admin + public), subdomain routing via middleware | FE is secondary; keep minimal |
| Auth | Vetted library (don't hand-roll); session/JWT | Effort goes into authz, not reinventing auth |
| Payments | **Razorpay** (UPI/cards) | Indian rail; webhook signature verification |
| Containers | Docker + compose (local) | — |
| IaC | **Terraform** | Authoring, not just scanning |
| Cloud | AWS **ap-south-1**: ECS **Fargate** + RDS Postgres + ALB + Route53 + wildcard **ACM** cert + Secrets Manager | Skip EKS early; Fargate first |
| CI/CD | **GitHub Actions + OIDC to AWS** | No static keys — strong signal |
| Pipeline security | Semgrep, osv-scanner/pip-audit, gitleaks+trufflehog, Checkov, Trivy, syft, cosign | Reuse `~/appsec-pipeline-lab` |

❓ FastAPI vs alternatives (Django gives you auth/admin batteries-included — faster MVP, less
hand-wiring; FastAPI is leaner and more explicit). Worth a 2-minute decision.

## 6. Architecture sketch (MVP)

```
            Route53 (*.sevasetu.app)
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
- **80G** — temple/trust donation tax-exemption receipts. ❓ MVP or wave 2?

## 8. Open decisions (collected)

1. MVP = P0 + P1 (tenant+domain, Devotees, Donations), Events/Publishing wave 2? Or Publishing in MVP?
2. Multi-tenancy: shared schema + Postgres RLS — agreed?
3. Backend: FastAPI vs Django?
4. 80G receipts in MVP or later?
5. Final product name (SevaSetu is a placeholder).
