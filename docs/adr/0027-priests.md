# ADR 0027: Priests (pujaris) and who performs each sankalp

- Status: Accepted (2026-10-04)
- Context: The roster showed which pujas were booked but not which priest performs them. Temples
  have several pujaris with different specialties, and the head priest assigns the day's sankalps.

## Decision

- **`priest` table** (forced RLS): name (unique per temple, case-insensitive), optional mobile,
  the pujas they perform, active flag. Priests are **deactivated, never deleted**, so past bookings
  keep the name of the priest who performed them.
- **`/api/v1/priests`**: MEMBER+ list (the mobile number only for LEADER+, like devotee contacts),
  LEADER+ add and edit. It sits under the Pujas module, so module limits apply.
- **`POST /api/v1/puja-bookings/{id}/priest`** (LEADER+): assign or clear the priest of an open
  booking; only an active priest of this temple. `puja_booking.priest_id` uses a composite foreign
  key `(tenant_id, priest_id)`, so the database refuses another trust's priest even if a check is
  missed. Audited (`PRIEST_SAVED`, `PRIEST_ASSIGNED`).
- The roster shows each sankalp's priest. The Pujas screen gets a third view, **Priests & Pujaris**.

## Consequences

- Priests are records, not staff logins. A priest who should mark pujas performed in the app is
  invited as a MEMBER, as before. Linking the two is a possible follow-up.
