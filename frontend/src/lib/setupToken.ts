/**
 * One-time setup links look like `https://<slug>.sevacenter.app/setup#token=...`.
 *
 * The token lives in the URL fragment, which browsers never send to any server (not in the
 * request line, not in Referer). We read it once, keep it only in memory and send it only in
 * the POST body of /api/v1/auth/setup. The fragment is then wiped from the address bar, so it
 * doesn't stay in history or get copied along with the URL.
 */

/** The backend issues 32 random bytes as unpadded base64url (43 chars); allow some slack. */
const TOKEN_RE = /^[A-Za-z0-9_-]{20,100}$/;

export function readSetupToken(hash: string | null | undefined): string | null {
  if (!hash) {
    return null;
  }
  const raw = hash.startsWith("#") ? hash.slice(1) : hash;
  const token = new URLSearchParams(raw).get("token");
  return token !== null && TOKEN_RE.test(token) ? token : null;
}

/** Minimal surface of `window.history` / `window.location` so this is testable without a DOM. */
export interface UrlBar {
  location: { pathname: string; search: string; hash: string };
  history: { replaceState(data: unknown, unused: string, url?: string | URL | null): void };
}

/** Reads the token and removes the fragment from the address bar (no navigation, no request). */
export function takeSetupTokenFromUrl(bar: UrlBar): string | null {
  const token = readSetupToken(bar.location.hash);
  if (bar.location.hash) {
    bar.history.replaceState(null, "", bar.location.pathname + bar.location.search);
  }
  return token;
}

export const MIN_PASSWORD_LENGTH = 12;
export const MAX_PASSWORD_LENGTH = 200;

export function passwordProblem(password: string, confirm: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  }
  if (password.length > MAX_PASSWORD_LENGTH) {
    return `Use at most ${MAX_PASSWORD_LENGTH} characters.`;
  }
  if (password !== confirm) {
    return "The two passwords don't match.";
  }
  return null;
}

/**
 * The backend builds setup links for the production host. On a local `*.localhost` host the
 * same path + fragment on the current origin is the working equivalent.
 */
export function localEquivalentSetupUrl(setupUrl: string, currentOrigin: string): string | null {
  let current: URL;
  let link: URL;
  try {
    current = new URL(currentOrigin);
    link = new URL(setupUrl);
  } catch {
    return null;
  }
  if (!current.hostname.endsWith(".localhost") || link.pathname !== "/setup") {
    return null;
  }
  return current.origin + link.pathname + link.hash;
}
