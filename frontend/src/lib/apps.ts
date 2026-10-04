/**
 * The staff app's navigation model (pure, unit-tested; no React here).
 *
 * Modules are grouped into sections (People, Finance & 80G, Pujas & Utsavs, Administration...). The
 * switcher lists sections; a section's pages switch from the top of the page. Visibility follows
 * the same role + module rules as the API, so nobody sees a module they can't open. That is a UI
 * hint only: the server enforces access on every call.
 */

import type { Translations } from "./i18n";
import { hasRole, moduleAccess, type Me, type Role, type StaffModule } from "./types";

export type ModuleId =
  | "dashboard" | "devotees" | "donations" | "payments" | "pujas" | "events" | "volunteers" | "mandir" | "staff"
  | "audit";

/** Related modules live together: one switcher entry per section, in this order. */
export type Section = "dashboard" | "people" | "finance" | "worship" | "mandir" | "admin";
export const SECTIONS: Section[] = ["dashboard", "people", "finance", "worship", "mandir", "admin"];

export interface NavModule {
  id: ModuleId;
  href: string;
  label: keyof Translations["nav"];
  section: Section;
  min: Role;
  module?: StaffModule;
}

export const MODULES: NavModule[] = [
  { id: "dashboard", href: "/dashboard", label: "dashboard", section: "dashboard", min: "MEMBER" },
  { id: "devotees", href: "/devotees", label: "devotees", section: "people", min: "MEMBER", module: "DEVOTEES" },
  { id: "donations", href: "/donations", label: "donations", section: "finance", min: "LEADER", module: "DONATIONS" },
  { id: "payments", href: "/payments", label: "payments", section: "finance", min: "TRUST_ADMIN" },
  { id: "pujas", href: "/pujas", label: "pujas", section: "worship", min: "MEMBER", module: "PUJAS" },
  { id: "events", href: "/events", label: "events", section: "worship", min: "MEMBER", module: "EVENTS" },
  { id: "volunteers", href: "/volunteers", label: "volunteers", section: "people", min: "LEADER", module: "VOLUNTEERS" },
  { id: "mandir", href: "/temple", label: "temple", section: "mandir", min: "MEMBER", module: "TEMPLE" },
  { id: "staff", href: "/staff", label: "staff", section: "admin", min: "LEADER" },
  { id: "audit", href: "/audit", label: "audit", section: "admin", min: "TRUST_ADMIN" },
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

export interface SectionGroup {
  section: Section;
  modules: NavModule[];
}

/** The sections this user can open, each with only its visible modules, in switcher order. */
export function sectionsOf(modules: NavModule[]): SectionGroup[] {
  return SECTIONS.map((section) => ({ section, modules: modules.filter((m) => m.section === section) }))
    .filter((g) => g.modules.length > 0);
}
