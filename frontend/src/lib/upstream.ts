/**
 * Helpers for the same-origin `/api/*` proxy (src/app/api/[...path]/route.ts).
 *
 * The proxy forwards only an allowlist of headers in each direction. In particular the
 * client can't choose its tenant: any `X-Tenant-Slug` it sends is dropped and replaced by the
 * slug derived from the Host it actually connected to.
 */

const FORWARD_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "content-type",
  "cookie",
  "user-agent",
  "x-xsrf-token",
] as const;

const FORWARD_RESPONSE_HEADERS = [
  "cache-control",
  "content-disposition",
  "content-type",
  "retry-after",
  "expires",
  "pragma",
] as const;

export const TENANT_HEADER = "X-Tenant-Slug";

export function upstreamRequestHeaders(incoming: Headers, slug: string | null): Headers {
  const out = new Headers();
  for (const name of FORWARD_REQUEST_HEADERS) {
    const value = incoming.get(name);
    if (value !== null) {
      out.set(name, value);
    }
  }
  if (slug) {
    out.set(TENANT_HEADER, slug);
  }
  return out;
}

export function downstreamResponseHeaders(upstream: Headers): Headers {
  const out = new Headers();
  for (const name of FORWARD_RESPONSE_HEADERS) {
    const value = upstream.get(name);
    if (value !== null) {
      out.set(name, value);
    }
  }
  // Each Set-Cookie must stay a separate header (SC_SESSION, XSRF-TOKEN).
  for (const cookie of upstream.getSetCookie()) {
    out.append("set-cookie", cookie);
  }
  return out;
}

/**
 * The backend path for an incoming `/api/...` pathname, or null if it isn't one we forward.
 * Only the versioned API is reachable; no traversal, no encoded slashes.
 */
export function upstreamPath(pathname: string): string | null {
  if (!/^\/api\/v1(\/[A-Za-z0-9._~-]+)+$/.test(pathname)) {
    return null;
  }
  if (pathname.split("/").some((seg) => seg === "." || seg === "..")) {
    return null;
  }
  return pathname;
}

export function backendBaseUrl(raw: string | undefined): string {
  const value = raw && raw.trim() ? raw.trim() : "http://localhost:8080";
  const url = new URL(value);
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("SC_BACKEND_URL must be an http(s) URL");
  }
  return url.origin;
}
