/** The signed-in devotee's account (GET /portal/me, ADR 0018, 0025). Pure; no React here. */

export type Channel = "EMAIL" | "SMS";

export interface DevoteeProfile {
  fullName: string | null;
  gotra: string | null;
  nakshatra: string | null;
  rashi: string | null;
  dateOfBirth: string | null;
  familyNames: string | null;
  addressLine: string | null;
  city: string | null;
  state: string | null;
  pincode: string | null;
}

export interface MySeva {
  channel: Channel;
  contact: string;
  contacts: { channel: Channel; contact: string }[];
  profile: DevoteeProfile;
  donations: {
    id: number; receivedOn: string; amount: string; mode: string; purpose: string | null; reversed: boolean;
    receiptNumber: string | null; receiptValid: boolean;
  }[];
  pujaBookings: { bookingCode: string; pujaName: string; pujaDate: string; amount: string; status: string }[];
  eventPasses: { passCode: string; eventTitle: string | null; startsAt: string | null; attendeeCount: number; status: string }[];
  sevakSignups: { sevaAreas: string; status: string; createdAt: string }[];
}

/** "+919822041187" -> "98220 41187"; anything else unchanged. */
export function displayPhone(e164: string): string {
  const m = /^\+91(\d{5})(\d{5})$/.exec(e164);
  return m ? `${m[1]} ${m[2]}` : e164;
}

/** What a signed-in devotee's forms start with: their first phone and email, and their name. */
export function prefill(seva: Pick<MySeva, "contacts" | "profile"> | null): { name: string; phone: string; email: string } {
  const phone = seva?.contacts.find((c) => c.channel === "SMS")?.contact;
  const email = seva?.contacts.find((c) => c.channel === "EMAIL")?.contact;
  return { name: seva?.profile.fullName ?? "", phone: phone ? displayPhone(phone) : "", email: email ?? "" };
}
