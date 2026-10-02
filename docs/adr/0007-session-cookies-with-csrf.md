# ADR-0007: Session cookies with CSRF protection (not bearer tokens)

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

The first run of M1 slice 1 showed `POST /api/v1/register` rejected by Spring Security's default
CSRF filter. That forced the auth-model decision ahead of slice 2: how does the browser (Next.js
admin app) prove who it is, and therefore does CSRF protection apply?

- **Session cookie:** the browser attaches it automatically to every request to our origin, including
  ones triggered by a malicious third-party page → CSRF is a real threat and must be defended.
- **Bearer token (JWT) in a header:** the browser never attaches it on its own → CSRF largely doesn't
  apply, but JavaScript must hold the token (stealable by any XSS) and revocation needs extra machinery.

## Decision

Server-side sessions in an **HttpOnly, Secure, SameSite** cookie, with **CSRF protection on for all
state-changing requests**, using the SPA double-submit pattern:

- `CookieCsrfTokenRepository.withHttpOnlyFalse()` puts the token in a JS-readable `XSRF-TOKEN` cookie.
- The client echoes it in the `X-XSRF-TOKEN` header. A cross-site page can't read our cookie
  (same-origin policy), so it can't forge the header.
- `GET /api/v1/csrf` hands out a token (and sets the cookie) before the first POST, `/register` included.

## Rationale

- JS never sees the session credential → an XSS bug can act *as* the user while the page is open,
  but can't steal a long-lived credential.
- Sessions are trivially revocable (logout, password change, admin kill-switch).
- Shrinking the CSRF surface by turning it off is the wrong trade for a browser-first admin app.

## Consequences

- Every non-GET request needs the token; non-browser API clients (later: integrations, webhooks
  such as Razorpay in M3) must be explicitly exempted *per path* with their own auth (signatures).
- `/error` must be a public path, otherwise CSRF/authz 403s surface as misleading 401s.
- **AppSec test cases:** POST without token → 403; with stale/foreign token → 403; GET never mutates
  state; cookie flags set correctly; CORS never allows credentialed cross-origin requests.
