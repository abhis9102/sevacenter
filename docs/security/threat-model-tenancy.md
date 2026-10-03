# Threat Model: Tenancy, Registration & Auth (M0/M1)

STRIDE threat model for SevaCenter's foundation — the per-temple registration/subdomain flow,
multi-tenancy, and authentication. Written at M0 (before building M1) so the design is shaped
by it, not patched after. Revisit each milestone.

## Assets

- Devotee PII; donation/payment records; donor PAN (80G).
- Tenant isolation boundary (the core security property).
- Admin/leader credentials and sessions.

## Trust boundaries

- Internet → ALB/app (public). Tenant identity derived from the request **Host** (subdomain).
- App → Postgres (RLS boundary). App → Razorpay (external).
- Role boundaries within a tenant: `trust-admin` > `leader` > `member/donor`.

## STRIDE

| Threat | Example in SevaCenter | Mitigation |
|---|---|---|
| **Spoofing** | Forged/ambiguous `Host` header to act as another tenant; subdomain takeover of a dangling tenant | Canonical Host validation + allowlist of provisioned tenants; no wildcard fallthrough; monitor dangling DNS |
| **Tampering** | Manipulating `tenant_id`, IDs, or donation amount in requests | Server-side authz; never trust client IDs; **RLS** enforces tenant scope; server-side payment amounts |
| **Repudiation** | Admin denies a sensitive action (refund, data export) | Audit log of security-relevant actions (who/what/when/tenant) |
| **Information disclosure** | Cross-tenant data read via a query missing `tenant_id`; tenant enumeration via subdomain/login responses; secrets in JS bundle | RLS as the backstop; uniform responses to avoid enumeration; secret scanning of bundle |
| **Denial of service** | Registration/donation/login abuse; expensive endpoints | Rate limiting; captcha on public forms; pagination/query caps |
| **Elevation of privilege** | `member` → `trust-admin`; a DB role with `BYPASSRLS`; reserved subdomain (`admin`, `api`) | `@PreAuthorize` method authz; app DB role is **non-superuser, non-BYPASSRLS**; reserved-subdomain blocklist |

## Key invariants (become tests)

1. Every DB connection has `app.tenant_id` set before any tenant-scoped query runs.
2. The application's DB role **cannot** bypass RLS.
3. No endpoint returns data for a tenant other than the caller's — verified with a two-tenant test.
4. Reserved subdomains (`www`, `api`, `admin`, `mail`, …) cannot be registered by a tenant.
5. Authorization is enforced server-side even when the UI hides the control.

### Status: invariant → test (`backend/src/test/java/app/sevacenter/TenantIsolationTest.java`)

Tests run as the least-privilege `sevacenter_app` role, like production; the Testcontainers
superuser would bypass RLS. Every test was mutation-checked: each fails when its control is removed.

| # | Enforced by | Tests |
|---|---|---|
| 1 | `TenantPinningDataSource` pins `app.tenant_id` on **every connection checkout** (or `''` = none); V3 policy treats `''` as unset | `noTenantPinnedSeesNothing`, `tenantPinDoesNotLeakIntoTheNextTransaction`, `repositoriesOnlySeeTheTenantInContext` |
| 2 | Non-superuser, `NOBYPASSRLS`, not table owner; `FORCE ROW LEVEL SECURITY` | `appConnectsAsLeastPrivilegeRoleThatRlsAppliesTo`, `appRoleCannotSwitchRlsOff`, `everyTenantScopedTableHasForcedRlsAndAPolicy` (also guards **future** tables) |
| 3 | RLS `USING` + `WITH CHECK` | `pinnedTenantSeesOnlyItsOwnRows`, `tenantCannotInsertIntoAnotherTenant`, `tenantCannotUpdateOrDeleteAnotherTenantsRows`, `repositoriesOnlySeeTheTenantInContext` (BOLA by id) |
| 4 | `ReservedSlugs`, shared by registration and routing | `reservedSubdomainsCannotBeRegistered` |
| 5 | `@PreAuthorize` on every user-management endpoint, over a TRUST_ADMIN > LEADER > MEMBER hierarchy; authorities re-read from the DB on every request | `membersCannotListUsersLeadersAndAdminsCan`, `onlyAdminsCanCreateChangeRolesDeactivateOrReissueLinks`, `aDemotionAppliesToTheUsersVeryNextRequest` (`UserManagementTest`) |

### Staff authentication (M1 slice 2a, ADR 0009): threat → control → test

