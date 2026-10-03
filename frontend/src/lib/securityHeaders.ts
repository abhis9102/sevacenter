/**
 * Security headers for every response. The CSP needs a per-request nonce, so it is built in
 * `src/proxy.ts` for page requests; the rest are static and set in `next.config.ts`.
 * README.md ("Security headers") explains each choice.
 */

export function buildCsp(nonce: string, isDev: boolean): string {
  const directives: string[] = [
    "default-src 'self'",
    // Nonce + strict-dynamic: only scripts Next.js rendered with this request's nonce run (and
    // what they load). No 'unsafe-inline'; 'unsafe-eval' only in `next dev`, where React needs
    // it to rebuild server error stacks. Production never gets it.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,
    // Dev only: the dev server injects CSS through <style> tags for hot reload.
    `style-src 'self' ${isDev ? "'unsafe-inline'" : `'nonce-${nonce}'`}`,
    "img-src 'self' data:",
    "font-src 'self'",
    // Only our own origin: the API is proxied same-origin. Dev adds the HMR websocket.
    `connect-src 'self'${isDev ? " ws:" : ""}`,
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-src 'none'",
    "frame-ancestors 'none'",
    "manifest-src 'self'",
    "worker-src 'self'",
  ];
  return directives.join("; ");
}

/** Static headers, applied to every route (pages, assets and the /api proxy). */
export const STATIC_SECURITY_HEADERS: ReadonlyArray<{ key: string; value: string }> = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  // Never send a Referer anywhere: URLs can carry search terms (devotee names) and the setup
  // page must never leak anything about its link.
  { key: "Referrer-Policy", value: "no-referrer" },
  // Legacy twin of CSP frame-ancestors for old browsers.
  { key: "X-Frame-Options", value: "DENY" },
  {
    key: "Permissions-Policy",
    value: [
      "accelerometer=()",
      "autoplay=()",
      "browsing-topics=()",
      "camera=()",
      "display-capture=()",
      "geolocation=()",
      "gyroscope=()",
      "hid=()",
      "magnetometer=()",
      "microphone=()",
      "midi=()",
      "payment=()",
      "publickey-credentials-get=()",
      "serial=()",
      "usb=()",
      "xr-spatial-tracking=()",
      // The "copy setup link" button writes to the clipboard: our own pages only.
      "clipboard-write=(self)",
    ].join(", "),
  },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  { key: "Cross-Origin-Resource-Policy", value: "same-origin" },
  // Ignored by browsers over plain http (local dev); enforced once served over TLS.
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
];
