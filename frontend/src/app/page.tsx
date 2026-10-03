import { headers } from "next/headers";
import { redirect } from "next/navigation";

import { LandingPage } from "@/components/LandingPage";
import { isMandirCenter, parseBaseDomains, slugFromHost } from "@/lib/tenant";

export default async function Home() {
  const host = (await headers()).get("host");
  const tenant = slugFromHost(host, parseBaseDomains(process.env.SC_TENANT_BASE_DOMAINS));

  if (tenant) {
    if (isMandirCenter(host)) {
      redirect("/portal");
    }
    redirect("/devotees");
  }

  return <LandingPage />;
}
