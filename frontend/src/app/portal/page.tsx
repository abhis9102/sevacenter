"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya, Wordmark } from "@/components/Diya";
import { useLanguage } from "@/components/LanguageProvider";
import { LanguageToggle } from "@/components/LanguageToggle";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";

interface DevoteeMe {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
  city: string | null;
}

interface PortalInfo {
  slug: string;
  name: string;
  tenantId: number;
}

interface DonationReceipt {
  donationId: number;
  receiptNumber: string;
  donorName: string;
  amountRupees: number;
  mode: string;
  purpose: string;
  date: string;
  mandirName: string;
}

interface DevoteeDonationItem {
  id: number;
  amountRupees: number;
  mode: string;
  purpose: string;
  receivedOn: string;
  reference: string | null;
}

const QUICK_AMOUNTS = [101, 251, 501, 1100, 2100, 5100];

export default function DevoteePortalPage() {
  const tenant = useTenant();
  const { lang, t } = useLanguage();
  const isHindi = lang === "hi";

  const [portalInfo, setPortalInfo] = useState<PortalInfo | null>(null);
  const [me, setMe] = useState<DevoteeMe | null>(null);
  const [activeTab, setActiveTab] = useState<"donate" | "history">("donate");

  // Donation form state
  const [selectedAmount, setSelectedAmount] = useState<number>(501);
  const [customAmount, setCustomAmount] = useState<string>("");
  const [purpose, setPurpose] = useState<string>("general");
  const [mode, setMode] = useState<string>("UPI");
  const [donorName, setDonorName] = useState<string>("");
  const [contact, setContact] = useState<string>("");
  const [city, setCity] = useState<string>("");
  const [donating, setDonating] = useState<boolean>(false);
  const [donateError, setDonateError] = useState<string | null>(null);
  const [activeReceipt, setActiveReceipt] = useState<DonationReceipt | null>(null);

  // Auth modal / state
  const [showAuthModal, setShowAuthModal] = useState<boolean>(false);
  const [authChannel, setAuthChannel] = useState<"PHONE" | "EMAIL">("PHONE");
  const [authIdentifier, setAuthIdentifier] = useState<string>("");
  const [authOtp, setAuthOtp] = useState<string>("");
  const [devOtpHint, setDevOtpHint] = useState<string | null>(null);
  const [authStep, setAuthStep] = useState<"identifier" | "otp" | "register">("identifier");
  const [authBusy, setAuthBusy] = useState<boolean>(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [regFullName, setRegFullName] = useState<string>("");
  const [regConsent, setRegConsent] = useState<boolean>(true);

  // Donation history
  const [history, setHistory] = useState<DevoteeDonationItem[]>([]);
  const [loadingHistory, setLoadingHistory] = useState<boolean>(false);

  // Load temple info and check if devotee is logged in
  useEffect(() => {
    api
      .get<PortalInfo>("/portal/info", { allowUnauthorized: true })
      .then(setPortalInfo)
      .catch(() => {});

    api
      .get<DevoteeMe>("/portal/me", { allowUnauthorized: true })
      .then((user) => {
        setMe(user);
        setDonorName(user.fullName);
        setContact(user.phone || user.email || "");
        if (user.city) setCity(user.city);
      })
      .catch(() => {});
  }, [tenant]);

  // Load devotee donation history when history tab is activated
  useEffect(() => {
    if (activeTab === "history" && me) {
      setLoadingHistory(true);
      api
        .get<DevoteeDonationItem[]>("/portal/donations", { allowUnauthorized: true })
        .then(setHistory)
        .catch(() => {})
        .finally(() => setLoadingHistory(false));
    }
  }, [activeTab, me]);

  function getEffectiveAmount(): number {
    if (customAmount && !isNaN(Number(customAmount)) && Number(customAmount) > 0) {
      return Math.round(Number(customAmount));
    }
    return selectedAmount;
  }

  function getPurposeLabel(key: string): string {
    const map: Record<string, string> = {
      general: t.portal.purposes.general,
      annadanam: t.portal.purposes.annadanam,
      puja: t.portal.purposes.puja,
      construction: t.portal.purposes.construction,
      gaushala: t.portal.purposes.gaushala,
      deepam: t.portal.purposes.deepam,
    };
    return map[key] || t.portal.purposes.general;
  }

  async function handleDonate(e: React.FormEvent) {
    e.preventDefault();
    setDonateError(null);
    const amount = getEffectiveAmount();
    if (amount <= 0) {
      setDonateError(isHindi ? "कृपया वैध दान राशि दर्ज करें" : "Please enter a valid donation amount");
      return;
    }
    if (!donorName.trim()) {
      setDonateError(isHindi ? "कृपया दानदाता का नाम दर्ज करें" : "Please enter donor name");
      return;
    }

    setDonating(true);
    try {
      const res = await api.request<DonationReceipt>("/portal/donations", {
        method: "POST",
        allowUnauthorized: true,
        json: {
          amountRupees: amount,
          donorName: donorName.trim(),
          mode,
          purpose: getPurposeLabel(purpose),
          reference: mode === "UPI" ? `UPI-${Date.now().toString().slice(-6)}` : null,
        },
      });
      setActiveReceipt(res);
      // Refresh history if signed in
      if (me) {
        api.get<DevoteeDonationItem[]>("/portal/donations", { allowUnauthorized: true }).then(setHistory).catch(() => {});
      }
    } catch (err) {
      setDonateError(describeError(err, t.errors));
    } finally {
      setDonating(false);
    }
  }

  async function handleSendOtp(e: React.FormEvent) {
    e.preventDefault();
    setAuthError(null);
    if (!authIdentifier.trim()) return;

    setAuthBusy(true);
    try {
      const res = await api.request<{ message: string; devOtp?: string }>("/portal/auth/send-otp", {
        method: "POST",
        allowUnauthorized: true,
        json: { channel: authChannel, identifier: authIdentifier.trim() },
      });
      if (res.devOtp) {
        setDevOtpHint(res.devOtp);
        setAuthOtp(res.devOtp); // Auto-fill in local dev for testing convenience
      }
      setAuthStep("otp");
    } catch (err) {
      setAuthError(describeError(err, t.errors));
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleVerifyOtp(e: React.FormEvent) {
    e.preventDefault();
    setAuthError(null);
    if (!authOtp.trim()) return;

    setAuthBusy(true);
    try {
      const res = await api.request<{
        authenticated: boolean;
        devotee?: DevoteeMe;
        verifiedIdentifier: string;
      }>("/portal/auth/verify-otp", {
        method: "POST",
        allowUnauthorized: true,
        json: {
          channel: authChannel,
          identifier: authIdentifier.trim(),
          otp: authOtp.trim(),
        },
      });

      if (res.authenticated && res.devotee) {
        setMe(res.devotee);
        setDonorName(res.devotee.fullName);
        setContact(res.devotee.phone || res.devotee.email || "");
        if (res.devotee.city) setCity(res.devotee.city);
        setShowAuthModal(false);
        resetAuthState();
      } else {
        // Needs registration
        setAuthStep("register");
      }
    } catch (err) {
      setAuthError(describeError(err, t.errors));
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setAuthError(null);
    if (!regFullName.trim()) {
      setAuthError(isHindi ? "कृपया अपना नाम दर्ज करें" : "Name is required");
      return;
    }

    setAuthBusy(true);
    try {
      const isPhone = authChannel === "PHONE";
      const res = await api.request<{
        authenticated: boolean;
        devotee: DevoteeMe;
      }>("/portal/auth/register", {
        method: "POST",
        allowUnauthorized: true,
        json: {
          fullName: regFullName.trim(),
          phone: isPhone ? authIdentifier.trim() : null,
          email: !isPhone ? authIdentifier.trim() : null,
          city: city.trim() || null,
        },
      });

      setMe(res.devotee);
      setDonorName(res.devotee.fullName);
      setContact(res.devotee.phone || res.devotee.email || "");
      setShowAuthModal(false);
      resetAuthState();
    } catch (err) {
      setAuthError(describeError(err, t.errors));
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleSignOut() {
    try {
      await api.request("/portal/auth/logout", { method: "POST", allowUnauthorized: true });
    } catch {}
    setMe(null);
    setHistory([]);
    setActiveTab("donate");
  }

  function resetAuthState() {
    setAuthStep("identifier");
    setAuthOtp("");
    setDevOtpHint(null);
    setAuthError(null);
  }

  const mandirTitle = portalInfo?.name || (tenant ? `Mandir (${tenant})` : "Shri Mandir");

  return (
    <div className="min-h-dvh flex flex-col">
      {/* Top Sacred Header */}
      <header className="border-b border-line bg-surface/90 backdrop-blur sticky top-0 z-20">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-3">
            <Diya className="size-8 text-primary" />
            <div>
              <h1 className="font-bold text-base sm:text-lg leading-tight font-spectral">
                {mandirTitle}
              </h1>
              <p className="text-xs text-muted">
                {isHindi ? "भक्त सेवा एवं दान पोर्टल" : "Devotee & Seva Portal"}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <LanguageToggle />
            {me ? (
              <div className="flex items-center gap-2">
                <span className="hidden sm:inline-block text-xs font-semibold text-fg">
                  {me.fullName}
                </span>
                <Button variant="secondary" onClick={handleSignOut} className="text-xs px-2.5 py-1">
                  {t.common.signOut}
                </Button>
              </div>
            ) : (
              <Button
                variant="secondary"
                onClick={() => {
                  resetAuthState();
                  setShowAuthModal(true);
                }}
                className="text-xs px-3 py-1 font-medium"
              >
                {t.portal.signInDevotee}
              </Button>
            )}
          </div>
        </div>
      </header>

      {/* Hero Welcome Banner */}
      <section className="bg-gradient-to-b from-primary/10 via-surface to-surface border-b border-line px-4 py-8 sm:py-10 text-center">
        <div className="mx-auto max-w-2xl">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-3 py-0.5 text-xs font-semibold text-primary-strong mb-3">
            <span className="inline-block size-2 rounded-full bg-primary" />
            {isHindi ? "पवित्र सेवा एवं दान" : "Sacred Seva & Offerings"}
          </span>
          <h2 className="text-2xl sm:text-3xl font-bold font-spectral tracking-tight">
            {mandirTitle}
          </h2>
          <p className="mt-2 text-xs sm:text-sm text-muted max-w-xl mx-auto">
            {t.portal.subtitle}
          </p>

          {/* Navigation Tabs */}
          <div className="mt-6 inline-flex rounded-[12px] bg-surface-2 p-1 border border-line">
            <button
              type="button"
              onClick={() => setActiveTab("donate")}
              className={`rounded-[10px] px-4 py-1.5 text-xs sm:text-sm font-semibold transition-all ${
                activeTab === "donate"
                  ? "bg-surface text-primary-strong shadow-xs"
                  : "text-muted hover:text-fg"
              }`}
            >
              {t.portal.donateTab}
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("history")}
              className={`rounded-[10px] px-4 py-1.5 text-xs sm:text-sm font-semibold transition-all ${
                activeTab === "history"
                  ? "bg-surface text-primary-strong shadow-xs"
                  : "text-muted hover:text-fg"
              }`}
            >
              {t.portal.historyTab}
            </button>
          </div>
        </div>
      </section>

      {/* Main Content Area */}
      <main className="flex-1 mx-auto w-full max-w-2xl px-4 py-8">
        {activeTab === "donate" ? (
          /* Donation Form */
          <Card className="p-6 sm:p-8 shadow-sm">
            <form onSubmit={handleDonate} className="flex flex-col gap-6">
              <div>
                <h3 className="text-lg font-bold font-spectral">{t.portal.donateHeading}</h3>
                <p className="text-xs text-muted mt-1">{t.portal.donateSubtitle}</p>
              </div>

              {donateError ? <Alert tone="danger">{donateError}</Alert> : null}

              {/* Quick Amounts */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-semibold text-fg">{t.portal.quickAmounts}</label>
                <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
                  {QUICK_AMOUNTS.map((amt) => {
                    const active = selectedAmount === amt && !customAmount;
                    return (
                      <button
                        key={amt}
                        type="button"
                        onClick={() => {
                          setSelectedAmount(amt);
                          setCustomAmount("");
                        }}
                        className={`rounded-[10px] border py-2 text-sm font-mono font-semibold transition-colors ${
                          active
                            ? "border-primary bg-primary text-white shadow-xs"
                            : "border-line bg-surface hover:border-primary/50 text-fg"
                        }`}
                      >
                        ₹{amt.toLocaleString("en-IN")}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Custom Amount */}
              <TextField
                label={t.portal.customAmount}
                type="number"
                min="1"
                max="10000000"
                placeholder={isHindi ? "अन्य राशि दर्ज करें (उदा. 5100)" : "Or enter other amount (e.g. 5100)"}
                value={customAmount}
                onChange={(e) => {
                  setCustomAmount(e.target.value);
                  setSelectedAmount(0);
                }}
              />

              {/* Seva Purpose */}
              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium">{t.portal.purpose}</label>
                <select
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                >
                  <option value="general">{t.portal.purposes.general}</option>
                  <option value="annadanam">{t.portal.purposes.annadanam}</option>
                  <option value="puja">{t.portal.purposes.puja}</option>
                  <option value="construction">{t.portal.purposes.construction}</option>
                  <option value="gaushala">{t.portal.purposes.gaushala}</option>
                  <option value="deepam">{t.portal.purposes.deepam}</option>
                </select>
              </div>

              {/* Payment Mode */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-semibold text-fg">{t.portal.paymentMode}</label>
                <div className="grid grid-cols-3 gap-2">
                  {[
                    { id: "UPI", label: isHindi ? "UPI / क्यूआर" : "UPI / QR" },
                    { id: "CARD", label: isHindi ? "डेबिट / क्रेडिट कार्ड" : "Card" },
                    { id: "BANK_TRANSFER", label: isHindi ? "नेट बैंकिंग" : "Net Banking" },
                  ].map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => setMode(m.id)}
                      className={`rounded-[10px] border py-2 text-xs font-semibold transition-colors ${
                        mode === m.id
                          ? "border-primary bg-primary/10 text-primary-strong"
                          : "border-line bg-surface text-muted hover:text-fg"
                      }`}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Donor Details */}
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label={t.portal.donorName}
                  required
                  value={donorName}
                  onChange={(e) => setDonorName(e.target.value)}
                  placeholder={isHindi ? "अपना पूरा नाम" : "Your full name"}
                />
                <TextField
                  label={t.portal.phoneOrEmail}
                  value={contact}
                  onChange={(e) => setContact(e.target.value)}
                  placeholder={isHindi ? "मोबाइल या ईमेल" : "+91 98765 43210"}
                />
              </div>

              {/* No PAN Notice */}
              <div className="rounded-[10px] bg-primary/5 border border-primary/20 p-3 text-xs text-primary-strong flex items-start gap-2">
                <span className="text-sm">✓</span>
                <span>{t.portal.noPanNotice}</span>
              </div>

              {/* Submit CTA */}
              <Button type="submit" busy={donating} className="py-3 text-base font-semibold">
                {t.portal.donateButton} (₹{getEffectiveAmount().toLocaleString("en-IN")})
              </Button>
            </form>
          </Card>
        ) : (
          /* Devotee History Tab */
          <div className="flex flex-col gap-4">
            {!me ? (
              <Card className="p-8 text-center flex flex-col items-center gap-4">
                <div className="size-12 rounded-full bg-primary/10 flex items-center justify-center text-primary text-xl">
                  🕉️
                </div>
                <div>
                  <h3 className="text-base font-bold">{t.portal.signInDevotee}</h3>
                  <p className="text-xs text-muted mt-1 max-w-sm">{t.portal.loginToViewHistory}</p>
                </div>
                <Button
                  onClick={() => {
                    resetAuthState();
                    setShowAuthModal(true);
                  }}
                  className="px-6"
                >
                  {t.portal.signInDevotee}
                </Button>
              </Card>
            ) : loadingHistory ? (
              <div className="text-center py-12 text-sm text-muted">{t.common.loading}</div>
            ) : history.length === 0 ? (
              <Card className="p-8 text-center text-muted text-sm">
                {t.portal.noDonationsYet}
              </Card>
            ) : (
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between px-1">
                  <h3 className="text-sm font-semibold">{t.portal.historyTab}</h3>
                  <span className="text-xs text-muted">
                    {isHindi ? `कुल ${history.length} दान` : `${history.length} records`}
                  </span>
                </div>
                {history.map((item) => (
                  <Card key={item.id} className="p-4 flex items-center justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-base font-bold font-mono text-fg">
                          ₹{item.amountRupees.toLocaleString("en-IN")}
                        </span>
                        <span className="rounded bg-surface-2 px-2 py-0.5 text-[11px] font-mono text-muted">
                          {item.mode}
                        </span>
                      </div>
                      <p className="text-xs text-muted mt-1">{item.purpose}</p>
                      <p className="text-[11px] text-muted/70">{item.receivedOn}</p>
                    </div>
                    <Button
                      variant="secondary"
                      className="text-xs px-3 py-1.5"
                      onClick={() => {
                        setActiveReceipt({
                          donationId: item.id,
                          receiptNumber: `MDR-${portalInfo?.tenantId || 1}-${String(item.id).padStart(6, "0")}`,
                          donorName: me.fullName,
                          amountRupees: item.amountRupees,
                          mode: item.mode,
                          purpose: item.purpose,
                          date: item.receivedOn,
                          mandirName: mandirTitle,
                        });
                      }}
                    >
                      {t.portal.viewReceipt}
                    </Button>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      {/* Instant Receipt Modal / Dialog */}
      {activeReceipt ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md bg-surface border border-line rounded-[16px] shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            {/* Printable Receipt Card */}
            <div id="printable-receipt" className="p-6 flex flex-col gap-4 border-b border-dashed border-line">
              <div className="text-center pb-2 border-b border-line/60">
                <Diya className="size-10 mx-auto text-primary mb-1" />
                <h4 className="text-lg font-bold font-spectral">{activeReceipt.mandirName}</h4>
                <p className="text-xs text-primary font-semibold">
                  {isHindi ? "आधिकारिक डिजिटल दान रसीद" : "Official Digital Seva Receipt"}
                </p>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{t.portal.receiptNumber}:</span>
                <span className="font-mono font-bold text-fg">{activeReceipt.receiptNumber}</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{isHindi ? "दिनांक" : "Date"}:</span>
                <span className="font-mono text-fg">{activeReceipt.date}</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{t.portal.donorName}:</span>
                <span className="font-semibold text-fg">{activeReceipt.donorName}</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{t.portal.purpose}:</span>
                <span className="text-fg">{activeReceipt.purpose}</span>
              </div>

              <div className="rounded-[10px] bg-primary/10 border border-primary/20 p-3 flex justify-between items-center my-2">
                <span className="text-xs font-semibold text-muted">
                  {isHindi ? "दान राशि" : "Amount"}:
                </span>
                <span className="text-xl font-bold font-mono text-primary-strong">
                  ₹{activeReceipt.amountRupees.toLocaleString("en-IN")}
                </span>
              </div>

              <div className="text-center pt-2 text-[11px] text-muted">
                {isHindi
                  ? "हरि ॐ! इस पावन दान के लिए आपका कोटिशः धन्यवाद। भगवान का आशीर्वाद आप पर सदैव बना रहे।"
                  : "Thank you for your sacred contribution. May the blessings of the Almighty be with you."}
              </div>
            </div>

            <div className="p-4 bg-surface-2 flex items-center justify-end gap-2">
              <Button
                variant="secondary"
                onClick={() => window.print()}
                className="text-xs"
              >
                {t.portal.printReceipt}
              </Button>
              <Button onClick={() => setActiveReceipt(null)} className="text-xs">
                {t.portal.closeReceipt}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {/* Devotee Login / OTP Modal */}
      {showAuthModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-sm bg-surface border border-line rounded-[16px] shadow-xl p-6 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between">
              <h4 className="text-lg font-bold font-spectral">{t.portal.signInDevotee}</h4>
              <button
                type="button"
                onClick={() => setShowAuthModal(false)}
                className="text-muted hover:text-fg text-sm"
              >
                ✕
              </button>
            </div>

            {authError ? <Alert tone="danger">{authError}</Alert> : null}

            {authStep === "identifier" ? (
              <form onSubmit={handleSendOtp} className="flex flex-col gap-3">
                {/* Channel toggle: Phone vs Email */}
                <div className="grid grid-cols-2 gap-1 rounded-[10px] bg-surface-2 p-1 border border-line">
                  <button
                    type="button"
                    onClick={() => setAuthChannel("PHONE")}
                    className={`rounded-[8px] py-1 text-xs font-semibold transition-colors ${
                      authChannel === "PHONE"
                        ? "bg-surface text-primary shadow-xs"
                        : "text-muted hover:text-fg"
                    }`}
                  >
                    {t.portal.phoneOtp}
                  </button>
                  <button
                    type="button"
                    onClick={() => setAuthChannel("EMAIL")}
                    className={`rounded-[8px] py-1 text-xs font-semibold transition-colors ${
                      authChannel === "EMAIL"
                        ? "bg-surface text-primary shadow-xs"
                        : "text-muted hover:text-fg"
                    }`}
                  >
                    {t.portal.emailOtp}
                  </button>
                </div>

                <TextField
                  label={authChannel === "PHONE" ? t.portal.enterPhone : t.portal.enterEmail}
                  type={authChannel === "PHONE" ? "tel" : "email"}
                  required
                  placeholder={authChannel === "PHONE" ? "+91 98765 43210" : "devotee@example.com"}
                  value={authIdentifier}
                  onChange={(e) => setAuthIdentifier(e.target.value)}
                />

                <Button type="submit" busy={authBusy} disabled={!authIdentifier.trim()}>
                  {authBusy ? t.portal.sendingOtp : t.portal.sendOtp}
                </Button>
              </form>
            ) : authStep === "otp" ? (
              <form onSubmit={handleVerifyOtp} className="flex flex-col gap-3">
                <p className="text-xs text-muted">
                  {isHindi
                    ? `${authIdentifier} पर भेजा गया 6 अंकों का OTP दर्ज करें:`
                    : `Enter the 6-digit OTP sent to ${authIdentifier}:`}
                </p>

                {devOtpHint ? (
                  <div className="rounded-[8px] bg-primary/10 border border-primary/20 p-2 text-xs font-mono text-primary-strong">
                    {t.portal.devOtpNotice}: <span className="font-bold">{devOtpHint}</span>
                  </div>
                ) : null}

                <TextField
                  label={t.portal.enterOtp}
                  type="text"
                  maxLength={6}
                  required
                  className="font-mono text-center text-lg tracking-widest"
                  placeholder="123456"
                  value={authOtp}
                  onChange={(e) => setAuthOtp(e.target.value)}
                />

                <Button type="submit" busy={authBusy} disabled={authOtp.trim().length < 4}>
                  {authBusy ? t.portal.verifyingOtp : t.portal.verifyOtp}
                </Button>

                <button
                  type="button"
                  onClick={() => setAuthStep("identifier")}
                  className="text-xs text-primary hover:underline text-center"
                >
                  {isHindi ? "← वापस बदलें" : "← Change number or email"}
                </button>
              </form>
            ) : (
              /* Register profile step for new devotees */
              <form onSubmit={handleRegister} className="flex flex-col gap-3">
                <p className="text-xs text-muted">{t.portal.registerPrompt}</p>

                <TextField
                  label={t.portal.donorName}
                  required
                  placeholder={isHindi ? "अपना पूरा नाम" : "Your full name"}
                  value={regFullName}
                  onChange={(e) => setRegFullName(e.target.value)}
                />

                <TextField
                  label={t.portal.city}
                  placeholder={isHindi ? "शहर (उदा. अयोध्या)" : "City (e.g. Ayodhya)"}
                  value={city}
                  onChange={(e) => setCity(e.target.value)}
                />

                <label className="flex items-start gap-2 text-xs text-muted cursor-pointer mt-1">
                  <input
                    type="checkbox"
                    checked={regConsent}
                    onChange={(e) => setRegConsent(e.target.checked)}
                    className="mt-0.5 accent-primary"
                  />
                  <span>{t.portal.consentNotice}</span>
                </label>

                <Button type="submit" busy={authBusy} disabled={!regFullName.trim() || !regConsent}>
                  {t.portal.registerButton}
                </Button>
              </form>
            )}
          </div>
        </div>
      ) : null}

      {/* Footer */}
      <footer className="border-t border-line bg-surface py-6 text-center text-xs text-muted mt-auto">
        <div className="mx-auto max-w-4xl px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Diya className="size-4" />
            <span>{mandirTitle}</span>
            <span>·</span>
            <span className="font-mono text-[11px]">Powered by SevaCenter</span>
          </div>
          <div className="flex items-center gap-3">
            <Link href="/login" className="hover:text-fg">
              {isHindi ? "मंदिर ट्रस्टी / स्टाफ़ लॉगिन →" : "Trustee & Staff Login →"}
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
