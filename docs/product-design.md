# Product Design — v1

Core decisions locked 2026-10-02 (see §8). Remaining open items still marked ❓.

**Decisions locked:** MVP = tenant+domain + Devotees + Donations + **Events/Registrations**;
multi-tenancy = **shared schema + Postgres RLS**; backend = **FastAPI**; **80G receipts in MVP**.

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
| **Registrations** | **Events + Registrations** | **P1 — in MVP** | Public forms, uploads, rate limiting. |
| **Publishing** | **Announcements / public page** | P2 (wave 2) | Mini-CMS; content authz, stored XSS surface. |
| Services / Check-Ins / Groups / Calendar | — | later | Little *new* security surface for the effort. |

**MVP (locked) = tenant registration + subdomain, Devotees, Donations (with 80G), Events/
Registrations.** Covers multi-tenancy + RBAC + PII + money + public forms. **Publishing is wave 2.**

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

**LOCKED:** shared DB + `tenant_id` + **Postgres Row-Level Security (RLS)** enforcing it at the DB
layer. Most common real-world SaaS model, and implementing RLS correctly (plus finding where app
code can bypass it — e.g. a connection that forgets to `SET app.tenant_id`, or a superuser role that
bypasses RLS) is a strong, demonstrable security story.

## 5. Tech stack (proposal)

| Layer | Choice | Notes |
|---|---|---|
| Backend / API | **Python + FastAPI** (LOCKED) | You know Python; API-first; clean for security demos |
| DB | **PostgreSQL** (RLS for tenancy) | — |
| Frontend | **Next.js** (admin + public), subdomain routing via middleware | FE is secondary; keep minimal |
| Auth | Vetted library (don't hand-roll); session/JWT | Effort goes into authz, not reinventing auth |
| Payments | **Razorpay** (UPI/cards) | Indian rail; webhook signature verification |
| Containers | Docker + compose (local) | — |
| IaC | **Terraform** | Authoring, not just scanning |
| Cloud | AWS **ap-south-1**: ECS **Fargate** + RDS Postgres + ALB + Route53 + wildcard **ACM** cert + Secrets Manager | Skip EKS early; Fargate first |
| CI/CD | **GitHub Actions + OIDC to AWS** | No static keys — strong signal |
| Pipeline security | Semgrep, osv-scanner/pip-audit, gitleaks+trufflehog, Checkov, Trivy, syft, cosign | Reuse `~/appsec-pipeline-lab` |

FastAPI chosen over Django (leaner, explicit, API-first). We wire auth/authz ourselves, which is
where the security learning is.

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
- **80G** — temple/trust donation tax-exemption receipts. **IN MVP.** Pulls in **donor PAN capture**
  (sensitive PII — extra care), the trust's 80G registration number in trust settings, a compliant
  receipt format, and financial-year reporting. Good PII + compliance surface.

## 8. Decisions

**Locked (2026-10-02):**
1. MVP = tenant+domain, Devotees, Donations (with 80G), **Events/Registrations**. Publishing = wave 2.
2. Multi-tenancy = shared schema + `tenant_id` + **Postgres RLS**.
3. Backend = **FastAPI**.
4. **80G receipts in the MVP** (brings donor PAN handling into scope).

**Still open ❓:**
5. Final product name (SevaSetu is a placeholder).
6. Custom domains (`donate.sometemple.org`) — confirmed wave 2, subdomain-only in MVP.
7. Public member/donor access — own login vs link/OTP in v1.
