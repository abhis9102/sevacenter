# ADR 0014: Events and registration passes

- Status: Accepted (2026-10-03)
- Context: M4. Temples run festivals, darshan slots and satsangs. Devotees register (often for
  family groups) and show a pass at the gate, where volunteers check them in. Threats: overselling
  limited places, guessable or reusable passes, PII exposure at the gate, spam registrations, and
  cross-trust access.

## Decision

**Events.** Staff (LEADER+) create, edit, publish and cancel an event. An event has a title, a
description, start and end times, an optional capacity, and an open/closed registration switch.
Lifecycle: DRAFT → PUBLISHED → CANCELLED. Only PUBLISHED events are public. Nothing is ever deleted
(the app role has no DELETE grant).

**Registration** (public, on the trust's host, no sign-in):
- A pass covers 1–10 people, needs a name and a phone or email, and closes when the event starts.
- **Capacity:** seats are counted under the event row lock in the same transaction as the insert,
  so simultaneous registrations can't oversell.
- **Pass code:** 10 characters from a 32-symbol alphabet without 0/O/1/I (50 bits, `SecureRandom`),
  unique per trust. RLS means a code only resolves on its own trust's host.
- Rate-limited per client IP (shared limiter with online donations).
- Server-owned fields (pass code, check-in, status) are never taken from the request.

**Gate check-in** (MEMBER+, for volunteers):
- Enter or scan a code, with case, spaces and dashes tolerated.
- Each pass checks in exactly once, under a row lock. A second scan reports when it was used.
- A pass for another event, a cancelled pass, or garbage are all refused.
- The gate sees only the name and head count. Contact details are visible to LEADER+ only.

**Paid passes** (and paid pujas) come in a later slice, through the M3.3 verified-payment flow.

## Consequences

- QR codes can encode the same pass code later, with no schema change.
- `EventTest`. Every row is also in `authz_probe.py`.
