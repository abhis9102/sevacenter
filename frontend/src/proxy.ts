import { NextResponse, type NextRequest } from "next/server";

import { buildCsp } from "@/lib/securityHeaders";

/**
 * Per-request CSP nonce for every page (Next.js reads the nonce from the request's CSP header
 * and stamps it on its own scripts). Static security headers live in next.config.ts.
 */
export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.getRandomValues(new Uint8Array(18))).toString("base64");
  // The donate page embeds Razorpay Checkout (ADR 0013): its frames are allowed there only, and
  // it may open the bank's / UPI app's window, which COOP same-origin would cut off.
  // Pages that open Razorpay Checkout: donations and paid puja bookings (ADR 0013/0016).
  const path = request.nextUrl.pathname;
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
