# Security Policy

SevaCenter handles devotee PII and donation/payment data, so security is a first-class
concern, not an afterthought. This file is the entry point to how we build securely.

## Secure-by-design principles

- **Authorization is always server-side.** The UI may hide controls by role, but every
  decision is enforced in the backend. The client is never trusted.
- **Tenant isolation at the data layer.** Shared-schema multi-tenancy is enforced with
  Postgres Row-Level Security, not only by application `WHERE` clauses (see ADR-0002).
- **No secrets in source or client bundles.** Config comes from the environment; CI runs
  gitleaks + trufflehog; the frontend bundle is scanned for leaked secrets.
- **Least privilege** for IAM, DB roles, and agent/tool access.
- **Payments:** card data never touches our servers — Razorpay hosts it; we stay in the
  lowest PCI SAQ scope. Donor PAN (for 80G) is treated as sensitive PII under the DPDP Act.
- **Fail closed:** errors don't leak internals; a missing config stops the app.

## The pipeline gate (shift-left)

Pre-commit (local) → PR CI gate → deploy. See `docs/roles-and-teams.md`. The security gate
grows over the roadmap: secret scanning + SAST from the start, full SAST/SCA/IaC/image/SBOM
and AI-code provenance by M7.

## Compliance targets

OWASP ASVS, OWASP Top 10 & API Security Top 10, PCI DSS (via Razorpay), ISO 27001 controls,
and India's DPDP Act 2023. Threat models live under `docs/security/`.

## Reporting a vulnerability

For now, report issues privately to the maintainer
rather than opening a public issue.
