# SevaCenter admin web app

The staff admin for trusts: sign in, account setup from a one-time link, staff management and
devotee records (search, view, create/edit, erase, CSV import/export). Built with Next.js (App
Router), TypeScript (strict) and Tailwind, against the SevaCenter API in `../backend`.

This is the **SevaCenter** brand (restrained palette, `docs/design/design-system.md`). The
vibrant MandirCenter palette belongs to the public temple sites, not here.

## Run it locally

Prereqs: Node.js 20.9+ (22 or 24 LTS recommended), the backend running on `:8080`
(`make db-up && make run` from the repo root).

```bash
cd frontend
npm ci                      # exact versions from package-lock.json; install scripts are disabled (.npmrc)
npm run dev                 # http://<slug>.localhost:3000  (PORT=3001 npm run dev for another port)
```

Then open **`http://<your-trust-slug>.localhost:3000`**, for example
`http://siddheshwar.localhost:3000`. To create a trust and its first admin, register one through
the API (recipe in `docs/STATUS.md`; it works through `:3000` too).

| Script | What it does |
|---|---|
| `npm run dev` | dev server with hot reload |
| `npm run build` / `npm start` | production build / serve it |
| `npm run lint` | ESLint (Next.js + TypeScript rules, plus our security rules), zero warnings allowed |
| `npm run typecheck` | `tsc --noEmit`, strict |
| `npm test` | unit tests on Node's built-in runner (`node:test`): no test framework dependency |

Server-side configuration (environment variables, read only on the server, never `NEXT_PUBLIC_*`):

| Variable | Default | Purpose |
|---|---|---|
| `SC_BACKEND_URL` | `http://localhost:8080` | where the `/api/*` proxy sends requests |
| `SC_TENANT_BASE_DOMAINS` | `localhost` | hosts of the form `<slug>.<base>` name a tenant. A deploy sets its own (e.g. the staff and temple domains); the default knows no production domain (ADR 0030) |

There are no secrets in this app: it holds no keys, and the browser never holds a credential.

## How tenants and the API work locally

The tenant is the subdomain (ADR 0009). Staff use `<slug>.<staff domain>`; locally you use
`<slug>.localhost:3000`, because browsers resolve every `*.localhost` name to loopback.

The browser only ever talks to its own origin. `src/app/api/[...path]/route.ts` forwards
`/api/v1/*` to the backend:

- It derives the slug from the **Host the browser connected to** and sends it as `X-Tenant-Slug`,
  the header only the backend's `local` profile honours. A client-sent `X-Tenant-Slug` is always
  dropped, so a page on one tenant's host can't act on another tenant.
- It forwards an allowlist of headers each way (cookies, `X-XSRF-TOKEN`, content type; back:
  `Set-Cookie`, content type/disposition, caching). Only `/api/v1/...` paths, no traversal.
- Because the API is same-origin, the session cookie just works and there is no CORS at all.

In production the load balancer routes `/api/*` on the tenant host straight to the backend,
which reads the real Host, so this proxy never runs there.

Local caveat: everything reaching the backend through the proxy comes from `127.0.0.1`, so the
backend's per-IP login throttle (5 failures per 15 minutes) is shared by every local login. If
sign-in says "too many attempts", wait 15 minutes or restart the backend.

## Auth and session (ADR 0007, 0009)

- **Session:** the backend's `SC_SESSION` cookie is HttpOnly. This app never sees, stores or
  logs a credential: nothing goes in `localStorage` or `sessionStorage`.
- **CSRF:** `src/lib/api.ts` echoes the readable `XSRF-TOKEN` cookie in `X-XSRF-TOKEN` on every
  POST/PUT/PATCH/DELETE. With no cookie yet it fetches `GET /api/v1/csrf` first, and it refetches
  after login, because the token rotates. A 403 on a write is retried once with a fresh token.
- **401** anywhere: the session is gone, so the app goes to `/login?next=<current path>`. After
  login, `next` must be a same-origin relative path (`src/lib/returnTo.ts`), so `/login` can't be
  used as an open redirect.
- **Roles** (TRUST_ADMIN > LEADER > MEMBER): controls a role can't use are hidden, but the server
  decides. Every 403 is shown as "Your role doesn't allow this action".
- **Setup links** (`/setup#token=…`): the token is in the URL fragment, which browsers never send
  to a server. The page reads it once, removes it from the address bar (`history.replaceState`),
  keeps it only in memory and sends it only in the POST body. The admin sees a new link once,
  with a copy button. On `*.localhost` the panel also offers the local equivalent of the
  production link the backend issues.
