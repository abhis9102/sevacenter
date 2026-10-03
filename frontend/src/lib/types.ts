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
  /** Per-module limits a trust admin set (ADR 0021); missing module = full access for the role. */
  moduleLimits?: ModuleLimits;
}

export type StaffModule = "DEVOTEES" | "DONATIONS" | "EVENTS" | "PUJAS" | "VOLUNTEERS" | "TEMPLE";
export type ModuleAccess = "FULL" | "VIEW" | "NONE";
export type ModuleLimits = Partial<Record<StaffModule, ModuleAccess>>;

export const STAFF_MODULES: { id: StaffModule; label: string }[] = [
  { id: "DEVOTEES", label: "Devotees" },
  { id: "DONATIONS", label: "Donations & receipts" },
  { id: "EVENTS", label: "Events" },
  { id: "PUJAS", label: "Pujas" },
  { id: "VOLUNTEERS", label: "Volunteers" },
  { id: "TEMPLE", label: "Temple page" },
];

/** UI hint only: the server enforces limits on every call. */
export function moduleAccess(limits: ModuleLimits | undefined, module: StaffModule): ModuleAccess {
  return limits?.[module] ?? "FULL";
}

export type UserStatus = "PENDING" | "ACTIVE" | "DISABLED";

export interface StaffUser {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  status: UserStatus;
  createdAt: string;
  moduleLimits?: ModuleLimits;
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

export type DonationMode = "CASH" | "UPI" | "CHEQUE" | "BANK_TRANSFER" | "CARD" | "WALLET";

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
  /** STAFF = recorded by hand; ONLINE = verified Razorpay payment (paymentRef = Razorpay payment id). */
  channel: "STAFF" | "ONLINE";
  paymentRef: string | null;
  createdAt: string;
  fundId: number | null;
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
  byFund: FundSummary[];
}

/** Earmarked fund (ADR 0022); fundId null = the general fund. */
export interface FundSummary {
  fundId: number | null;
  fund: string;
  net: string;
  donations: number;
}

export interface DonationFund {
  id: number;
  name: string;
  active: boolean;
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
