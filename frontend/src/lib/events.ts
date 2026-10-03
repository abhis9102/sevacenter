/** Events (ADR 0014): shared types and pure helpers (unit-tested; no React here). */

export type EventStatus = "DRAFT" | "PUBLISHED" | "CANCELLED";

export interface StaffEvent {
  id: number;
  title: string;
  description: string | null;
  startsAt: string;
  endsAt: string;
  capacity: number | null;
  seatsTaken: number;
  status: EventStatus;
  registrationOpen: boolean;
}

export interface EventPass {
  id: number;
  passCode: string;
  attendeeName: string;
  attendeeCount: number;
  phone: string | null;
  email: string | null;
  status: "ACTIVE" | "CANCELLED";
  checkedInAt: string | null;
  createdAt: string;
}

export interface PublicEvent {
  id: number;
  title: string;
  description: string | null;
  startsAt: string;
  endsAt: string;
  placesLeft: number | null;
  registrationOpen: boolean;
}

export interface PassIssued {
  passCode: string;
  eventTitle: string;
  startsAt: string;
  name: string;
  count: number;
}

/** A <input type="datetime-local"> value ("2030-08-15T22:00") as an IST instant for the API. */
export function istFromLocalInput(local: string): string | null {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(local) ? `${local}:00+05:30` : null;
}

/** "Sat, 15 Aug 2030, 10:00 pm" in IST, whatever the viewer's own time zone. */
export function formatIst(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    timeZone: "Asia/Kolkata",
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(iso));
}

/** Pass codes are read aloud: show them as "ABCDE-23456". */
export function formatPassCode(code: string): string {
  return code.length === 10 ? `${code.slice(0, 5)}-${code.slice(5)}` : code;
}
