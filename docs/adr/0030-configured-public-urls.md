# ADR 0030: Public URLs come from configuration, per environment

- Status: Accepted (2026-10-05)
- Context: Setup links, password-reset links and the registration response were built with
  `https://<slug>.sevacenter.app` hardcoded, and tenant hosts were resolved against a hardcoded
  default list. A staging deploy on any other domain would hand out links to a domain it doesn't
  own, and whoever controls that domain would receive live setup and reset tokens. A local
  build and a production build would also differ in more than configuration.

## Decision

- **One URL template per brand:** `SEVACENTER_STAFF_URL` and `SEVACENTER_TEMPLE_URL`
  (`sevacenter.public.*`), e.g. `https://{slug}.sevacenter.app`. Every link the backend hands out
  is built from them, and the tenant resolution filter takes its base domains from the same
  values, so links and host resolution cannot disagree.
- **Never from the request.** Links are not built from `Host`, `X-Forwarded-Host` or `Forwarded`.
  Building them from the request would make reset-link poisoning possible. A test sends forged
  headers to prove this.
- **No default; validated at startup.** A missing value, plain http outside localhost, `{slug}`
  anywhere but the first label, or any path, query, fragment or userinfo stops the app from
  starting. The local profile supplies `http://{slug}.localhost:3000`.
- **Frontend:** `SC_TENANT_BASE_DOMAINS` defaults to `localhost` only, so a deploy that doesn't
  name its domains resolves no tenant (fail closed). Pages that show a trust's address read the
  domain from the page they're served on; the post-registration link comes from the backend.
- The same image therefore runs locally, on staging and in production; only the environment
  differs (build once, promote).

## Consequences

- Each environment must set these values (Terraform / task definition in M6). A forgotten value
  is a failed deploy, not a silent leak.
- Brand and marketing copy may still mention `sevacenter.app` as an example; it is product text,
  not configuration.
