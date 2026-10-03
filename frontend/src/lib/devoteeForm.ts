import type { ConsentSource, Devotee, DevoteeInput } from "./types";

/** Form state: every field a string, as the inputs hold it. */
export interface DevoteeFormValues {
  fullName: string;
  phone: string;
  email: string;
  addressLine: string;
  city: string;
  state: string;
  pincode: string;
  dateOfBirth: string;
  consentSource: string;
}

export const EMPTY_DEVOTEE: DevoteeFormValues = {
  fullName: "",
  phone: "",
  email: "",
  addressLine: "",
  city: "",
  state: "",
  pincode: "",
  dateOfBirth: "",
  consentSource: "",
};

export function valuesFromDevotee(d: Devotee): DevoteeFormValues {
  return {
    fullName: d.fullName,
    phone: d.phone ?? "",
    email: d.email ?? "",
    addressLine: d.addressLine ?? "",
    city: d.city ?? "",
    state: d.state ?? "",
    pincode: d.pincode ?? "",
    dateOfBirth: d.dateOfBirth ?? "",
    consentSource: d.consentSource ?? "",
  };
}

function opt(v: string): string | null {
  const t = v.trim();
  return t === "" ? null : t;
}

/**
 * Request body for create (with consent) or update (without: consent is recorded once and
 * the server ignores it on edits). Blank optional fields are sent as null, never "".
 */
export function toDevoteeInput(v: DevoteeFormValues, mode: "create" | "edit"): DevoteeInput {
  const body: DevoteeInput = {
    fullName: v.fullName.trim(),
    phone: opt(v.phone),
    email: opt(v.email),
    addressLine: opt(v.addressLine),
    city: opt(v.city),
    state: opt(v.state),
    pincode: opt(v.pincode),
    dateOfBirth: opt(v.dateOfBirth),
  };
  if (mode === "create") {
    body.consentSource = (opt(v.consentSource) as ConsentSource | null) ?? null;
  }
  return body;
}

/** Checks done before sending (the server re-checks everything). Keyed like the API's `fields`. */
export function clientFieldErrors(v: DevoteeFormValues, mode: "create" | "edit"): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!v.fullName.trim()) {
    errors.fullName = "Enter the devotee's name.";
  }
  if (mode === "create" && !v.consentSource) {
    errors.consentSource = "Record how the devotee gave consent.";
  }
  if (v.pincode.trim() && !/^[1-9][0-9]{5}$/.test(v.pincode.trim())) {
    errors.pincode = "Pincode must be 6 digits.";
  }
  return errors;
}
