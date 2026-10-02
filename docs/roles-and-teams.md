# Roles & Teams — Operating Model

SevaCenter is built by a lean team (founder Abhi plus AI coding agents), run as a three-function
engineering org from day one. The separation is a real security control (separation of duties), and
it scales: each role becomes a team as the company grows.

## The three roles

| Role | Performed by | Responsibilities |
|---|---|---|
| **Development** | Coding agents (Antigravity / Claude Code) | Write application code — features, REST API, frontend, DB migrations. Open PRs. Follow the secure-coding + AI-declaration rules. |
| **AppSec** | **Abhi** | Threat modeling (STRIDE) per feature, secure code review of agent output, owning the CI security gates (SAST/SCA/secrets/IaC/image scan), pentesting the running app, and AI-generated-code governance. Reviews substantively — never prompt-and-merge. |
| **DevOps** | Abhi, with coding agents | CI/CD pipelines, Terraform / IaC, AWS infrastructure (**cloud lives here**), deployment, secrets management, monitoring/observability. |

**Cloud is not a separate team.** DevOps = CI/CD + IaC + cloud infra + deploy + monitoring. In large
orgs this splits into Platform/Cloud vs DevOps/SRE; at our scale it is one track.

## Why separate them (it's a control, not just tidiness)

- **Separation of duties:** Dev cannot bypass the AppSec gate; DevOps only deploys artifacts that
  passed it. No single role takes code from keyboard to production unchecked.
- **Forces clean boundaries:** each role's output is reviewed by the next.
- **Ready to scale:** when the team grows, roles are handed off without redesigning the process.

## The pipeline flow (shift-left)

```
 Dev (agent)              AppSec (Abhi) — automated gates in CI        DevOps
 ──────────               ────────────────────────────────────        ──────
 write code    ──PR──▶    pre-commit: gitleaks, trufflehog, semgrep
                          PR gate:   SAST (semgrep diff), SCA (osv),
                                     secrets, IaC (checkov), image (trivy)
                                     AI-code: provenance + hallucinated-pkg check
                          must pass  ───────────────────────────▶      deploy to
                          + Abhi's manual review on sensitive paths     AWS (staging→prod)
                                                                        + DAST on staging
```

Sensitive paths (auth, payments, tenancy, IAM/Terraform) always get Abhi's manual review on top of
the automated gates, regardless of what the scanners say.

## AI-generated-code governance (because agents are the developers)

Enforced from day one, because AI agents write most of the code:
- Commit trailers declaring AI authorship + tool + model.
- PR template with an AI-use declaration and a risk flag.
- Stricter review rules for AI-assisted changes touching sensitive paths.
- A check for hallucinated / non-existent dependencies (slopsquatting).
- Provenance is an *audit trail*, not a security control — security never depends on self-reporting.
