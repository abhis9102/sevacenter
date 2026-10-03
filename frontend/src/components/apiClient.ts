"use client";

import { createApi } from "@/lib/api";
import { safeReturnTo } from "@/lib/returnTo";

/** The browser's API client: same-origin fetch, CSRF from document.cookie, 401 -> login. */
export const api = createApi({
  fetch: (input, init) => window.fetch(input, init),
  getCookies: () => document.cookie,
  onUnauthorized: () => {
    const here = window.location.pathname + window.location.search;
    if (window.location.pathname === "/login") {
      return;
    }
    const next = safeReturnTo(here, "");
    window.location.assign(next ? `/login?next=${encodeURIComponent(next)}` : "/login");
  },
});
