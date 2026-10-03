# ADR 0020: The trust's audit log

- Status: Accepted (2026-10-04)
- Context: Trusts handle donations, 80G receipts and devotees' personal data, and several people
  share the admin. Until now staff actions went only to the application log, which the trust
  can't see, and user management and devotee edits weren't recorded at all. Trustees need to answer
  "who changed this, and when".

## Decision

- **`audit_log` (V19) is append-only.** It has forced RLS, and the app role is granted only
  `SELECT, INSERT`. Nobody can edit or erase history through the app: no staff member, no bug, and
  not a compromised app.
- **An entry is written in the same transaction as the action** (`AuditTrail`, propagation
  `MANDATORY`). It commits exactly when the action does:
  - a rolled-back action leaves no entry;
  - an entry that can't be written rolls the action back;
  - calling it outside a transaction is a bug and fails loudly.
- **The actor comes from the security context**, never from a caller-supplied value.
- **No personal data:**
  - The action is a fixed code (`AuditAction`, with a DB CHECK on its shape).
  - The target is a type and an id.
  - The detail is a short value the code chooses (a role change, an amount, a row count), never
    user input. Names, phones and emails never land in the log.
- **What's recorded:**
  - User invited, role changed, deactivated, deleted; setup and reset links issued.
  - Devotee created, updated, erased, imported (one entry per import), exported.
  - Donation recorded or reversed; receipt issued; trust profile and payment settings saved.
  - Event status, puja catalog and booking cancellations, sevak reviews, temple page.
- **Reading it:** `GET /api/v1/audit`, TRUST_ADMIN only, newest first, filterable by action, sent
  as `no-store`. The **Audit log** screen shows it.

## Consequences

- **Each audited action is one extra insert**, written in its own transaction. That's negligible at
  trust scale.
- **Public and devotee actions** (bookings, signups, logins) stay in the application log. They
  aren't staff accountability events.
- **There's no retention policy yet.** The log grows forever; revisit with M6 backups and DPDP
  retention.
