import { NextResponse, type NextRequest } from "next/server";

import { siteForHost } from "@/lib/brand";
import { buildCsp } from "@/lib/securityHeaders";

/**
 * Two-domain brand gate (ADR 0030): a SevaCenter (staff) host never serves the public MandirCenter
 * pages and vice-versa; a wrong-brand route is redirected to the SAME trust on the correct domain.
 * `/` is brand-dependent and handled by app/page.tsx, so it is not listed here. No-op when brand
 * gating is off (single-domain / dev).
 */
const STAFF_PREFIXES = [
  "/dashboard", "/devotees", "/donations", "/events", "/payments", "/profile",
  "/pujas", "/staff", "/volunteers", "/temple", "/audit",
  "/login", "/register", "/setup", "/reset-password", "/forgot-password",
];
const MANDIR_PREFIXES = ["/donate", "/book-puja", "/sevak", "/my-seva", "/upcoming"];

function hasPrefix(path: string, prefixes: readonly string[]): boolean {
  return prefixes.some((p) => path === p || path.startsWith(`${p}/`));
}

/**
 * Per-request CSP nonce for every page (Next.js reads the nonce from the request's CSP header
 * and stamps it on its own scripts). Static security headers live in next.config.ts.
 */
export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;

  // Brand gate first: a cross-brand redirect needs no CSP of its own.
  const site = siteForHost(request.headers.get("host"));
  // A temple's own domain serves the public pages only: never staff sign-in or the staff app, so
  // no staff session ever lives on a domain we don't control (ADR 0036). 404, not a redirect: the
  // trust's staff address isn't advertised on its public domain.
  if (site.custom && hasPrefix(path, STAFF_PREFIXES)) {
    return new NextResponse("Not found", { status: 404, headers: { "content-type": "text/plain" } });
  }
  // A trust's staff host has no home page: straight to sign-in. Done here, not in the page, so the
  // 307 carries no body (a page-level redirect streamed the app shell with it: DAST "big redirect").
  if (!site.singleDomain && site.brand === "staff" && site.slug && path === "/") {
    return NextResponse.redirect(new URL("/login", request.nextUrl), 307);
  }
  if (!site.singleDomain && site.brand && !site.custom) {
    const crossTo = (base: string) => {
      const url = request.nextUrl.clone();
      url.host = site.slug ? `${site.slug}.${base}` : base;
      url.port = "";
      return url;
    };
    if (site.brand === "staff" && hasPrefix(path, MANDIR_PREFIXES)) {
      return NextResponse.redirect(crossTo(site.mandirBase), 307);
    }
    if (site.brand === "mandir" && hasPrefix(path, STAFF_PREFIXES)) {
      return NextResponse.redirect(crossTo(site.staffBase), 307);
    }
  }

  const nonce = Buffer.from(crypto.getRandomValues(new Uint8Array(18))).toString("base64");
  // The donate page embeds Razorpay Checkout (ADR 0013): its frames are allowed there only, and
  // it may open the bank's / UPI app's window, which COOP same-origin would cut off.
  // Pages that open Razorpay Checkout: donations and paid puja bookings (ADR 0013/0016).
  const donate = ["/donate", "/book-puja"].some((p) => path === p || path.startsWith(`${p}/`));
  const csp = buildCsp(nonce, process.env.NODE_ENV === "development", { razorpay: donate });

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  response.headers.set("Cross-Origin-Opener-Policy", donate ? "same-origin-allow-popups" : "same-origin");
  return response;
}

export const config = {
  matcher: [
    {
      // Pages only: not the API proxy or static build assets.
      source: "/((?!api/|_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
