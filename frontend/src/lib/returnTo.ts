/**
 * Where to go after login. Only a same-origin relative path is allowed, so a crafted
 * `/login?next=https://evil.example` (or `//evil.example`, `/\evil.example`, `javascript:`)
 * can never turn our login page into an open redirect.
 */
export const DEFAULT_AFTER_LOGIN = "/devotees";

const BASE = "http://sevacenter.invalid";

export function safeReturnTo(raw: string | null | undefined, fallback: string = DEFAULT_AFTER_LOGIN): string {
  if (!raw || raw.length > 512) {
    return fallback;
  }
  // A single leading slash only. "//host" and "/\host" are protocol-relative to browsers.
  if (!raw.startsWith("/") || raw.startsWith("//")) {
    return fallback;
  }
  // Backslashes (browsers read them as "/") and control characters (stripped by the URL
  // parser, so "/\t/evil.example" becomes "//evil.example") are never legitimate here.
  if (/[\\\u0000-\u001f\u007f]/.test(raw)) {
    return fallback;
  }
  let url: URL;
  try {
    url = new URL(raw, BASE);
  } catch {
    return fallback;
  }
  if (url.origin !== BASE) {
    return fallback;
  }
  // Never bounce back into the auth pages or to a raw API response.
  if (/^\/(login|setup|api)(\/|$)/.test(url.pathname)) {
    return fallback;
  }
  return url.pathname + url.search + url.hash;
}
