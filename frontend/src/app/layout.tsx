import type { Metadata } from "next";
import { IBM_Plex_Mono, Mukta, Spectral } from "next/font/google";
import { headers } from "next/headers";

import { LanguageProvider } from "@/components/LanguageProvider";
import { TenantProvider } from "@/components/TenantProvider";
import { parseBaseDomains, slugFromHost } from "@/lib/tenant";

import "./globals.css";

// Self-hosted at build time by next/font: no request to Google at runtime (CSP font-src 'self').
const spectral = Spectral({ subsets: ["latin"], weight: ["500", "600"], variable: "--font-spectral" });
const mukta = Mukta({ subsets: ["latin", "devanagari"], weight: ["400", "500", "600"], variable: "--font-mukta" });
const plexMono = IBM_Plex_Mono({ subsets: ["latin"], weight: ["400", "500"], variable: "--font-plex-mono" });

export const metadata: Metadata = {
  title: { default: "SevaCenter", template: "%s · SevaCenter" },
  description: "Admin for temples and religious trusts",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Reading request headers makes every page dynamic, which per-request CSP nonces require.
  const host = (await headers()).get("host");
  const tenant = slugFromHost(host, parseBaseDomains(process.env.SC_TENANT_BASE_DOMAINS));

  return (
    <html lang="en" className={`${spectral.variable} ${mukta.variable} ${plexMono.variable}`}>
      <body className="min-h-dvh antialiased">
        <TenantProvider tenant={tenant}>
          <LanguageProvider>{children}</LanguageProvider>
        </TenantProvider>
      </body>
    </html>
  );
}
