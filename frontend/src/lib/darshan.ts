/** Live darshan status and the next aarti, in temple (IST) time. Pure, unit-tested; no React here. */

export interface DarshanHours {
  morningOpen: string | null;
  morningClose: string | null;
  eveningOpen: string | null;
  eveningClose: string | null;
}

export interface Aarti {
  name: string;
  at: string;
  description: string | null;
}

export type DarshanState =
  | { open: true; until: string; overridden: false }
  | { open: false; opensAt: string | null; overridden: false }
  | { open: boolean; overridden: true; note: string | null };

/** Minutes since midnight in IST, whatever the browser's timezone. */
export function istMinutes(at: Date): number {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(at);
  const n = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  return n("hour") * 60 + n("minute");
}

/** "05:30" or "05:30:00" -> 330. */
export function minutes(time: string): number {
  const [h, m] = time.split(":");
  return Number(h) * 60 + Number(m);
}

/** "05:30" -> "5:30 am" (Latin digits in both languages, as on Indian temple boards). */
export function clock(time: string): string {
  const total = minutes(time);
  const h = Math.floor(total / 60), m = total % 60;
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, "0")} ${h < 12 ? "am" : "pm"}`;
}

function sessions(h: DarshanHours | null): Array<[string, string]> {
  if (!h) return [];
  const out: Array<[string, string]> = [];
  if (h.morningOpen && h.morningClose) out.push([h.morningOpen, h.morningClose]);
  if (h.eveningOpen && h.eveningClose) out.push([h.eveningOpen, h.eveningClose]);
  return out;
}

/** Null when there's nothing to say (no hours and no override): the page shows no status at all. */
export function darshanState(hours: DarshanHours | null, override: "OPEN" | "CLOSED" | null, note: string | null,
                             at: Date): DarshanState | null {
  if (override) {
    return { open: override === "OPEN", overridden: true, note };
  }
  const list = sessions(hours);
  if (list.length === 0) return null;
  const now = istMinutes(at);
  for (const [open, close] of list) {
    if (now >= minutes(open) && now < minutes(close)) return { open: true, until: close, overridden: false };
  }
  const next = list.find(([open]) => minutes(open) > now);
  return { open: false, opensAt: next ? next[0] : list[0]![0], overridden: false };
}

/** The next aarti today, or null once the last one has started. */
export function nextAarti(aartis: Aarti[], at: Date): Aarti | null {
  const now = istMinutes(at);
  return aartis.find((a) => minutes(a.at) >= now) ?? null;
}
