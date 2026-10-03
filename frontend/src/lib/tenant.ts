/**
 * Tenant = the subdomain (ADR 0009): staff use `<slug>.sevacenter.app`; locally
 * `<slug>.localhost:3000` (browsers resolve *.localhost to loopback).
 *
 * Mirrors the backend's rule: only a host of exactly `<slug>.<base-domain>` with a known base
 * domain names a tenant. Anything else (extra labels, a bare base domain, another domain)
 * resolves no tenant.
 */

/** Same shape the backend accepts for a slug: DNS label, lowercase. */
const SLUG_RE = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;

export const DEFAULT_BASE_DOMAINS: readonly string[] = ["localhost", "sevacenter.app", "mandircenter.app"];

export function isMandirCenter(host: string | null | undefined): boolean {
  if (!host) return false;
  const h = host.toLowerCase();
  return h.includes("mandircenter");
}

export function parseBaseDomains(raw: string | undefined): readonly string[] {
  if (!raw || !raw.trim()) {
    return DEFAULT_BASE_DOMAINS;
  }
  return raw
    .split(",")
    .map((d) => d.trim().toLowerCase())
    .filter((d) => d.length > 0);
}

export function slugFromHost(
  host: string | null | undefined,
  baseDomains: readonly string[] = DEFAULT_BASE_DOMAINS,
): string | null {
  if (!host) {
    return null;
  }
  let h = host.trim().toLowerCase();
  if (h.startsWith("[")) {
    return null; // IPv6 literal: never a tenant
  }
  const colon = h.indexOf(":");
  if (colon !== -1) {
    const port = h.slice(colon + 1);
    if (!/^\d{1,5}$/.test(port)) {
      return null;
    }
    h = h.slice(0, colon);
  }
  for (const base of baseDomains) {
    const suffix = `.${base}`;
    if (h.endsWith(suffix)) {
      const label = h.slice(0, h.length - suffix.length);
      return SLUG_RE.test(label) ? label : null;
    }
  }
  return null;
}

/** A tenant slug as the backend accepts it (V2 check): 3-40 lowercase letters, digits, hyphens. */
export const REGISTERED_SLUG_RE = /^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$/;

/**
 * The sign-in URL on a trust's own host, built from the slug a visitor typed. Validated first: the
 * slug becomes part of a hostname, so "evil.example/x" must never turn into a link to evil.example.
 */
export function portalLoginUrl(
  rawSlug: string,
  location: { protocol: string; hostname: string; port: string },
): string | null {
  const slug = rawSlug.trim().toLowerCase();
  if (!REGISTERED_SLUG_RE.test(slug)) {
    return null;
  }
  const port = location.port ? `:${location.port}` : "";
  const local = location.hostname === "localhost" || location.hostname === "127.0.0.1";
  const base = local ? "localhost" : location.hostname.replace(/^www\./, "");
  return `${location.protocol}//${slug}.${base}${port}/login`;
}
