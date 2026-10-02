# ADR-0004: Schema managed by Flyway; Hibernate validate-only

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

The schema must evolve safely across environments and over time, and must be reviewable and
auditable (it enforces tenant isolation and holds PII/payment data).

## Decision

All schema changes are **Flyway migrations** (`V<n>__description.sql`), forward-only and
immutable once applied. Hibernate runs with `ddl-auto: validate` — it never creates or alters
schema, only checks that entities match the migrated schema.

## Rationale

- Deterministic, versioned, peer-reviewable schema — no "it works on my machine" drift.
- `validate` catches entity/schema mismatches at startup instead of at runtime.
- Migrations are a security artifact too (RLS policies, constraints live there and get reviewed).

## Consequences

- Every schema change is a migration + matching entity change in the same PR.
- Applied migrations are never edited; corrections ship as new migrations.
