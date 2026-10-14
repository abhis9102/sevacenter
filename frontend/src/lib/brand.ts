/**
 * Brand = which product a request's Host belongs to (ADR 0030, two-domain split):
 *   - SevaCenter (staff/admin)    -> SC_STAFF_BASE_DOMAINS   (e.g. stg-seva.example.com)
 *   - MandirCenter (public/devotee) -> SC_MANDIR_BASE_DOMAINS (e.g. stg-mandir.example.com)
 *
 * When the two base-domain sets are identical (local dev, or a single-domain deploy where both
 * brands share one host and are told apart by path), brand gating is OFF: `singleDomain` is true,
 * `brand` is null, and callers keep the original path-based behaviour. Server/edge only
 * (reads process.env); the client gets the resolved value via SiteProvider.
 */
import { parseBaseDomains, slugFromHost } from "./tenant";

export type Brand = "staff" | "mandir";

export interface Site {
  /** "staff" or "mandir" when the host is on exactly one brand; null when gating is off. */
  brand: Brand | null;
  /** The tenant slug from the Host, or null on a bare base domain / non-tenant host. */
  slug: string | null;
  /** First base domain of each brand, for building cross-brand links. */
  staffBase: string;
  mandirBase: string;
  /** true when staff and mandir base domains are the same set: no gating (dev/single-domain). */
  singleDomain: boolean;
  /**
   * A temple's own domain (ADR 0036): any real hostname that isn't ours. It serves only the public
   * MandirCenter pages; the backend decides which trust (if any) it belongs to. The edge only gets
   * a certificate for verified ones, so an unverified name never reaches us over HTTPS.
   */
  custom: boolean;
}

/** Never public or documentation-only; the same list the backend refuses (Hostnames.java). */
const RESERVED_TLDS = new Set(["localhost", "local", "internal", "test", "example", "invalid", "lan", "home", "arpa", "onion"]);

/** A plausible public hostname (not ours, not an IP, not a reserved name), lowercase, without port. */
export function customHostname(host: string | null | undefined): string | null {
  const h = hostnameOf(host ?? "");
  if (!h || h.length > 253 || !/^[a-z0-9.-]+$/.test(h) || !h.includes(".")) return null;
  if (/^[0-9.]+$/.test(h) || RESERVED_TLDS.has(h.slice(h.lastIndexOf(".") + 1))) return null;
  return h;
}

export function staffBaseDomains(): readonly string[] {
  return parseBaseDomains(process.env.SC_STAFF_BASE_DOMAINS ?? process.env.SC_TENANT_BASE_DOMAINS);
}
export function mandirBaseDomains(): readonly string[] {
  return parseBaseDomains(process.env.SC_MANDIR_BASE_DOMAINS ?? process.env.SC_TENANT_BASE_DOMAINS);
}

function hostnameOf(host: string): string {
  let h = (host || "").trim().toLowerCase();
  if (h.startsWith("[")) return h; // IPv6 literal
  const c = h.indexOf(":");
  if (c !== -1) h = h.slice(0, c);
  return h;
}
function onAny(host: string, bases: readonly string[]): boolean {
  const h = hostnameOf(host);
  return bases.some((b) => h === b || h.endsWith(`.${b}`));
}
function sameSet(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false;
  const bs = new Set(b);
  return a.every((x) => bs.has(x));
}

export function siteForHost(host: string | null | undefined): Site {
  const staff = staffBaseDomains();
  const mandir = mandirBaseDomains();
  const singleDomain = sameSet(staff, mandir);
  const h = host ?? "";
  const onStaff = onAny(h, staff);
  const onMandir = onAny(h, mandir);

  let brand: Brand | null = null;
  if (!singleDomain) {
    if (onStaff && !onMandir) brand = "staff";
    else if (onMandir && !onStaff) brand = "mandir";
  }
  const slug =
    (onStaff ? slugFromHost(h, staff) : null) ??
    (onMandir ? slugFromHost(h, mandir) : null) ??
    slugFromHost(h, staff) ??
    slugFromHost(h, mandir);

  const custom = !onStaff && !onMandir && customHostname(h) !== null;
  if (custom) {
    brand = "mandir";
  }

  return { brand, slug, staffBase: staff[0] ?? "", mandirBase: mandir[0] ?? "", singleDomain, custom };
}
