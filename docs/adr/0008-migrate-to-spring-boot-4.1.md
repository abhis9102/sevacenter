# ADR-0008: Migrate to Spring Boot 4.1 (3.5 is end-of-life)

- **Status:** Accepted. Supersedes [ADR-0005](0005-pin-spring-boot-3.5-lts.md).
- **Date:** 2026-10-02

## Context

The first SCA scan (G3) of the backend on Spring Boot 3.5.3 found **82 known vulnerabilities
(9 critical, 34 high)**, all in transitive dependencies. Checking the upgrade path showed the real
problem: **Spring Boot 3.5's open-source support ended on 2026-06-30**. Its last release, 3.5.16,
still carried 19 vulnerabilities (3 critical), and nothing published after June will ever be
fixed in a free 3.5.x release. ADR-0005 picked 3.5 for ecosystem maturity, but missed that the
line was already EOL.

| Option | Vulns | Effort | Supported |
|---|---|---|---|
| Stay on 3.5.3 | 82 (9 crit) | none | No |
| 3.5.16 + overrides | ~0 for now | small | No: decays from here |
| **Boot 4.1** | 0 after 3 patch overrides | migration | Until 2027-07-31 |

## Decision

Migrate to **Spring Boot 4.1.1** now, while the codebase is ~20 classes and migration is cheap.
Keep Java 21.

What changed (`backend/pom.xml`):
- Starters renamed/modularized: `starter-web` → `starter-webmvc`, `starter-aop` →
  `starter-aspectj`, `flyway-core` → **`starter-flyway`**, test starters split per module.
- **Flyway trap:** in Boot 4, auto-configuration lives in the starter. With bare `flyway-core` the
  app compiles and starts but **never runs migrations**.
- Testcontainers 2.0 (`testcontainers-postgresql`, new `org.testcontainers.postgresql` package),
  springdoc 3.1, Spring Security 7, Hibernate 7, Jackson 3 (Jackson 2 still present transitively).
- Three patch-level CVE overrides (`tomcat.version`, `jackson-bom.version`,
  `jackson-2-bom.version`), each annotated with its advisories. Remove them once Boot catches up.

**Behavior-preserving on purpose:** CSRF config is unchanged, even though Security 7 adds
`csrf.spa()`, because that would change the token format `/api/v1/csrf` returns. Behavior changes
get their own PR.

## Verification

- `./mvnw verify` green; Flyway applied V1+V2 on Boot 4.
- Live checks identical to 3.5.3: ping 200, no token 403, forged token 403, valid 201,
  duplicate 409, bad body 400, protected 401, error bodies leak nothing, RLS by role 0/1/0.
- osv-scanner: 17/17 declared dependencies resolved, 167 packages, **0 vulnerabilities**.

## Consequences

- Framework support until 2027-07-31. Track the next line (4.2) ahead of that.
- Overrides need maintenance; Dependabot (maven) proposes Boot bumps, and majors get a 30-day
  cooldown because they need a planned migration.
- Lesson recorded in `docs/security/sca.md`: an early 4.1 test scan reported **0 vulnerabilities
  because the scanner resolved only 15 of 183 packages** (old starter names). Hence the coverage gate.
