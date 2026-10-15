import { headers } from "next/headers";
import { redirect } from "next/navigation";

import { LandingPage } from "@/components/LandingPage";
import { TemplePublicPage } from "@/components/TemplePublicPage";
import { siteForHost } from "@/lib/brand";

export default async function Home() {
  const host = (await headers()).get("host");
  const site = siteForHost(host);

  // A temple's own domain (ADR 0036): its temple page; the backend resolves which trust.
  if (site.custom) {
    return <TemplePublicPage />;
  }

  // Single-domain / dev: one host serves both brands by path (original behaviour).
  if (site.singleDomain || site.brand === null) {
    return site.slug ? <TemplePublicPage /> : <LandingPage />;
  }

  // MandirCenter (public): a trust's own host shows its temple page; the bare domain, marketing.
  if (site.brand === "mandir") {
    return site.slug ? <TemplePublicPage /> : <LandingPage />;
  }

  // SevaCenter (staff): a trust's own host sends staff to sign-in; the bare domain is the landing.
  if (site.slug) {
    redirect("/login");
  }
  return <LandingPage />;
}
