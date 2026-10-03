# ADR 0015: Sevak (volunteer) signups

- Status: Accepted (2026-10-03)
- Context: MVP (MandirCenter operations). Devotees offer seva (kitchen, crowd management,
  festivals) and organisers follow up. These are public, unauthenticated submissions of personal
  contact data.

## Decision

- **Public signup.** `POST /api/v1/public/sevak` on the trust's host. Needs a name, seva areas,
  and a phone or email. Rate-limited per IP with the shared limiter. The response only echoes the
  name: no id, no status, no contact details.
- **Review.** LEADER+ sees signups, contacts included, and approves or declines them. Nobody
  else, including MEMBER, sees them.
- Server-owned fields (status, reviewer) are never read from the request.
- **No deletion** (no DELETE grant). Retention and erasure follow the devotee policy (ADR 0010)
  when signups are linked to devotee records later.
