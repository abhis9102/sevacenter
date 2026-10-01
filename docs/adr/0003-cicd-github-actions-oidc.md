# ADR-0003: CI/CD — GitHub Actions + OIDC to AWS

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

We need CI (build/test + security gates) and CD (deploy to AWS). Candidates: GitHub Actions,
GitLab CI, Jenkins, Azure DevOps. Target JDs list several "or similar" — concepts transfer.

## Decision

**GitHub Actions** as the pipeline, authenticating to AWS via **OIDC** (short-lived,
federated credentials — no long-lived access keys stored as secrets). Jenkins is a planned
wave-2 side exercise (enterprise relevance + Jenkins hardening is itself an AppSec topic).

## Rationale

- Code is already on GitHub; zero CI infra to run.
- OIDC → keyless deploys: a strong, concrete security signal.
- Native SARIF upload to GitHub code scanning for security findings.
- Free for public repos; the project is public portfolio.

## Consequences

- Deploy role in AWS must trust GitHub's OIDC provider, scoped to this repo/branch.
- Not exposed to self-hosted CI operations by default — addressed by the wave-2 Jenkins
  exercise.
