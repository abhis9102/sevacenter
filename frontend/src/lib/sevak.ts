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
