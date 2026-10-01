# ADR-0006: Two brands — SevaCenter (admin) & MandirCenter (public), two domains

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

The platform serves two very different audiences: trust *staff* doing back-office work, and
*devotees* who come to donate and sign up for events. Planning Center solves this with two brands —
the admin platform (Planning Center) and the public, per-church site (Church Center).

## Decision

Adopt the same split:

- **SevaCenter** — the admin platform for trust staff, at `app.sevacenter.app`.
- **MandirCenter** — the public devotee site, one per temple, at `yourtemple.mandircenter.app`.

Two separate domains (`sevacenter.app`, `mandircenter.app`), one shared design system with two
accent palettes (SevaCenter restrained; MandirCenter the vibrant Hindu devotional palette).

## Rationale

- Clear mental model and clear security boundary: staff tooling vs. public, tenant-branded pages.
- MandirCenter is where a temple's devotees experience *their* temple, so it carries the temple's
  identity and a warmer, devotional look; the admin stays calm and efficient.
- Matches the proven Planning Center / Church Center pattern.

## Consequences

- **Tenant routing** resolves a tenant from the request Host on *both* domains — the admin host
  (identify the staff member's tenant) and the public per-temple subdomain. Build M1 routing to
  handle both.
- **Infra (M6):** two Route53 zones and two ACM certs (a wildcard `*.mandircenter.app` for temple
  subdomains, plus `app.sevacenter.app`). More cost/complexity, accepted for realism.
- Reserved-subdomain blocklist applies to `*.mandircenter.app` (`www`, `api`, `admin`, …).
- Per-temple accent theming on MandirCenter is a possible later feature.
