# ADR-0005: Pin backend to Spring Boot 3.5 (not 4.x)

- **Status:** Superseded by [ADR-0008](0008-migrate-to-spring-boot-4.1.md) (2026-10-02): 3.5 OSS support ended 2026-06-30
- **Date:** 2026-10-02

## Context

The skeleton was first generated on Spring Boot 4.1 (then the Initializr default). Wiring up
springdoc-openapi for API docs surfaced the problem: springdoc's latest release (2.8.6,
Mar 2025) targets Spring Boot 3.x / Spring Framework 6 and has no Boot 4 (Spring 7) release.
This is representative — Boot 4 is very new and much of the ecosystem hasn't caught up.

## Decision

Pin the backend to **Spring Boot 3.5.3** (latest stable 3.5.x), with Spring Security 6.

## Rationale

- **Ecosystem maturity:** springdoc, Testcontainers integrations, libraries, tutorials, and
  community answers overwhelmingly target Boot 3.x. Fewer dead ends.
- **Market realism:** enterprises (the Java target market) run Boot 3.x, not bleeding-edge 4.
- **Foundation stability beats novelty** for a project meant to be built on reliably.

## Consequences

- Boot-3 starter names (`spring-boot-starter-web`, aggregate `spring-boot-starter-test`) and
  Spring Security 6 APIs. (No code change needed — the SecurityConfig lambda DSL + `build()`
  is identical across Security 6/7.)
- Revisit a Boot 4 upgrade once springdoc and the rest of our stack ship Boot 4 support.
