# ADR-0001: Polyglot — Java/Spring Boot app, Python security tooling

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

SevaCenter needs a backend that is secure by default, long-term maintainable, and easy to hire
for, plus security and pipeline tooling that the security function can own and change quickly.

## Decision

- **Application backend: Java 21 + Spring Boot** (Spring Security, Spring Data JPA).
- **All security/pipeline tooling: Python** (custom checks, CI glue, AI-assisted triage).
- Frontend: Next.js/TypeScript. IaC: Terraform/HCL.

## Rationale

- Java/Spring Boot: mature, well-supported, a large hiring pool in India, and strong enterprise
  credibility with larger trusts and institutional customers.
- **Spring Security** gives battle-tested authn/authz, CSRF, session management and method
  security, so we don't hand-roll security primitives.
- Python is the lingua franca of security tooling (scanners, SARIF, APIs), so the security team
  can build and change pipeline checks quickly without touching the app.
- The split mirrors a real org (app engineers in one language, security engineering in another),
  and the pipeline scans every language we ship (Java, JS, HCL).

## Consequences

- Slower initial build than an all-Python stack (accepted for long-term maintainability).
- Two toolchains to maintain (Maven + Python). Python tooling is stdlib-first to keep its own
  dependency surface near zero.