| Threat | Control | Test |
|---|---|---|
| Host-header tenant confusion (`Host: siddheshwar.attacker.example`) | Tenant resolved only from `<slug>.<configured base domain>` | `TenantHostResolutionTest` (10 hosts) |
| Logging in to another tenant | User lookup scoped by RLS to the Host's tenant | `staffCannotLogInOnAnotherTenantsHost`, `noTenantHostMeansNoLogin` |
| Session replayed on another tenant's host | `StaffSessionFilter`: 401 + session invalidated | `aSessionIsWorthlessOnAnotherTenantsHostAndIsDestroyed` |
| Session fixation | Framework form login: session ID changes at login | `sessionIdChangesAtLogin` |
| Login CSRF | CSRF required on login; token rotates after login | `loginRequiresCsrf`, `SessionCookieTest` |
| User enumeration | One generic 401; dummy hash check for unknown users | `wrongPasswordAndUnknownEmailAreIndistinguishable` |
| Brute force / password spraying | Per-account + per-IP lockout (5 / 15 min), 429 even for the right password | 3 throttle tests in `StaffAuthTest` |
| Session theft via script / cross-site | `SC_SESSION`: HttpOnly, Secure, SameSite=Lax | `SessionCookieTest` (real server) |

All mutation-checked (removing the binding filter, the throttle, fixation protection or the
cookie flags turns the matching test red).

### Staff management (M1 slice 2b): threat → control → test

Tests in `UserManagementTest` unless noted. 22 mutations, all killed.

| Threat | Control | Test |
|---|---|---|
| Member/leader manages users | `@PreAuthorize` (invariant 5) | `onlyAdminsCanCreateChangeRolesDeactivateOrReissueLinks`, `membersCannotListUsersLeadersAndAdminsCan` |
| Mass assignment (`tenantId`, `status`, `passwordHash` in the body) | Explicit request records; new users are always PENDING in the caller's tenant | `tenantStatusAndPasswordInTheRequestBodyAreIgnored` |
| BOLA: acting on another tenant's user id | RLS hides it → 404 | `anotherTenantsUserIdIsNotFound` |
| Setup link leaked from the DB | Only SHA-256 stored; token in the URL fragment (not logged, no Referer) | `newUsersArePendingAndOnlyTheTokenHashIsStored` |
| Setup link replayed / redeemed late / redeemed twice concurrently | Single use, 72 h expiry, row lock on redemption | `aSetupLinkWorksOnceAndThenTheUserCanLogIn`, `aUsedTokenIsRejectedEvenWhileTheUserIsStillPending`, `expiredLinksAreRejected`, `aTokenIsLockedWhileBeingRedeemed` |
| Setup link used as a password reset (account takeover) | Only a PENDING user can be set up; links only issued to PENDING users | `aValidTokenCannotResetAnActiveUsersPassword`, `linksAreOnlyIssuedToPendingUsers` |
| Setup link redeemed on another tenant's host | `user_setup_token` under forced RLS | `aLinkOnlyWorksOnItsOwnTenantsHost`, `everyTenantScopedTableHasForcedRlsAndAPolicy` |
| Stale links after reissue / deactivation | Reissue and deactivation delete the user's links | `reissuingALinkKillsTheOldOne`, `deactivatingAPendingUserDeletesTheirLink` |
| Weak first password | ≥ 12 characters | `weakPasswordsAreRefusedAtSetup` |
| Trust locked out (no admin left), incl. two admins demoting each other at once | Last-admin check counted under a row lock | `theLastAdminCannotBeDemotedOrDeactivated`, `adminsAreCountedUnderARowLock` |
| Deactivated / demoted user keeps acting until the session expires | `StaffSessionFilter` re-reads the user on every request | `deactivationEndsTheUsersSessionOnTheirNextRequest`, `aDemotionAppliesToTheUsersVeryNextRequest` |
| Session replayed on another tenant's host if RLS were misconfigured | The filter's own tenant check, independent of RLS | `StaffSessionFilterTest` (unit) |

**Found by mutation testing:** 6 of the first 22 mutations survived. Each was a defence that
another layer covered for in end-to-end tests: e.g. a reusable token was masked by the PENDING
check and vice versa, and RLS masked the filter's tenant check. Each layer now has a test
that defeats the other layers first.

Accepted for now: setup links are returned to the admin to deliver (no email yet), so the
admin can see them. Email delivery is a later slice.


**Found while writing these tests (all fixed):**
- **Every test ran as a superuser**, so RLS had never actually been tested.
- **The RLS policy crashed instead of failing closed** on reused pooled connections: after a
  transaction-local `set_config`, Postgres resets the setting to `''`, not NULL, and `''::bigint`
  errors. Fixed by V3 (`nullif`).
- **Repository calls were never pinned**: the former `RlsTenantAspect` only fired for our own
  `@Transactional` classes. Replaced by pinning at connection checkout.
- **Reserved subdomains could be registered**: they were only skipped during routing.

Known limit: RLS stops application *bugs*, not SQL injection. Injected SQL runs as the same role
and can call `set_config` itself. Injection is covered by SAST, DAST and parameterized queries.

## Open questions

- Public donor access: own login vs. link/OTP (affects the auth surface).
- Custom domains (wave 2): per-tenant cert issuance + domain-ownership verification.
