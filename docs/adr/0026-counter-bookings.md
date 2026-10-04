# ADR 0026: Counter bookings and gate passes for walk-in devotees

- Status: Accepted (2026-10-04)
- Context: Pujas and darshan passes could only be booked by devotees themselves on the Mandir Center
  (ADR 0014, 0016). Temples take most bookings at the counter, often in cash, and at the gate on
  festival days, from people who give no phone or email.

## Decision

- **`POST /api/v1/puja-bookings`** (LEADER+, Pujas module): staff book a puja for a devotee. It is
  confirmed at once. A paid puja must say how the dakshina was taken (cash, UPI, card, cheque, bank
  transfer, optional reference). LEADER, like recording a donation, because money changes hands.
  Counter dakshina is seva income like online dakshina: no ledger row, no 80G receipt, no payment
  order.
- **`POST /api/v1/events/{id}/passes`** (MEMBER+, Events module): staff issue a pass at the counter
  or gate. It works after online registration closes, until the utsav ends, and still counts
  against capacity under the same row lock.
- **Contact optional for staff-made records.** The database rule becomes "a phone or email, or the
  staff member who made it" (`booked_by`, `issued_by`). A record with a contact still shows up in
  that devotee's My Mandir. Paid counter bookings must have a mode, enforced in the database.
- Both actions are in the audit trail (`PUJA_BOOKED_AT_COUNTER`, `PASS_ISSUED_AT_COUNTER`).

## Consequences

- Counter cash for pujas isn't reconciled against a till here. A daily counter report can be built
  later from `booked_by` and `counter_mode`.
