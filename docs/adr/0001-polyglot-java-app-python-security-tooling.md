# ADR-0001: Polyglot — Java/Spring Boot app, Python security tooling

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

SevaCenter is both a product and a DevSecOps/AppSec training vehicle serving two career
tracks: offensive/AppSec (Python-strong) and general SWE (Java, in progress). The backend
language choice had to serve those, not merely copy Planning Center (which is Ruby on Rails).

## Decision

- **Application backend: Java 21 + Spring Boot** (Spring Security, Spring Data JPA).
- **All security/pipeline tooling: Python** (custom checks, CI glue, AI-assisted triage).
- Frontend: Next.js/TypeScript. IaC: Terraform/HCL.

## Rationale

- Java/Spring Boot feeds the SWE track and has large enterprise/Indian-market demand.
- **Spring Security is where enterprise authn/authz lives** — learning it deeply makes the
  maintainer a better AppSec engineer, since most apps they'd later secure are Java/Spring.
- Python for tooling plays to existing strength and matches what AppSec job postings want.
- The polyglot split mirrors a real org (app devs in one language, security eng in another)
  and yields a richer DevSecOps story (pipeline scans Java + JS + HCL).
- **Not Ruby/Rails:** we copy Planning Center's product *shape*, not its stack; Ruby serves
  neither track.

## Consequences

- Slower initial build than an all-Python stack; the maintainer reviews agent-written Java
  while still building Java fluency (mitigated: authz/injection instincts transfer; agents
  assist).
- Two toolchains to maintain (Maven + Python), which is intentional for the learning value.
