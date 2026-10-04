# ADR 0028: Sevak Hub: seva teams, shifts, and staff-managed volunteers

- Status: Accepted (2026-10-04)
- Context: Volunteers could only offer seva on the Mandir Center and be approved or declined
  (ADR 0015). Temples run seva as teams (annadanam kitchen, darshan queue, decoration…) with
  shifts and a target headcount, register volunteers at the office, and deploy them to teams.

## Decision

- **Seva teams** (`seva_team`, forced RLS) are the volunteer activities: name (unique among live
  teams), description, optional target headcount, and up to 8 **shifts** (`seva_shift`, forced RLS,
  replaced as a whole on save). A shift may run past midnight. LEADER+ create, edit and **delete**.
  Delete sets `deleted_at`, releases the team's volunteers and frees the name; the row stays for
  the audit trail.
- **Staff register volunteers** (`POST /api/v1/sevaks`), approved by default and optionally placed
  in a team at once. A phone or email is still required: the temple needs to reach a sevak.
- **Deploy / release** (`POST /api/v1/sevaks/{id}/assign`): a team (or null) and a duty / shift.
  Deploying a pending offer approves it; a declined offer can't be deployed.
- **Remove a volunteer** (`DELETE /api/v1/sevaks/{id}`): gone from the hub, the devotee record and
  My Mandir; the row stays (`removed_at`) for the audit trail.
- Team and volunteer links use composite foreign keys `(tenant_id, team_id)`; everything sits under
  the Volunteers module and is audited.

## Consequences

- Removing is not erasure. A volunteer asking for their data to be erased under the DPDP Act is a
  separate, admin-only path (as for devotees, ADR 0010), still to be added for sevaks.
