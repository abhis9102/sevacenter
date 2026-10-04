/** Staff dashboard helpers (pure, unit-tested; no React here). */

import type { StaffEvent } from "./events";

/** The temple's date (IST) as YYYY-MM-DD, whatever the browser's timezone. */
export function istDate(at: Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(at);
}

/** Published events that haven't ended yet, soonest first. */
export function upcomingEvents(events: readonly StaffEvent[], nowMs: number, limit = 5): StaffEvent[] {
  return events
    .filter((e) => e.status === "PUBLISHED" && new Date(e.endsAt).getTime() >= nowMs)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
    .slice(0, limit);
}
