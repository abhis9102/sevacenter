# ADR 0029: Deleting events and pujas

- Status: Accepted (2026-10-05)
- Context: Staff could cancel an utsav, a pass or a booking, and hide a puja, but never get rid of
  one: a draft made by mistake or a puja the temple stopped offering stayed on their screens for
  good. Events, passes and pujas were deliberately never deleted (ADR 0014, 0016), because devotees
  hold passes and bookings against them and paid dakshina must stay traceable.

## Decision

- **`DELETE /api/v1/events/{id}`** (LEADER+, Events module): only a **draft or cancelled** event.
  A published one answers 409 `cancel_first`, so registered devotees are told "cancelled" instead
  of holding a pass that silently stops working. A database check enforces the same rule.
- **`DELETE /api/v1/pujas/{id}`** (LEADER+, Pujas module): refused with 409 `has_open_bookings`
  while the puja has a booking awaiting payment or confirmed for today or later. Those are performed
  or cancelled first; staff who only want it off the Mandir Center hide it instead.
- **Delete sets `deleted_at` / `deleted_by`; the row stays.** The app role still has no `DELETE`
  on `event`, `event_pass` or `puja`. A deleted row is gone from every list, lookup and action
  (staff, public, gate check-in, counter); history reads keep it: a devotee's My Mandir and the
  Devotee 360 still show their (cancelled) pass, and past bookings keep their own copy of the
  puja's name.
- **Races:** a booking takes a shared lock on its puja and delete takes an exclusive one, so a
  delete waits for an in-flight booking and then sees it. Edits, status changes and delete of an
  event all take the event row lock that seat counting already uses.
- Both are in the audit trail (`EVENT_DELETED`, `PUJA_DELETED`).

## Consequences

- Delete is not erasure: names and contacts on old passes and bookings remain, as for removed
  sevaks (ADR 0028). Erasure on request is a separate admin path.
- The prototype's hard delete of passes and puja bookings is not adopted: cancelling them already
  exists and keeps the money trail.
