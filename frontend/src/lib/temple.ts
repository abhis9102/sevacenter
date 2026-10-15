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
  /** At least one group is listed for devotees to join (ADR 0037). */
  groups: boolean;
  /** The About page has photos. */
  gallery: boolean;
  /** Plain text: render as text (whitespace-pre-line), never as HTML. */
  about: string | null;
  history: string | null;
  /** Photo at /api/v1/public/temple/image?v={imageVersion}; null when the temple has none. */
  imageVersion: string | null;
}

export interface GalleryPhoto {
  id: number;
  section: "ABOUT" | "HISTORY";
  caption: string | null;
  width: number;
  height: number;
  version: string;
}

export const galleryImageUrl = (p: Pick<GalleryPhoto, "id" | "version">) =>
  `/api/v1/public/temple/gallery/${p.id}?v=${encodeURIComponent(p.version)}`;

/** The About page exists when there's anything to show on it. */
export const hasAboutPage = (t: Pick<PublicTemple, "about" | "history" | "gallery">) => !!(t.about || t.history || t.gallery);

export const templeImageUrl = (version: string) =>
  `/api/v1/public/temple/image?v=${encodeURIComponent(version)}`;

/** Fired by the sign-in page so the header can show the devotee straight away. */
export const DEVOTEE_CHANGED = "devotee-changed";
