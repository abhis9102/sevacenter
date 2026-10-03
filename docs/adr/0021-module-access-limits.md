# ADR 0021: Per-module access limits, enforced by the server

- Status: Accepted (2026-10-04)
- Context: Trusts asked to restrict individual staff further than the three roles allow. For
  example, an accountant who handles donations but shouldn't see devotees, or a volunteer
  coordinator who should only read the puja schedule. A UI-only version of this was proposed: it
  hid menu items while every API stayed open. That's not access control, so it was rebuilt here.

## Decision

- **Modules:** Devotees, Donations & receipts (including the trust profile), Events, Pujas,
  Volunteers, Temple page. Staff management, payment settings and the audit log aren't modules.
  They stay TRUST_ADMIN-only by role and can't be delegated.
- **A per-user limit per module is either `VIEW` (read-only) or `NONE` (no access).** It's stored
  canonically on `app_user.module_limits` (V20, with a DB CHECK on the format). The absence of a
  limit means whatever the role allows.
- **Limits only take away.** Effective access is the role, then minus the limits; a limit can never
  grant more than the role. A TRUST_ADMIN is never limited: the API refuses with
  `admins_not_limited`, and promotion clears limits. So a trust can't lock itself out of its own
  admin.
- **Enforced on every API call** by `ModuleAccessInterceptor`, on top of the `@PreAuthorize` role
  checks:
  - `NONE` → 403 for the whole module;
  - `VIEW` → 403 for anything but GET/HEAD;
  - the same 403 body as a role denial.
- **One table maps path prefixes to modules**, matching whole segments only. `ModuleCoverageTest`
  fails if any `/api/v1` endpoint is in neither a module nor the explicit non-module list, so a new
  endpoint can't slip past unmapped.
- **Applies on the next request.** `StaffSessionFilter` already rebuilds the principal from the
  database on every request, so the limits come with it. There's no re-login and no stale session.
- **Only a TRUST_ADMIN sets limits** (`PUT /api/v1/users/{id}/module-access`), and every change is
  audited as `USER_ACCESS_CHANGED` with the new limits (ADR 0020).
- **The UI hides what the server denies:** nav items for `NONE` modules are hidden, and the Staff
  screen has a per-user "Access" editor. The UI is a hint; the server is the control.

## Consequences

- **Read-only is by HTTP method.** Every state change in the API is a non-GET; that's already a
  CSRF invariant, so it holds.
- **Finer grants**, like a field-level PII view, stay with the roles (ADR 0010 masking). They aren't
  per-module levels.
