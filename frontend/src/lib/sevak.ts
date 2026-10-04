/** Sevak Hub (ADR 0015, 0028): shared types and pure helpers (unit-tested; no React here). */

export interface Signup {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
  sevaAreas: string;
  availability: string | null;
  notes: string | null;
  status: "NEW" | "APPROVED" | "DECLINED";
  createdAt: string;
  teamId: number | null;
  duty: string | null;
  registeredByStaff: boolean;
}

export interface Shift {
  name: string;
  startsAt: string;
  endsAt: string;
}

export interface Team {
  id: number;
  name: string;
  description: string | null;
  targetCount: number | null;
  icon: string | null;
  shifts: Shift[];
}

export interface HubStats {
  total: number;
  assigned: number;
  ready: number;
  pending: number;
  /** Assigned volunteers as a share of all teams' targets, 0–100; null without targets. */
  readiness: number | null;
}

export function hubStats(signups: Signup[], teams: Team[]): HubStats {
  const live = signups.filter((s) => s.status !== "DECLINED");
  const assigned = live.filter((s) => s.teamId !== null).length;
  const target = teams.reduce((n, t) => n + (t.targetCount ?? 0), 0);
  return {
    total: live.length,
    assigned,
    ready: live.filter((s) => s.status === "APPROVED" && s.teamId === null).length,
    pending: live.filter((s) => s.status === "NEW").length,
    readiness: target > 0 ? Math.min(100, Math.round((assigned / target) * 100)) : null,
  };
}

/** "06:00:00" -> "06:00". */
export const hhmm = (t: string) => t.slice(0, 5);

/** The icons a seva team can show: the server accepts exactly these (SevakService.ICONS). */
export const SEVA_ICON_KEYS = [
  "hands", "kitchen", "meal", "prasad", "cow", "queue", "crowd", "elder", "wheelchair", "child", "flower", "garland", "rangoli", "lamp", "bell", "flag", "temple", "music", "mic", "footwear", "broom", "water", "first-aid", "shield", "parking", "transport", "tent", "light", "tools", "camera", "book", "info", "phone", "clipboard", "rupee", "gift", "leaf", "star",
] as const;
export type SevaIconKey = (typeof SEVA_ICON_KEYS)[number];

export const SEVA_ICON_LABELS: Record<SevaIconKey, string> = {
  "hands": "Seva", "kitchen": "Kitchen", "meal": "Meals", "prasad": "Prasad", "cow": "Gau seva", "queue": "Darshan queue", "crowd": "Crowd", "elder": "Elders", "wheelchair": "Accessibility", "child": "Children", "flower": "Flowers", "garland": "Garlands", "rangoli": "Rangoli", "lamp": "Diya", "bell": "Bell", "flag": "Utsav", "temple": "Mandir", "music": "Bhajan", "mic": "Sound", "footwear": "Footwear", "broom": "Cleaning", "water": "Jal seva", "first-aid": "First aid", "shield": "Security", "parking": "Parking", "transport": "Transport", "tent": "Pandal", "light": "Lighting", "tools": "Maintenance", "camera": "Photography", "book": "Pathshala", "info": "Help desk", "phone": "Helpline", "clipboard": "Registration", "rupee": "Donation counter", "gift": "Distribution", "leaf": "Garden", "star": "Special duty",
};

/** A stored icon name, or the default for teams saved before icons existed. */
export function sevaIcon(icon: string | null | undefined): SevaIconKey {
  return (SEVA_ICON_KEYS as readonly string[]).includes(icon ?? "") ? (icon as SevaIconKey) : "hands";
}