- **Masked data:** MEMBERs receive masked devotee data from the server (`masked: true`), and the
  UI says "contact details hidden for your role". Masking is never done in the browser.

## Security headers

Set on every response: static headers in `next.config.ts`, and the CSP per request in
`src/proxy.ts`, because it carries a fresh nonce. Both come from `src/lib/securityHeaders.ts`.

**Content-Security-Policy (production):**

```
default-src 'self'; script-src 'self' 'nonce-<random>' 'strict-dynamic'; style-src 'self' 'nonce-<random>';
img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none';
form-action 'self'; frame-src 'none'; frame-ancestors 'none'; manifest-src 'self'; worker-src 'self'
```

- **Scripts:** a per-request nonce (18 random bytes) plus `'strict-dynamic'`. Next.js stamps the
  nonce on its own scripts, so no `'unsafe-inline'` or `'unsafe-eval'` is needed. Nonces require
  dynamic rendering, so every page renders per request (the root layout reads the Host anyway).
- **Fonts:** self-hosted by `next/font` at build time (`font-src 'self'`), so no Google request at
  runtime. `img-src data:` allows small inline images only (no remote images).
- **`frame-ancestors 'none'`** (plus legacy `X-Frame-Options: DENY`): the app can't be framed
  (clickjacking).

**Documented exceptions, development only (`next dev`, never in a production build):**

| Exception | Why |
|---|---|
| `script-src 'unsafe-eval'` | React uses `eval` in development to rebuild server error stacks in the browser |
| `style-src 'unsafe-inline'` (instead of the nonce) | the dev server injects CSS through `<style>` tags for hot reload |
| `connect-src ws:` | the hot-reload websocket |

`tests/errors.test.ts` asserts the production policy has a nonce and no `unsafe-*`. The
production build was run end to end in Chromium with a `securitypolicyviolation` listener: no
violations.

**Other headers:**

| Header | Value | Why |
|---|---|---|
| `Referrer-Policy` | `no-referrer` | nothing in our URLs ever leaks to another site (the setup page above all) |
| `X-Content-Type-Options` | `nosniff` | no MIME sniffing of API responses or assets |
| `Permissions-Policy` | camera, microphone, geolocation, payment, USB, … all `()`; `clipboard-write=(self)` | an admin app needs no device APIs; the clipboard is for the "copy setup link" button |
| `Cross-Origin-Opener-Policy` | `same-origin` | isolates our window from pages that open it |
| `Cross-Origin-Resource-Policy` | `same-origin` | other sites can't embed our responses |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains` | HTTPS only once served over TLS (browsers ignore it over plain http) |
| `Cache-Control` on `/api/*` | `no-store` | API responses carry personal data |
| `X-Powered-By` | removed | no framework fingerprint |

## Rendering untrusted data

All API data is untrusted: a devotee can be named `<script>…`. Everything is rendered as React
text, never as HTML. ESLint enforces it: `react/no-danger` (no `dangerouslySetInnerHTML`),
no `innerHTML`/`outerHTML`/`insertAdjacentHTML`, no `eval`/`new Function`, and `no-console`
(no PII or tokens in the browser console). Error messages shown to users come from a fixed map
in `src/lib/errors.ts`; unknown server text is never echoed.

Devotee search terms (which may be a phone number or email) are kept in memory, not in the page
URL, so they stay out of browser history and bookmarks.

## Dependencies

Pinned to exact versions (`.npmrc`: `save-exact`, `ignore-scripts`, `engine-strict`).

| Package | Why |
|---|---|
| `next`, `react`, `react-dom` | the framework |
| `typescript`, `@types/*` | strict type checking (TypeScript 6.0: `typescript-eslint` doesn't support 7 yet) |
| `tailwindcss`, `@tailwindcss/postcss` | styling from the design tokens (`src/app/globals.css`) |
| `eslint`, `eslint-config-next` | linting (ESLint 9: the React plugins don't support ESLint 10 yet) |

No UI kit, no test framework, no HTTP client: dialogs are the native `<dialog>` element, tests
use `node:test`, and requests use `fetch`.

## Layout

```
src/
  app/                  routes: login, setup, (app)/devotees, (app)/staff, api/[...path] (proxy)
  components/           UI primitives (ui.tsx), app shell pieces, forms
  lib/                  framework-free logic (API client, CSRF, errors, tenant, return-to, CSP); unit-tested
  proxy.ts              per-request CSP nonce
tests/                  node:test unit tests
```
