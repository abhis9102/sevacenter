import type { NextConfig } from "next";

import { STATIC_SECURITY_HEADERS } from "./src/lib/securityHeaders";

const nextConfig: NextConfig = {
  // Don't advertise the framework (X-Powered-By: Next.js).
  poweredByHeader: false,
  reactStrictMode: true,
  // Don't let `next dev` write AGENTS.md / CLAUDE.md files into the project.
  agentRules: false,
  // No source maps shipped to browsers in production builds.
  productionBrowserSourceMaps: false,
  // `next dev` blocks cross-origin requests to its dev assets; *.localhost is our own host.
  allowedDevOrigins: ["*.localhost"],
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [...STATIC_SECURITY_HEADERS],
      },
      {
        // API responses carry personal data: never cache them anywhere.
        source: "/api/:path*",
        headers: [{ key: "Cache-Control", value: "no-store" }],
      },
    ];
  },
};

export default nextConfig;
