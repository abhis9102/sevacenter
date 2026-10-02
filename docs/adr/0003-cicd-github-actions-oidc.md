# ADR-0003: CI/CD — GitHub Actions + OIDC to AWS

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

We need CI (build/test + security gates) and CD (deploy to AWS). Candidates: GitHub Actions,
GitLab CI, Jenkins, Azure DevOps.

## Decision

**GitHub Actions** as the pipeline, authenticating to AWS via **OIDC** (short-lived,
federated credentials — no long-lived access keys stored as secrets).

## Rationale

- Code is already on GitHub; zero CI infra to run.
- OIDC → keyless deploys: a strong, concrete security signal.
- Native SARIF upload to GitHub code scanning for security findings.
- Free for public repositories, including GitHub's code scanning, secret scanning and push protection.

## Consequences

- Deploy role in AWS must trust GitHub's OIDC provider, scoped to this repo/branch.
- No self-hosted CI to operate or harden; revisit if enterprise customers require on-prem CI.
