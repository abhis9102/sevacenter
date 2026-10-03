"use client";

import Link from "next/link";
import { useState } from "react";

import { Diya, Wordmark } from "@/components/Diya";
import { LanguageToggle } from "@/components/LanguageToggle";
import { useLanguage } from "@/components/LanguageProvider";
import { Button, Card } from "@/components/ui";

export function LandingPage() {
  const { lang, t } = useLanguage();
  const [subdomain, setSubdomain] = useState("");

  const isHindi = lang === "hi";

  function handleGoToSubdomain(e: React.FormEvent) {
    e.preventDefault();
    const clean = subdomain.trim().toLowerCase();
    if (!clean) return;
    if (typeof window !== "undefined") {
      const { hostname, port, protocol } = window.location;
      const portPart = port ? `:${port}` : "";
      if (hostname === "localhost" || hostname === "127.0.0.1") {
        window.location.href = `${protocol}//${clean}.localhost${portPart}/login`;
      } else {
        window.location.href = `${protocol}//${clean}.${hostname}${portPart}/login`;
      }
    }
  }

  return (
    <div className="min-h-dvh flex flex-col bg-surface text-fg selection:bg-primary/20">
      {/* Top Navbar */}
      <header className="border-b border-line bg-surface/80 backdrop-blur sticky top-0 z-20">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-3">
            <Diya className="size-8 text-primary" />
            <div>
              <Wordmark />
              <p className="hidden text-xs text-muted sm:block">
                {isHindi ? "मंदिर और ट्रस्ट प्रबंधन" : "Platform for temples & trusts"}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3 sm:gap-4">
            <LanguageToggle />
            <Link
              href="/register"
              className="inline-flex items-center justify-center rounded-[10px] bg-primary px-3.5 py-1.5 text-sm font-semibold text-white shadow-xs hover:bg-primary-strong transition-colors"
            >
              {isHindi ? "नया ट्रस्ट जोड़ें" : "Register Temple"}
            </Link>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <main className="flex-1">
        <section className="relative overflow-hidden px-4 py-16 sm:px-6 sm:py-24 text-center">
          <div className="mx-auto max-w-3xl flex flex-col items-center">
            <span className="mb-4 inline-flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3.5 py-1 text-xs font-semibold text-primary-strong">
              <span className="inline-block size-2 rounded-full bg-primary animate-pulse" />
              {isHindi ? "सुरक्षित एवं बहुभाषी डिजिटल मंच" : "Secure, Multi-Tenant Temple Platform"}
            </span>

            <h1 className="text-3xl font-bold tracking-tight sm:text-5xl font-spectral leading-tight">
              {isHindi ? (
                <>
                  मंदिरों और धार्मिक ट्रस्टों के लिए{" "}
                  <span className="text-primary underline decoration-primary/30 decoration-wavy">
                    आधुनिक डिजिटल सेवा
                  </span>
                </>
              ) : (
                <>
                  The Dedicated Digital Platform for{" "}
                  <span className="text-primary underline decoration-primary/30 decoration-wavy">
                    Temples &amp; Trusts
                  </span>
                </>
              )}
            </h1>

            <p className="mt-6 text-base text-muted sm:text-lg max-w-2xl leading-relaxed">
              {isHindi
                ? "भक्त प्रबंधन, 80G दान रसीदें, डिजिटल व्यक्तिगत डेटा सुरक्षा (DPDP 2023), और प्रत्येक मंदिर का अपना अलग उप-डोमेन (subdomain) — सब कुछ एक सुरक्षित जगह पर।"
                : "Devotee directory, compliant 80G tax donation receipts, DPDP 2023 privacy protection, and dedicated subdomains for every temple or trust."}
            </p>

            {/* Quick Actions Card */}
            <div className="mt-10 w-full max-w-xl">
              <Card className="p-6 text-left shadow-md border-line">
                <div className="flex flex-col gap-4">
                  <div className="flex items-center justify-between border-b border-line pb-3">
                    <h2 className="text-base font-semibold">
                      {isHindi ? "अपने मंदिर के पोर्टल पर जाएं" : "Go to Your Temple Portal"}
                    </h2>
                    <span className="text-xs font-mono text-muted">.sevacenter.app</span>
                  </div>

                  <form onSubmit={handleGoToSubdomain} className="flex flex-col sm:flex-row gap-2">
                    <div className="relative flex-1">
                      <input
                        type="text"
                        value={subdomain}
                        onChange={(e) => setSubdomain(e.target.value)}
                        placeholder={isHindi ? "मंदिर का कोड / slug (उदा. demo)" : "Temple code / slug (e.g. demo)"}
                        className="w-full rounded-[10px] border border-line bg-field px-3.5 py-2 text-sm font-mono placeholder:text-muted focus:border-primary focus:outline-none"
                      />
                    </div>
                    <Button type="submit" disabled={!subdomain.trim()}>
                      {isHindi ? "पोर्टल खोलें" : "Open Portal"}
                    </Button>
                  </form>

                  <div className="flex flex-wrap items-center justify-between gap-2 pt-2 text-xs text-muted">
                    <span>
                      {isHindi ? "नया मंदिर या ट्रस्ट है?" : "Setting up for the first time?"}{" "}
                      <Link href="/register" className="font-semibold text-primary hover:underline">
                        {isHindi ? "यहाँ रजिस्टर करें →" : "Register here →"}
                      </Link>
                    </span>
                    <a
                      href="http://demo.localhost:3001/login"
                      className="font-mono text-primary hover:underline hidden sm:inline"
                    >
                      demo.localhost:3001 ↗
                    </a>
                  </div>
                </div>
              </Card>
            </div>
          </div>
        </section>

        {/* Feature Highlights Grid */}
        <section className="border-t border-line bg-surface-2/40 px-4 py-16 sm:px-6 sm:py-20">
          <div className="mx-auto max-w-6xl">
            <div className="text-center max-w-2xl mx-auto mb-12">
              <h2 className="text-2xl font-bold tracking-tight sm:text-3xl font-spectral">
                {isHindi ? "मंदिरों और भक्तों के विश्वास के लिए निर्मित" : "Built for Trust, Privacy & Sacred Service"}
              </h2>
              <p className="mt-3 text-sm text-muted">
                {isHindi
                  ? "प्रत्येक सुविधा मंदिर समिति और दानदाताओं की विशिष्ट आवश्यकताओं को ध्यान में रखकर बनाई गई है।"
                  : "Every capability is engineered specifically for temple administrative trusts and their communities."}
              </p>
            </div>

            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {/* Feature 1 */}
              <div className="rounded-[14px] border border-line bg-surface p-5 shadow-xs flex flex-col justify-between">
                <div>
                  <div className="mb-3 inline-flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-lg">
                    🔒
                  </div>
                  <h3 className="text-base font-semibold">
                    {isHindi ? "पृथक उप-डोमेन व डेटा" : "Per-Temple Subdomains"}
                  </h3>
                  <p className="mt-2 text-xs text-muted leading-relaxed">
                    {isHindi
                      ? "प्रत्येक मंदिर का अपना अलग पता जैसे ram-mandir.sevacenter.app। डेटाबेस स्तर पर पूर्ण सुरक्षा और अलगाव।"
                      : "Independent subdomains with database Row-Level Security ensuring absolute tenant data isolation."}
                  </p>
                </div>
                <div className="mt-4 pt-3 border-t border-line/50 text-[11px] font-mono text-muted">
                  tenant_id RLS enforced
                </div>
              </div>

              {/* Feature 2 */}
              <div className="rounded-[14px] border border-line bg-surface p-5 shadow-xs flex flex-col justify-between">
                <div>
                  <div className="mb-3 inline-flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-lg">
                    📜
                  </div>
                  <h3 className="text-base font-semibold">
                    {isHindi ? "80G दान एवं रसीदें" : "80G Tax Receipts"}
                  </h3>
                  <p className="mt-2 text-xs text-muted leading-relaxed">
                    {isHindi
                      ? "आयकर अधिनियम के 80G(5) प्रावधानों के अनुकूल रसीदें, फॉर्म 10BE अनुपालन और स्वचालित क्रम संख्या।"
                      : "Income Tax compliant 80G receipts with donor PAN verification, Form 10BE alignment, and instant PDF generation."}
                  </p>
                </div>
                <div className="mt-4 pt-3 border-t border-line/50 text-[11px] font-mono text-muted">
                  Form 10BE Ready
                </div>
              </div>

              {/* Feature 3 */}
              <div className="rounded-[14px] border border-line bg-surface p-5 shadow-xs flex flex-col justify-between">
                <div>
                  <div className="mb-3 inline-flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-lg">
                    🛡️
                  </div>
                  <h3 className="text-base font-semibold">
                    {isHindi ? "DPDP 2023 गोपनीयता" : "DPDP Act 2023 Privacy"}
                  </h3>
                  <p className="mt-2 text-xs text-muted leading-relaxed">
                    {isHindi
                      ? "भक्तों की सहमति का रिकॉर्ड, गोपनीय फोन/ईमेल मास्किंग और सख्त ऑडिट लॉगिंग।"
                      : "Explicit consent records (oral, phone, written), masked devotee contact details, and immutable audit logs."}
                  </p>
                </div>
                <div className="mt-4 pt-3 border-t border-line/50 text-[11px] font-mono text-muted">
                  Consent &amp; Audit Trail
                </div>
              </div>

              {/* Feature 4 */}
              <div className="rounded-[14px] border border-line bg-surface p-5 shadow-xs flex flex-col justify-between">
                <div>
                  <div className="mb-3 inline-flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-lg">
                    🕉️
                  </div>
                  <h3 className="text-base font-semibold">
                    {isHindi ? "हिन्दी और अंग्रेजी में" : "Bilingual Devanagari"}
                  </h3>
                  <p className="mt-2 text-xs text-muted leading-relaxed">
                    {isHindi
                      ? "सभी फॉर्म, रसीदें और पृष्ठ हिन्दी और अंग्रेजी दोनों में सुलभ ताकि ट्रस्टी और सेवक आसानी से काम कर सकें।"
                      : "Native bilingual support in English and Hindi (Devanagari) across all interfaces, forms, and receipts."}
                  </p>
                </div>
                <div className="mt-4 pt-3 border-t border-line/50 text-[11px] font-mono text-muted">
                  English / हिन्दी
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-line bg-surface py-8 text-center text-xs text-muted">
        <div className="mx-auto max-w-6xl px-4 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <Diya className="size-5" />
            <span className="font-semibold text-fg">SevaCenter</span>
            <span>·</span>
            <span>{isHindi ? "मंदिरों एवं ट्रस्टों की समर्पित सेवा" : "Dedicated software for temples & trusts"}</span>
          </div>
          <div className="flex items-center gap-4">
            <Link href="/register" className="hover:text-fg">
              {isHindi ? "नया रजिस्ट्रेशन" : "Register"}
            </Link>
            <span>·</span>
            <Link href="/login" className="hover:text-fg">
              {isHindi ? "लॉगिन" : "Login"}
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
