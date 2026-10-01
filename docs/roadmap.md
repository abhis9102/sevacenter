# Roadmap

MVP (locked 2026-10-02): tenant registration + per-temple subdomain, Devotees, Donations (with 80G),
Events/Registrations. Publishing + custom domains = wave 2.

Assume ~10 hrs/week around the day job. Each milestone ends with something that runs and that Abhi
can explain in an interview. Security work (AppSec role) runs *inside* every milestone, not after.

| # | Milestone | Dev (agents) build | AppSec (Abhi) does | DevOps | Target |
|---|---|---|---|---|---|
| **M0** ✅ | Repo + guardrails | Spring Boot skeleton (Maven), hello-world endpoint, Postgres via docker-compose | pre-commit hooks (gitleaks/trufflehog/semgrep), PR template + AI-declaration, threat-model the tenancy model; **Python** CI glue | local docker-compose | ~1 wk |
| **M1** 🚧 | Auth + tenancy core | registration, login/sessions (**Spring Security**), RBAC (trust-admin/leader/member via `@PreAuthorize`), **tenant model + Postgres RLS** (session var per request), Host-based tenant routing (servlet filter) | threat model auth+tenancy; test RLS bypass, Host-header tenant confusion, reserved subdomains, authz-per-endpoint | — | ~3-4 wks |
| **M2** | Devotees (People) | devotee CRUD, search, CSV import/export, invites | test IDOR/BOLA, cross-tenant leaks, mass-assignment, CSV/formula injection, export authz | — | ~2 wks |
| **M3** | Donations + 80G | Razorpay order + webhook, donation history, **80G receipt + PAN capture**, FY reporting | test amount tampering, webhook-signature bypass, idempotency/replay, refund abuse, PAN PII exposure; map to PCI SAQ + DPDP | — | ~3 wks |
| **M4** | Events/Registrations | public event pages, registration forms, (optional) file upload | test public-form abuse, rate limiting, upload validation, enumeration, CSRF | — | ~2 wks |
| **M5** | Containerize | Dockerfiles, compose parity | image scanning (Trivy) in the loop | Docker, local k8s optional | ~1-2 wks |
| **M6** | IaC + AWS deploy | — | review Terraform for IAM least-privilege, SG exposure, secrets handling; IaC scan (Checkov) | **Terraform**: ECS Fargate + RDS + ALB + Route53 + wildcard ACM + Secrets Manager (ap-south-1) | ~4-5 wks |
| **M7** | Full CI/CD gates | — | own the gate: SAST/SCA/secrets/IaC/image + AI-code provenance + hallucinated-pkg check; DAST on staging | GitHub Actions + **OIDC to AWS** (no static keys) | ~3 wks |
| **M8** | Observe + iterate | feature iteration, Publishing (wave 2) | ongoing pentest passes; harden; feed findings back as new rules | CloudWatch logs/metrics/alerts | ongoing |

**Totals (realistic, ~10 hrs/wk):** demonstrable end-to-end product with a secured pipeline on AWS
(M0-M7) ≈ **4-5 months**. Comfortable talking to all of it ≈ 6-7 months. EKS/custom domains are a
later stretch.

## Immediate next step

Start **M0**. Reuse the `~/appsec-pipeline-lab` security tooling (already installed: semgrep,
pre-commit, gitleaks, trufflehog, osv-scanner). First deliverable: repo skeleton + FastAPI
hello-world + Postgres in docker-compose + pre-commit guardrails + PR/AI-declaration template.
