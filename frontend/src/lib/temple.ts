/** The public temple page payload (GET /public/temple, ADR 0017 + 0024). */

import type { Aarti, DarshanHours } from "./darshan";
import type { LunarCalendar } from "./panchang";

export interface PublicTemple {
  trustName: string | null;
  deity: string | null;
  address: string | null;
  helpline: string | null;
  timings: string | null;
  announcement: string | null;
  hours: DarshanHours | null;
  aartis: Aarti[];
  status: "OPEN" | "CLOSED" | null;
  statusNote: string | null;
  calendar: LunarCalendar;
  onlineDonations: boolean;
  upcomingEvents: boolean;
  pujaBooking: boolean;
}

/** Fired by the sign-in page so the header can show the devotee straight away. */
export const DEVOTEE_CHANGED = "devotee-changed";
