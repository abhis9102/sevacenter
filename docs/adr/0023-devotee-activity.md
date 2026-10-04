# ADR 0023: A devotee's activity on their record

- Status: Accepted (2026-10-04)
- Context: Staff asked to see, on a devotee's page, everything that person has done with the temple:
  donations, puja bookings, event passes and seva offers. A first version, written against an
  earlier model, returned every section to anyone who could open the devotee. It bypassed per-module
  limits (ADR 0021), let members see financial history, and counted reversed donations in the
  total.

## Decision

- **`GET /api/v1/devotees/{id}/activity`** is one read for MEMBER+ with Devotees access. It returns
  404 for erased devotees, unknown ids and other trusts' devotees (RLS).
- **Each section follows the rules of the module it comes from, not the path:**

  | Section | Needs |
  |---|---|
  | Donations | LEADER+ and Donations access |
  | Puja bookings | MEMBER+ and Pujas access |
  | Event passes | MEMBER+ and Events access |
  | Seva offers | LEADER+ and Volunteers access (seva contacts are LEADER-only, ADR 0015) |

  A section the caller can't see is **null**, distinct from an empty list, so "not allowed" never
  looks like "none".
- **Donations come from the ledger link** (`donation.devotee_id`). The **net includes reversals**,
  so a reversed gift counts zero. Reversal entries aren't listed; the donation they undo is marked.
  The receipt number is shown; the PAN is never included.
- **Bookings, passes and seva offers match the devotee's phone or email exactly**, the same rule as
  "my seva" (ADR 0018). That means a family sharing one phone shows the family's activity, which is
  the expected trade-off.
- The response is `no-store`.

## Consequences

- **It's read-only and adds no new data.** Each section is at most as visible as the screen it comes
  from.
- **Online donations aren't linked to a devotee record**, because the checkout has no `devotee_id`.
  They appear in the donor's own "my seva" instead (ADR 0019). Linking them on staff confirmation is
  a possible follow-up.
