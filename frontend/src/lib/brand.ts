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

  return { brand, slug, staffBase: staff[0] ?? "", mandirBase: mandir[0] ?? "", singleDomain };
}
