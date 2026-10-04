/** Pujas (ADR 0016): shared types (no React here). */

export interface Puja {
  id: number;
  name: string;
  deity: string | null;
  description: string | null;
  dakshina: string;
  active: boolean;
  displayOrder: number;
}

export interface PujaBooking {
  id: number;
  bookingCode: string;
  pujaName: string;
  pujaDate: string;
  devoteeName: string;
  gotra: string | null;
  nakshatra: string | null;
  rashi: string | null;
  familyNames: string | null;
  phone: string | null;
  email: string | null;
  amount: string;
  status: "AWAITING_PAYMENT" | "CONFIRMED" | "PERFORMED" | "CANCELLED";
  /** Booked by staff at the counter (ADR 0026), and how a paid one was paid. */
  counter: boolean;
  counterMode: string | null;
}

export interface Booked {
  bookingCode: string;
  status: PujaBooking["status"];
  pujaName: string;
  pujaDate: string;
  amount: string;
  orderId: string | null;
  keyId: string | null;
  amountPaise: number;
}

/** Today in IST as YYYY-MM-DD (the temple's calendar, whatever the viewer's zone). */
export function todayIst(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
}

export function isFree(dakshina: string): boolean {
  return Number(dakshina) === 0;
}
