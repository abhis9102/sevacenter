/**
 * The staff app's navigation model (pure, unit-tested; no React here).
 *
 * Every module is one tab in the module bar and one tile in the switcher. Visibility follows the
 * same role + module rules as the API, so nobody sees a module they can't open. That is a UI hint
 * only: the server enforces access on every call.
 */

import type { Translations } from "./i18n";
import { hasRole, moduleAccess, type Me, type Role, type StaffModule } from "./types";

export type ModuleId =
  | "dashboard" | "devotees" | "donations" | "payments" | "pujas" | "events" | "volunteers" | "mandir" | "staff"
  | "audit";

/** Groups modules for the switcher's colour and badge. */
export type Category = "operations" | "people" | "giving" | "worship" | "mandir" | "admin";

export interface NavModule {
  id: ModuleId;
  href: string;
  label: keyof Translations["nav"];
  category: Category;
  min: Role;
  module?: StaffModule;
}

export const MODULES: NavModule[] = [
  { id: "dashboard", href: "/dashboard", label: "dashboard", category: "operations", min: "MEMBER" },
  { id: "devotees", href: "/devotees", label: "devotees", category: "people", min: "MEMBER", module: "DEVOTEES" },
  { id: "donations", href: "/donations", label: "donations", category: "giving", min: "LEADER", module: "DONATIONS" },
  { id: "payments", href: "/payments", label: "payments", category: "giving", min: "TRUST_ADMIN" },
  { id: "pujas", href: "/pujas", label: "pujas", category: "worship", min: "MEMBER", module: "PUJAS" },
  { id: "events", href: "/events", label: "events", category: "worship", min: "MEMBER", module: "EVENTS" },
  { id: "volunteers", href: "/volunteers", label: "volunteers", category: "people", min: "LEADER", module: "VOLUNTEERS" },
  { id: "mandir", href: "/temple", label: "temple", category: "mandir", min: "MEMBER", module: "TEMPLE" },
  { id: "staff", href: "/staff", label: "staff", category: "admin", min: "LEADER" },
  { id: "audit", href: "/audit", label: "audit", category: "admin", min: "TRUST_ADMIN" },
];

export function canOpen(me: Pick<Me, "role" | "moduleLimits">, m: NavModule): boolean {
  return hasRole(me.role, m.min) && (!m.module || moduleAccess(me.moduleLimits, m.module) !== "NONE");
}

/** The modules this user can open, in bar order. */
export function visibleModules(me: Pick<Me, "role" | "moduleLimits">): NavModule[] {
  return MODULES.filter((m) => canOpen(me, m));
}

/** True for the page itself and anything under it (/devotees/42), never for a prefix (/devoteesx). */
export function isActive(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

/** The module a path belongs to, or null (e.g. /profile belongs to none). */
export function moduleForPath(modules: NavModule[], pathname: string): NavModule | null {
  return modules.find((m) => isActive(pathname, m.href)) ?? null;
}
