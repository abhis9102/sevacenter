/** Shapes returned by the SevaCenter API (backend/src/main/java/app/sevacenter). */

export const ROLES = ["TRUST_ADMIN", "LEADER", "MEMBER"] as const;
export type Role = (typeof ROLES)[number];

export const ROLE_LABELS: Record<Role, string> = {
  TRUST_ADMIN: "Trust admin",
  LEADER: "Leader",
  MEMBER: "Member",
};

const RANK: Record<Role, number> = { MEMBER: 1, LEADER: 2, TRUST_ADMIN: 3 };

/** TRUST_ADMIN > LEADER > MEMBER. For hiding controls only: the server decides. */
export function hasRole(actual: string | undefined, required: Role): boolean {
  if (!actual || !(actual in RANK)) {
    return false;
  }
  return RANK[actual as Role] >= RANK[required];
}

export interface Me {
  userId: number;
  email: string;
  displayName: string;
  role: string;
  tenant: string | null;
  hasAvatar: boolean;
}

export type UserStatus = "PENDING" | "ACTIVE" | "DISABLED";

export interface StaffUser {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  status: UserStatus;
  createdAt: string;
}

export interface CreatedStaffUser {
  user: StaffUser;
  setupUrl: string;
}

export const CONSENT_SOURCES = ["IN_PERSON", "PHONE", "ONLINE_FORM", "WRITTEN"] as const;
export type ConsentSource = (typeof CONSENT_SOURCES)[number];

export const CONSENT_LABELS: Record<ConsentSource, string> = {
  IN_PERSON: "In person",
  PHONE: "By phone",
  ONLINE_FORM: "Online form",
  WRITTEN: "In writing",
};

export interface Devotee {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
  addressLine: string | null;
  city: string | null;
  state: string | null;
  pincode: string | null;
  dateOfBirth: string | null;
  consentSource: ConsentSource | null;
  consentGivenAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** True when the server hid contact details for the caller's role (ADR 0010). */
  masked: boolean;
}

export interface DevoteePage {
  items: Devotee[];
  page: number;
  size: number;
  total: number;
}

/** Editable fields; empty optional values are sent as null. */
export interface DevoteeInput {
  fullName: string;
  phone: string | null;
  email: string | null;
  addressLine: string | null;
  city: string | null;
  state: string | null;
  pincode: string | null;
  dateOfBirth: string | null;
  consentSource?: ConsentSource | null;
}

export interface ImportResult {
  imported: number;
}

export interface ImportRowError {
  line: number;
  field: string;
  message: string;
}

export interface UserProfile {
  userId: number;
  email: string;
  displayName: string;
  role: Role;
  tenant: string | null;
  status: UserStatus;
  createdAt: string;
  notifyDevotees: boolean;
  notifyDonations: boolean;
  notifySecurity: boolean;
  hasAvatar: boolean;
}

export interface UpdateProfileInput {
  displayName?: string;
  notifyDevotees?: boolean;
  notifyDonations?: boolean;
  notifySecurity?: boolean;
}

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

export type DonationMode = "CASH" | "UPI" | "CHEQUE" | "BANK_TRANSFER" | "CARD";

export const DONATION_MODES: DonationMode[] = ["UPI", "CASH", "CARD", "BANK_TRANSFER", "CHEQUE"];

export interface DonationItem {
  id: number;
  devoteeId: number | null;
  donorName: string;
  amount: string;
  mode: DonationMode;
  reference: string | null;
  purpose: string | null;
  receivedOn: string;
  reversesId: number | null;
  reversalReason: string | null;
  recordedBy: number | null;
  createdAt: string;
}

export interface DonationPage {
  items: DonationItem[];
  page: number;
  size: number;
  total: number;
}

export interface ModeSummary {
  mode: DonationMode;
  net: string;
  donations: number;
  reversals: number;
}

export interface DonationSummary {
  financialYear: string;
  from: string;
  to: string;
  net: string;
  byMode: ModeSummary[];
}

export interface ReceiptDetail {
  id: number;
  number: string;
  donationId: number;
  issuedOn: string;
  donorName: string;
  donorAddress: string;
  donorPan: string;
  amount: string;
  mode: DonationMode;
  receivedOn: string;
  trustLegalName: string;
  trustAddress: string;
  trustPan: string;
  trustRegistration80g: string;
  cancelled: boolean;
  cancellationReason: string | null;
}

export interface RecordDonationInput {
  donorName?: string;
  devoteeId?: number | null;
  amount: string;
  mode: DonationMode;
  reference?: string | null;
  purpose?: string | null;
  receivedOn: string;
}

export interface AartiTiming {
  name: string;
  time: string;
  description: string;
}

export interface MandirSchedule {
  mandirName: string;
  deity: string;
  address: string;
  helpline: string;
  morningHours: string;
  eveningHours: string;
  isOpenNow: boolean;
  panchangTithi: string;
  nakshatra: string;
  specialAnnouncement: string;
  aartis: AartiTiming[];
}

export interface PujaItem {
  code: string;
  name: string;
  deity: string;
  duration: string;
  dakshinaRupees: number;
  description: string;
  prasadIncluded: boolean;
}

export interface PujaBookingResponse {
  id: number;
  bookingNumber: string;
  pujaCode: string;
  pujaName: string;
  pujaDate: string;
  timeSlot: string;
  devoteeName: string;
  gotra: string | null;
  nakshatra: string | null;
  rashi: string | null;
  familyMembers: string | null;
  amountRupees: number;
  status: string;
  mandirName: string;
}

export interface MandirEvent {
  code: string;
  name: string;
  date: string;
  timeRange: string;
  description: string;
  highlights: string;
  registrationOpen: boolean;
}

export interface DarshanPassResponse {
  id: number;
  passNumber: string;
  eventCode: string;
  eventName: string;
  visitDate: string;
  timeSlot: string;
  primaryDevoteeName: string;
  attendeeCount: number;
  contact: string;
  status: string;
  qrString: string;
  mandirName: string;
}

export interface SevakResponse {
  id: number;
  fullName: string;
  sevaArea: string;
  availableDays: string;
  shiftPreference: string;
  message: string;
}
