import type { ImportRowError } from "./types";

/**
 * A failed API call. The server's error codes are machine-readable (`error`), with per-field
 * messages for validation (`fields`) and per-row errors for CSV import (`rows`).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly fields: Readonly<Record<string, string>>;
  readonly rows: readonly ImportRowError[];

  constructor(status: number, body: unknown) {
    const obj = isRecord(body) ? body : {};
    const code = typeof obj.error === "string" ? obj.error : null;
    super(code ?? `http_${status}`);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = toStringMap(obj.fields);
    this.rows = toRows(obj.rows);
  }
}

/** Thrown when the backend can't be reached at all (or answered with something unreadable). */
export class NetworkError extends Error {
  constructor() {
    super("network_error");
    this.name = "NetworkError";
  }
}

/** Plain-language messages for the codes the API returns. */
const MESSAGES: Record<string, string> = {
  invalid_credentials: "That email and password don't match an account here.",
  too_many_attempts: "Too many attempts. Wait a few minutes before trying again.",
  email_taken: "A staff member with this email already exists.",
  last_admin: "The trust must keep at least one active trust admin. Make someone else an admin first.",
  cannot_delete_self: "You can't delete your own account. Ask another trust admin.",
  use_change_password: "For your own account, use Profile → Change password.",
  not_active: "Only active staff can get a password reset link.",
  registration_closed: "Registration for this event is closed.",
  event_full: "There aren't enough places left for this event.",
  event_cancelled: "This event was cancelled.",
  pass_cancelled: "This pass was cancelled.",
  payments_not_configured: "This trust hasn't connected a payment account yet.",
  payment_not_verified: "The payment could not be verified, so no donation was recorded.",
  gateway_unavailable: "Razorpay didn't respond. Nothing was recorded; please try again.",
  not_pending: "This person has already set up their account, so they don't need a new setup link.",
  invalid_or_expired_link:
    "This setup link is invalid, expired or already used. Ask your trust admin for a new one.",
  not_found: "That record doesn't exist (or was removed).",
  validation_failed: "Some fields need attention.",
  concurrent_modification: "Someone else changed this record at the same time. Reload it and try again.",
  upload_too_large: "That file is too large. The limit is 2 MB.",
  malformed_upload: "The upload was not readable. Choose the file again and retry.",
  too_many_rows: "The file has too many rows for one import. Split it into smaller files.",
  malformed_csv: "That file isn't valid CSV. Save it as CSV (UTF-8) and try again.",
  empty: "The file has no devotee rows.",
  invalid_rows: "Some rows have errors, so nothing was imported. Fix the rows below and upload again.",
  bad_header: "The header row doesn't match the expected columns. Start from an exported file or the template.",
  backend_unavailable: "The SevaCenter service isn't reachable right now. Try again in a moment.",
};

const BY_STATUS: Record<number, string> = {
  401: "Your session has ended. Sign in again.",
  403: "Your role doesn't allow this action.",
  404: MESSAGES.not_found!,
  409: "That conflicts with the current state. Reload and try again.",
  413: MESSAGES.upload_too_large!,
  429: "Too many attempts. Wait a few minutes before trying again.",
};

export function describeError(err: unknown, dict?: Record<string, string>): string {
  const messages = dict ? { ...MESSAGES, ...dict } : MESSAGES;
  if (err instanceof NetworkError) {
    return dict?.network_error ?? "Can't reach SevaCenter. Check your connection and try again.";
  }
  if (err instanceof ApiError) {
    if (err.code && messages[err.code]) {
      return messages[err.code]!;
    }
    if (BY_STATUS[err.status]) {
      return BY_STATUS[err.status]!;
    }
    if (err.status >= 500) {
      return dict?.server_error ?? "Something went wrong on our side. Try again; if it keeps happening, contact support.";
    }
    return dict?.request_failed ?? "The request couldn't be completed.";
  }
  return "Something went wrong. Try again.";
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function toStringMap(v: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  if (isRecord(v)) {
    for (const [k, val] of Object.entries(v)) {
      if (typeof val === "string") {
        out[k] = val;
      }
    }
  }
  return out;
}

function toRows(v: unknown): ImportRowError[] {
  if (!Array.isArray(v)) {
    return [];
  }
  return v.filter(isRecord).map((r) => ({
    line: typeof r.line === "number" ? r.line : 0,
    field: typeof r.field === "string" ? r.field : "",
    message: typeof r.message === "string" ? r.message : "",
  }));
}
