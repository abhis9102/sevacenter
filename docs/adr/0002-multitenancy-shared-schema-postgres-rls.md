# ADR-0002: Multi-tenancy — shared schema + Postgres Row-Level Security

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

SevaCenter is multi-tenant: many temples/trusts share the platform, each strictly isolated.
Tenant isolation is the single highest-stakes correctness-and-security property. Options:
shared schema + `tenant_id`; schema-per-tenant; database-per-tenant.

## Decision

Shared database, shared schema, a `tenant_id` column on tenant-scoped tables, with **Postgres
Row-Level Security (RLS)** enforcing isolation at the database layer. The current tenant is
set per request (a session variable, e.g. `SET app.tenant_id`) resolved from the request
Host (subdomain).

## Rationale

- The most common, best-understood SaaS model; cost-efficient at our scale.
- **Defense in depth:** even if application code forgets a `WHERE tenant_id = ?`, RLS blocks
  cross-tenant reads/writes. Isolation doesn't depend solely on app correctness.
- Tenant isolation (BOLA) is our highest-stakes risk, so we test it adversarially: the work is
  implementing RLS correctly *and* hunting where it can be bypassed
  (a connection that forgets to set the tenant var; a role with `BYPASSRLS`; superuser).

## Consequences

- Must guarantee the tenant session variable is set on every DB connection/request, and
  that the app's DB role is **not** RLS-exempt. These become explicit test cases.
  Implemented by `TenantPinningDataSource` (pins on every connection checkout, so every access
  path is covered) and proven by `TenantIsolationTest`. See the threat model's invariant table.
- Noisy-neighbor and per-tenant backup/export are harder than DB-per-tenant (acceptable at
  our scale).
