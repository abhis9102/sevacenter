import { headers } from "next/headers";

import { LandingPage } from "@/components/LandingPage";
import { TemplePublicPage } from "@/components/TemplePublicPage";
import { parseBaseDomains, slugFromHost } from "@/lib/tenant";

export default async function Home() {
  const host = (await headers()).get("host");
  const tenant = slugFromHost(host, parseBaseDomains(process.env.SC_TENANT_BASE_DOMAINS));

  // A trust's own host: its public temple page (ADR 0017). Staff sign in from there and land on
  // /devotees after login.
  if (tenant) {
    return <TemplePublicPage />;
  }

  return <LandingPage />;
}
