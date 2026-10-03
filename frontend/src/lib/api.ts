import { ApiError, NetworkError } from "./errors";

/**
 * Browser API client (ADR 0007 / 0009).
 *
 * - Same-origin only: `/api/*` on the tenant's own host, so the HttpOnly `SC_SESSION` cookie
 *   rides along on its own. This code never sees or stores a credential.
 * - CSRF double-submit: every state-changing request echoes the readable `XSRF-TOKEN` cookie
 *   in `X-XSRF-TOKEN`. If there's no cookie yet we fetch one from `GET /api/v1/csrf`. The
 *   token rotates on login, so callers refresh it after signing in, and a 403 on a write is
 *   retried once with a fresh token (a stale token is the common cause; a real authorization
 *   403 simply fails again, and a rejected request changed nothing).
 * - 401 anywhere means the session is gone: `onUnauthorized` sends the user to login.
 */

export const CSRF_COOKIE = "XSRF-TOKEN";
export const CSRF_HEADER = "X-XSRF-TOKEN";
const API_PREFIX = "/api/v1";

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface RequestOptions {
  method?: Method;
  /** JSON body. */
  json?: unknown;
  /** application/x-www-form-urlencoded body (login). */
  form?: Record<string, string>;
  /** Multipart body (CSV import). */
  multipart?: FormData;
  /** Don't redirect to login on 401: the caller handles it (login page, session probe). */
  allowUnauthorized?: boolean;
  /** Return the raw Response (file download) instead of parsed JSON. */
  raw?: boolean;
  /** Custom Accept header (defaults to application/json, or text/csv when raw is true). */
  accept?: string;
  signal?: AbortSignal;
}

export interface ApiDeps {
  fetch: (input: string, init: RequestInit) => Promise<Response>;
  /** `document.cookie`. */
  getCookies: () => string;
  onUnauthorized: () => void;
}

export function readCookie(cookies: string, name: string): string | null {
  for (const part of cookies.split(";")) {
    const eq = part.indexOf("=");
    if (eq === -1) {
      continue;
    }
    if (part.slice(0, eq).trim() === name) {
      const value = part.slice(eq + 1).trim();
      try {
        return value ? decodeURIComponent(value) : null;
      } catch {
        return null;
      }
    }
  }
  return null;
}

function isUnsafe(method: Method): boolean {
  return method !== "GET";
}

export interface Api {
  request<T>(path: string, options?: RequestOptions): Promise<T>;
  get<T>(path: string, options?: Omit<RequestOptions, "method">): Promise<T>;
  /** Fetches a fresh CSRF token (call after login: the token rotates). */
  refreshCsrf(): Promise<string>;
}

export function createApi(deps: ApiDeps): Api {
  async function send(url: string, init: RequestInit): Promise<Response> {
    try {
      return await deps.fetch(url, init);
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        throw e;
      }
      throw new NetworkError();
    }
  }

  async function refreshCsrf(): Promise<string> {
    const res = await send(`${API_PREFIX}/csrf`, {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      throw new ApiError(res.status, await safeJson(res));
    }
    const body = await safeJson(res);
    const fromBody =
      typeof body === "object" && body !== null && typeof (body as { token?: unknown }).token === "string"
        ? (body as { token: string }).token
        : null;
    const token = fromBody ?? readCookie(deps.getCookies(), CSRF_COOKIE);
    if (!token) {
      throw new ApiError(500, { error: "csrf_unavailable" });
    }
    return token;
  }

  async function csrfToken(): Promise<string> {
    return readCookie(deps.getCookies(), CSRF_COOKIE) ?? (await refreshCsrf());
  }

  function buildInit(method: Method, options: RequestOptions, token: string | null): RequestInit {
    const accept = options.accept ?? (options.raw ? "text/csv, */*" : "application/json");
    const headers: Record<string, string> = { Accept: accept };
    let body: BodyInit | undefined;
    if (options.json !== undefined) {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify(options.json);
    } else if (options.form) {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      body = new URLSearchParams(options.form).toString();
    } else if (options.multipart) {
      body = options.multipart; // the browser sets the multipart boundary itself
    }
    if (token) {
      headers[CSRF_HEADER] = token;
    }
    return {
      method,
      headers,
      body,
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
      signal: options.signal,
    };
  }

  async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    if (!path.startsWith("/") || path.startsWith("//")) {
      throw new Error("API paths are relative to /api/v1");
    }
    const method = options.method ?? "GET";
    const url = API_PREFIX + path;

    let token = isUnsafe(method) ? await csrfToken() : null;
    let res = await send(url, buildInit(method, options, token));

    if (res.status === 403 && isUnsafe(method)) {
      token = await refreshCsrf();
      res = await send(url, buildInit(method, options, token));
    }

    if (res.status === 401 && !options.allowUnauthorized) {
      deps.onUnauthorized();
    }
    if (!res.ok) {
      throw new ApiError(res.status, await safeJson(res));
    }
    if (options.raw) {
      return res as unknown as T;
    }
    if (res.status === 204) {
      return undefined as T;
    }
    const type = res.headers.get("content-type") ?? "";
    if (!type.includes("json")) {
      return undefined as T;
    }
    return (await res.json()) as T;
  }

  return {
    request,
    get: (path, options) => request(path, { ...options, method: "GET" }),
    refreshCsrf,
  };
}

async function safeJson(res: Response): Promise<unknown> {
  try {
    const type = res.headers.get("content-type") ?? "";
    return type.includes("json") ? await res.json() : null;
  } catch {
    return null;
  }
}
