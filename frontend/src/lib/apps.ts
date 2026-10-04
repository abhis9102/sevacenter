/**
 * The staff app's navigation model (pure, unit-tested; no React here).
 *
 * Pages are grouped into "apps". The top bar switches between apps; the second bar shows the
 * current app's pages. Visibility follows the same role + module rules as the API, so nobody sees
 * an app they can't open. That is a UI hint only: the server enforces access on every call.
 */

import type { Translations } from "./i18n";
import { hasRole, moduleAccess, type Me, type Role, type StaffModule } from "./types";

export type AppId = "home" | "people" | "giving" | "worship" | "site" | "admin";

export interface AppPage {
  href: string;
  label: keyof Translations["nav"];
  min: Role;
  module?: StaffModule;
}

export interface AppDef {
  id: AppId;
  pages: AppPage[];
}

export const APPS: AppDef[] = [
  { id: "home", pages: [{ href: "/dashboard", label: "dashboard", min: "MEMBER" }] },
  {
    id: "people",
    pages: [
      { href: "/devotees", label: "devotees", min: "MEMBER", module: "DEVOTEES" },
      { href: "/volunteers", label: "volunteers", min: "LEADER", module: "VOLUNTEERS" },
    ],
  },
  {
    id: "giving",
    pages: [
      { href: "/donations", label: "donations", min: "LEADER", module: "DONATIONS" },
      { href: "/payments", label: "payments", min: "TRUST_ADMIN" },
    ],
  },
  {
    id: "worship",
    pages: [
      { href: "/pujas", label: "pujas", min: "MEMBER", module: "PUJAS" },
      { href: "/events", label: "events", min: "MEMBER", module: "EVENTS" },
    ],
  },
  { id: "site", pages: [{ href: "/temple", label: "temple", min: "MEMBER", module: "TEMPLE" }] },
  {
    id: "admin",
    pages: [
      { href: "/staff", label: "staff", min: "LEADER" },
      { href: "/audit", label: "audit", min: "TRUST_ADMIN" },
    ],
  },
];

export function canOpen(me: Pick<Me, "role" | "moduleLimits">, page: AppPage): boolean {
  return hasRole(me.role, page.min) && (!page.module || moduleAccess(me.moduleLimits, page.module) !== "NONE");
}

/** Apps with at least one page this user can open, each with only those pages. */
export function visibleApps(me: Pick<Me, "role" | "moduleLimits">): AppDef[] {
  return APPS.map((app) => ({ ...app, pages: app.pages.filter((p) => canOpen(me, p)) })).filter(
    (app) => app.pages.length > 0,
  );
}

/** True for the page itself and anything under it (/devotees/42), never for a prefix (/devoteesx). */
export function isActive(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

/** The app a path belongs to, or null (e.g. /profile belongs to no app). */
export function appForPath(apps: AppDef[], pathname: string): AppDef | null {
  return apps.find((app) => app.pages.some((p) => isActive(pathname, p.href))) ?? null;
}
