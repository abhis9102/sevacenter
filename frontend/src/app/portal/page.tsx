"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya } from "@/components/Diya";
import { useLanguage } from "@/components/LanguageProvider";
import { LanguageToggle } from "@/components/LanguageToggle";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import type {
  DarshanPassResponse,
  MandirEvent,
  MandirSchedule,
  PujaBookingResponse,
  PujaItem,
  SevakResponse,
} from "@/lib/types";

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

type PortalTab = "home" | "pujas" | "donate" | "events" | "volunteer" | "my-mandir";

const QUICK_AMOUNTS = [101, 251, 501, 1100, 2100, 5100];

function QrCodeSvg({ value, size = 160 }: { value: string; size?: number }) {
  const N = 21;
  const matrix: boolean[][] = Array.from({ length: N }, () => Array(N).fill(false));

  function fillFinder(startR: number, startC: number) {
    for (let r = 0; r < 7; r++) {
      for (let c = 0; c < 7; c++) {
        const isBorder = r === 0 || r === 6 || c === 0 || c === 6;
        const isCenter = r >= 2 && r <= 4 && c >= 2 && c <= 4;
        const row = matrix[startR + r];
        if (row) {
          row[startC + c] = isBorder || isCenter;
        }
      }
    }
  }

  // Corner finders
  fillFinder(0, 0);
  fillFinder(0, 14);
  fillFinder(14, 0);

  // Timing patterns
  for (let i = 8; i < 13; i++) {
    const row6 = matrix[6];
    if (row6) row6[i] = i % 2 === 0;
    const rowI = matrix[i];
    if (rowI) rowI[6] = i % 2 === 0;
  }

  // Deterministic pattern from value string
  let hash = 2166136261;
  for (let i = 0; i < value.length; i++) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }

  let bitIdx = 0;
  for (let r = 0; r < N; r++) {
    for (let c = 0; c < N; c++) {
      const inFinder1 = r < 8 && c < 8;
      const inFinder2 = r < 8 && c >= 13;
      const inFinder3 = r >= 13 && c < 8;
      const inTiming = r === 6 || c === 6;
      if (inFinder1 || inFinder2 || inFinder3 || inTiming) continue;

      const pseudoBit = ((hash >> (bitIdx % 31)) ^ (r * 7 + c * 13 + bitIdx)) & 1;
      const row = matrix[r];
      if (row) {
        row[c] = pseudoBit === 1;
      }
      bitIdx++;
    }
  }

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${N} ${N}`}
      className="bg-white p-2 rounded shadow-xs border border-line mx-auto"
      aria-label="QR Code"
    >
      {matrix.flatMap((row, r) =>
        row.map((filled, c) =>
          filled ? <rect key={`${r}-${c}`} x={c} y={r} width={1} height={1} fill="#1a1a1a" /> : null
        )
      )}
    </svg>
  );
}

export default function MandirCenterPage() {
  const tenant = useTenant();
  const { lang, t } = useLanguage();
  const isHindi = lang === "hi";

  const [activeTab, setActiveTab] = useState<PortalTab>("home");
  const [portalInfo, setPortalInfo] = useState<PortalInfo | null>(null);
  const [me, setMe] = useState<DevoteeMe | null>(null);

  // Schedules, Pujas, Events
  const [schedule, setSchedule] = useState<MandirSchedule | null>(null);
  const [pujas, setPujas] = useState<PujaItem[]>([]);
  const [events, setEvents] = useState<MandirEvent[]>([]);

  // Devotee History / My Mandir
  const [myDonations, setMyDonations] = useState<DevoteeDonationItem[]>([]);
  const [myPujas, setMyPujas] = useState<PujaBookingResponse[]>([]);
  const [myPasses, setMyPasses] = useState<DarshanPassResponse[]>([]);
  const [myMandirSubTab, setMyMandirSubTab] = useState<"donations" | "pujas" | "passes">("donations");

  // Donation state
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

  // Booking Puja Modal & State
  const [bookingPujaModal, setBookingPujaModal] = useState<PujaItem | null>(null);
  const [pujaDate, setPujaDate] = useState<string>("");
  const [pujaSlot, setPujaSlot] = useState<string>("07:00 AM - 08:30 AM");
  const [gotra, setGotra] = useState<string>("Kashyap");
  const [nakshatra, setNakshatra] = useState<string>("");
  const [rashi, setRashi] = useState<string>("");
  const [familyMembers, setFamilyMembers] = useState<string>("");
  const [pujaBusy, setPujaBusy] = useState<boolean>(false);
  const [pujaError, setPujaError] = useState<string | null>(null);
  const [confirmedPujaBooking, setConfirmedPujaBooking] = useState<PujaBookingResponse | null>(null);

  // Darshan Pass Modal & State
  const [passEventModal, setPassEventModal] = useState<MandirEvent | null>(null);
  const [passVisitDate, setPassVisitDate] = useState<string>("");
  const [passSlot, setPassSlot] = useState<string>("06:00 AM - 08:00 AM");
  const [attendeeCount, setAttendeeCount] = useState<number>(2);
  const [passContact, setPassContact] = useState<string>("");
  const [passBusy, setPassBusy] = useState<boolean>(false);
  const [passError, setPassError] = useState<string | null>(null);
  const [confirmedPass, setConfirmedPass] = useState<DarshanPassResponse | null>(null);

  // Sevak / Volunteer state
  const [sevakName, setSevakName] = useState<string>("");
  const [sevakContact, setSevakContact] = useState<string>("");
  const [sevaArea, setSevaArea] = useState<string>("Mahaprasad & Bhandara");
  const [availableDays, setAvailableDays] = useState<string>("Weekends");
  const [shiftPreference, setShiftPreference] = useState<string>("Morning");
  const [sevakNotes, setSevakNotes] = useState<string>("");
  const [sevakBusy, setSevakBusy] = useState<boolean>(false);
  const [sevakError, setSevakError] = useState<string | null>(null);
  const [sevakSuccess, setSevakSuccess] = useState<SevakResponse | null>(null);

  // Auth modal state
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

  // Load initial portal metadata, schedule, and auth state
  useEffect(() => {
    let mounted = true;

    void (async () => {
      try {
        const info = await api.get<PortalInfo>("/portal/info", { allowUnauthorized: true });
        if (mounted) setPortalInfo(info);
      } catch {}

      try {
        const sched = await api.get<MandirSchedule>("/portal/schedule", { allowUnauthorized: true });
        if (mounted) setSchedule(sched);
      } catch {}

      try {
        const user = await api.get<DevoteeMe>("/portal/me", { allowUnauthorized: true });
        if (mounted) {
          setMe(user);
          setDonorName(user.fullName);
          setSevakName(user.fullName);
          const c = user.phone || user.email || "";
          setContact(c);
          setPassContact(c);
          setSevakContact(c);
          if (user.city) setCity(user.city);
        }
      } catch {}
    })();

    return () => {
      mounted = false;
    };
  }, [tenant]);

  // Load pujas catalog when pujas tab is opened or on init
  useEffect(() => {
    if (activeTab === "pujas" && pujas.length === 0) {
      let mounted = true;
      void (async () => {
        try {
          const list = await api.get<PujaItem[]>("/portal/pujas", { allowUnauthorized: true });
          if (mounted) setPujas(list);
        } catch {}
      })();
      return () => {
        mounted = false;
      };
    }
  }, [activeTab, pujas.length]);

  // Load events when events tab is opened
  useEffect(() => {
    if (activeTab === "events" && events.length === 0) {
      let mounted = true;
      void (async () => {
        try {
          const list = await api.get<MandirEvent[]>("/portal/events", { allowUnauthorized: true });
          if (mounted) setEvents(list);
        } catch {}
      })();
      return () => {
        mounted = false;
      };
    }
  }, [activeTab, events.length]);

  // Refresh devotee history records when My Mandir tab is opened
  useEffect(() => {
    if (activeTab === "my-mandir" && me) {
      let mounted = true;
      void (async () => {
        try {
          const dons = await api.get<DevoteeDonationItem[]>("/portal/donations", { allowUnauthorized: true });
          if (mounted) setMyDonations(dons);
        } catch {}

        try {
          const pjs = await api.get<PujaBookingResponse[]>("/portal/pujas/my-bookings", { allowUnauthorized: true });
          if (mounted) setMyPujas(pjs);
        } catch {}

        try {
          const passes = await api.get<DarshanPassResponse[]>("/portal/events/my-passes", { allowUnauthorized: true });
          if (mounted) setMyPasses(passes);
        } catch {}
      })();
      return () => {
        mounted = false;
      };
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
      if (me) {
        api.get<DevoteeDonationItem[]>("/portal/donations", { allowUnauthorized: true }).then(setMyDonations).catch(() => {});
      }
    } catch (err) {
      setDonateError(describeError(err, t.errors));
    } finally {
      setDonating(false);
    }
  }

  async function handleBookPuja(e: React.FormEvent) {
    e.preventDefault();
    if (!bookingPujaModal) return;
    setPujaError(null);

    const name = donorName.trim() || me?.fullName || "";
    if (!name) {
      setPujaError(isHindi ? "कृपया भक्त का नाम दर्ज करें" : "Devotee name is required");
      return;
    }

    setPujaBusy(true);
    try {
      const res = await api.request<PujaBookingResponse>("/portal/pujas/book", {
        method: "POST",
        allowUnauthorized: true,
        json: {
          pujaCode: bookingPujaModal.code,
          pujaDate: pujaDate || new Date(Date.now() + 86400000).toISOString().split("T")[0],
          timeSlot: pujaSlot,
          devoteeName: name,
          gotra: gotra.trim() || null,
          nakshatra: nakshatra.trim() || null,
          rashi: rashi.trim() || null,
          familyMembers: familyMembers.trim() || null,
          contact: contact.trim() || null,
          paymentMode: "UPI",
        },
      });
      setConfirmedPujaBooking(res);
      setBookingPujaModal(null);
      if (me) {
        api.get<PujaBookingResponse[]>("/portal/pujas/my-bookings", { allowUnauthorized: true }).then(setMyPujas).catch(() => {});
      }
    } catch (err) {
      setPujaError(describeError(err, t.errors));
    } finally {
      setPujaBusy(false);
    }
  }

  async function handleBookPass(e: React.FormEvent) {
    e.preventDefault();
    if (!passEventModal) return;
    setPassError(null);

    const name = donorName.trim() || me?.fullName || "";
    if (!name) {
      setPassError(isHindi ? "कृपया प्रमुख भक्त का नाम दर्ज करें" : "Devotee name is required");
      return;
    }
    const c = passContact.trim() || contact.trim();
    if (!c) {
      setPassError(isHindi ? "कृपया मोबाइल या ईमेल दर्ज करें" : "Contact is required for pass");
      return;
    }

    setPassBusy(true);
    try {
      const res = await api.request<DarshanPassResponse>("/portal/events/pass", {
        method: "POST",
        allowUnauthorized: true,
        json: {
          eventCode: passEventModal.code,
          visitDate: passVisitDate || passEventModal.date,
          timeSlot: passSlot,
          primaryDevoteeName: name,
          attendeeCount: attendeeCount,
          contact: c,
        },
      });
      setConfirmedPass(res);
      setPassEventModal(null);
      if (me) {
        api.get<DarshanPassResponse[]>("/portal/events/my-passes", { allowUnauthorized: true }).then(setMyPasses).catch(() => {});
      }
    } catch (err) {
      setPassError(describeError(err, t.errors));
    } finally {
      setPassBusy(false);
    }
  }

  async function handleVolunteerSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSevakError(null);
    if (!sevakName.trim()) {
      setSevakError(isHindi ? "कृपया अपना नाम दर्ज करें" : "Name is required");
      return;
    }
    if (!sevakContact.trim()) {
      setSevakError(isHindi ? "कृपया संपर्क नंबर या ईमेल दर्ज करें" : "Contact details are required");
      return;
    }

    setSevakBusy(true);
    try {
      const res = await api.request<SevakResponse>("/portal/volunteer", {
        method: "POST",
        allowUnauthorized: true,
        json: {
          fullName: sevakName.trim(),
          contact: sevakContact.trim(),
          sevaArea,
          availableDays,
          shiftPreference,
          notes: sevakNotes.trim() || null,
        },
      });
      setSevakSuccess(res);
    } catch (err) {
      setSevakError(describeError(err, t.errors));
    } finally {
      setSevakBusy(false);
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
        setAuthOtp(res.devOtp);
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
        setSevakName(res.devotee.fullName);
        const c = res.devotee.phone || res.devotee.email || "";
        setContact(c);
        setPassContact(c);
        setSevakContact(c);
        if (res.devotee.city) setCity(res.devotee.city);
        setShowAuthModal(false);
        resetAuthState();
      } else {
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
      setSevakName(res.devotee.fullName);
      const c = res.devotee.phone || res.devotee.email || "";
      setContact(c);
      setPassContact(c);
      setSevakContact(c);
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
    setMyDonations([]);
    setMyPujas([]);
    setMyPasses([]);
    setActiveTab("home");
  }

  function resetAuthState() {
    setAuthStep("identifier");
    setAuthOtp("");
    setDevOtpHint(null);
    setAuthError(null);
  }

  const mandirTitle = portalInfo?.name || schedule?.mandirName || (tenant ? `Mandir (${tenant})` : "Shri Mandir");

  return (
    <div className="min-h-dvh flex flex-col bg-canvas text-fg">
      {/* Top Sacred Header */}
      <header className="border-b border-line bg-surface/95 backdrop-blur sticky top-0 z-30 shadow-xs">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-2.5 sm:px-6">
          <div className="flex items-center gap-3">
            <Diya className="size-8 text-primary shrink-0" />
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-bold text-base sm:text-lg leading-tight font-spectral">
                  {mandirTitle}
                </h1>
                <span className="hidden sm:inline-flex items-center rounded-full bg-primary/10 border border-primary/20 px-2 py-0.5 text-[10px] font-semibold text-primary-strong">
                  {t.portal.title}
                </span>
              </div>
              <p className="text-xs text-muted">
                {isHindi ? "दैनिक दर्शन, सेवा, पूजा एवं उत्सव केंद्र" : "Sacred Mandir Hub for Darshan, Pujas & Seva"}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <LanguageToggle />
            {me ? (
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setActiveTab("my-mandir")}
                  className="hidden sm:flex items-center gap-1.5 rounded-full border border-line bg-surface-2 px-3 py-1 text-xs font-semibold hover:border-primary/50 transition-colors"
                >
                  <span className="text-primary">🕉️</span>
                  <span>{me.fullName}</span>
                </button>
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

        {/* Live Darshan Status Banner */}
        <div className="bg-surface-2 border-t border-line px-4 py-1.5 text-xs text-center flex items-center justify-center gap-2 font-medium">
          {schedule?.isOpenNow ? (
            <>
              <span className="size-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-emerald-700 font-semibold">{t.portal.openForDarshan}</span>
              <span className="text-muted hidden sm:inline">
                · {isHindi ? "प्रातः काल" : "Morning"}: {schedule.morningHours} | {isHindi ? "सायं काल" : "Evening"}: {schedule.eveningHours}
              </span>
            </>
          ) : (
            <>
              <span className="size-2 rounded-full bg-amber-500" />
              <span className="text-amber-800 font-semibold">{t.portal.templeClosed}</span>
              <span className="text-muted hidden sm:inline">
                · {isHindi ? "दर्शन समय" : "Darshan hours"}: {schedule?.morningHours || "6:00 AM - 12:00 PM"} & {schedule?.eveningHours || "4:30 PM - 9:00 PM"}
              </span>
            </>
          )}
        </div>
      </header>

      {/* Navigation Tabs Bar */}
      <nav className="border-b border-line bg-surface/80 backdrop-blur sticky top-[77px] z-20 overflow-x-auto scrollbar-none">
        <div className="mx-auto flex max-w-5xl items-center gap-1 px-4 py-2 sm:px-6 min-w-max">
          {[
            { id: "home", label: t.portal.homeTab, icon: "🏛️" },
            { id: "pujas", label: t.portal.pujasTab, icon: "🕉️" },
            { id: "donate", label: t.portal.donateTab, icon: "🪔" },
            { id: "events", label: t.portal.eventsTab, icon: "📅" },
            { id: "volunteer", label: t.portal.volunteerTab, icon: "🤝" },
            { id: "my-mandir", label: t.portal.myMandirTab, icon: "👤" },
          ].map((tab) => {
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id as PortalTab)}
                className={`flex items-center gap-1.5 rounded-[10px] px-3.5 py-1.5 text-xs sm:text-sm font-semibold transition-all ${
                  active
                    ? "bg-primary text-white shadow-xs"
                    : "text-muted hover:bg-surface-2 hover:text-fg"
                }`}
              >
                <span>{tab.icon}</span>
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Main Content Area */}
      <main className="flex-1 mx-auto w-full max-w-5xl px-4 py-6 sm:px-6">
        {/* ========================================================================= */}
        {/* TAB 1: MANDIR HOME */}
        {/* ========================================================================= */}
        {activeTab === "home" && (
          <div className="flex flex-col gap-6">
            {/* Sacred Welcome & Panchang Hero */}
            <div className="rounded-[16px] bg-gradient-to-br from-primary/15 via-primary/5 to-surface border border-primary/20 p-6 sm:p-8 relative overflow-hidden shadow-xs">
              <div className="max-w-2xl">
                <span className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-3 py-0.5 text-xs font-semibold text-primary-strong mb-3">
                  <Diya className="size-3.5 text-primary" />
                  {isHindi ? "पवित्र मंदिर दर्शन" : "Sacred Mandir Darshan"}
                </span>
                <h2 className="text-2xl sm:text-3xl font-bold font-spectral tracking-tight">
                  {mandirTitle}
                </h2>
                <p className="mt-1 text-sm text-muted">
                  {schedule?.deity ? (isHindi ? `आराध्य देव: ${schedule.deity}` : `Presiding Deity: ${schedule.deity}`) : ""}
                  {schedule?.address ? ` · ${schedule.address}` : ""}
                </p>

                {/* Panchang & Tithi Badge */}
                {schedule?.panchangTithi ? (
                  <div className="mt-4 inline-flex flex-wrap items-center gap-2 rounded-[12px] bg-surface/90 border border-primary/20 px-3 py-2 text-xs">
                    <span className="font-semibold text-primary-strong flex items-center gap-1">
                      <span>🪔</span> {t.portal.dailyPanchang}:
                    </span>
                    <span className="font-medium text-fg">{schedule.panchangTithi}</span>
                    {schedule.nakshatra ? (
                      <span className="text-muted">
                        · {isHindi ? "नक्षत्र" : "Nakshatra"}: {schedule.nakshatra}
                      </span>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>

            {/* Special Announcement Banner */}
            {schedule?.specialAnnouncement ? (
              <div className="rounded-[12px] bg-amber-500/10 border border-amber-500/30 p-4 text-xs sm:text-sm text-amber-950 flex items-start gap-3">
                <span className="text-lg">📢</span>
                <div>
                  <h4 className="font-bold text-amber-900 mb-0.5">{t.portal.announcements}</h4>
                  <p>{schedule.specialAnnouncement}</p>
                </div>
              </div>
            ) : null}

            {/* Quick Action Cards Grid */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <button
                type="button"
                onClick={() => setActiveTab("pujas")}
                className="flex flex-col items-center justify-center p-4 rounded-[14px] bg-surface border border-line hover:border-primary/50 hover:shadow-xs transition-all text-center group"
              >
                <span className="text-3xl mb-2 group-hover:scale-110 transition-transform">🕉️</span>
                <span className="text-xs sm:text-sm font-bold text-fg">{t.portal.bookPuja}</span>
                <span className="text-[11px] text-muted mt-0.5">{isHindi ? "सपरिवार ससंकल्प" : "With Family Sankalp"}</span>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab("donate")}
                className="flex flex-col items-center justify-center p-4 rounded-[14px] bg-surface border border-line hover:border-primary/50 hover:shadow-xs transition-all text-center group"
              >
                <span className="text-3xl mb-2 group-hover:scale-110 transition-transform">🪔</span>
                <span className="text-xs sm:text-sm font-bold text-fg">{t.portal.donateTab}</span>
                <span className="text-[11px] text-muted mt-0.5">{isHindi ? "अन्नदान व सेवा" : "Annadanam & Seva"}</span>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab("events")}
                className="flex flex-col items-center justify-center p-4 rounded-[14px] bg-surface border border-line hover:border-primary/50 hover:shadow-xs transition-all text-center group"
              >
                <span className="text-3xl mb-2 group-hover:scale-110 transition-transform">🎫</span>
                <span className="text-xs sm:text-sm font-bold text-fg">{t.portal.bookPass}</span>
                <span className="text-[11px] text-muted mt-0.5">{isHindi ? "डिजिटल ई-टोकन" : "E-Token Pass"}</span>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab("volunteer")}
                className="flex flex-col items-center justify-center p-4 rounded-[14px] bg-surface border border-line hover:border-primary/50 hover:shadow-xs transition-all text-center group"
              >
                <span className="text-3xl mb-2 group-hover:scale-110 transition-transform">🤝</span>
                <span className="text-xs sm:text-sm font-bold text-fg">{t.portal.volunteerTab}</span>
                <span className="text-[11px] text-muted mt-0.5">{isHindi ? "सेवादार पंजीकरण" : "Serve Mandir"}</span>
              </button>
            </div>

            {/* Daily Aarti Timings Schedule */}
            <Card className="p-6">
              <div className="flex items-center justify-between pb-4 border-b border-line mb-4">
                <div>
                  <h3 className="text-base sm:text-lg font-bold font-spectral">{t.portal.todayAarti}</h3>
                  <p className="text-xs text-muted">{t.portal.darshanSchedule}</p>
                </div>
                <span className="text-xs rounded-full bg-primary/10 border border-primary/20 px-2.5 py-1 text-primary-strong font-medium">
                  {schedule?.aartis?.length || 5} {isHindi ? "दैनिक आरतियां" : "Daily Aartis"}
                </span>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {(schedule?.aartis || [
                  { name: "Mangala Aarti", time: "05:30 AM", description: "Brahma Muhurta awakening of the deity with sacred hymns" },
                  { name: "Shringar Aarti", time: "07:30 AM", description: "Sacred alankaram, floral adornment and morning prayers" },
                  { name: "Rajbhog Aarti", time: "12:00 PM", description: "Noon naivedyam & grand prasadam offering" },
                  { name: "Sandhya Aarti", time: "07:00 PM", description: "Evening twilight deepam worship and shankhnaad" },
                  { name: "Shayan Aarti", time: "09:30 PM", description: "Night resting prayers and conclusion of daily darshan" },
                ]).map((aarti, idx) => (
                  <div
                    key={idx}
                    className="flex flex-col justify-between p-3.5 rounded-[12px] bg-surface-2 border border-line"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-bold text-sm text-fg">{aarti.name}</span>
                      <span className="font-mono text-xs font-bold text-primary bg-primary/10 px-2 py-0.5 rounded-full shrink-0">
                        {aarti.time}
                      </span>
                    </div>
                    <p className="text-xs text-muted mt-2 leading-relaxed">{aarti.description}</p>
                  </div>
                ))}
              </div>
            </Card>

            {/* Helpline & Mandir Info */}
            <div className="flex flex-col sm:flex-row gap-4">
              <Card className="p-4 flex-1 flex items-center gap-3">
                <span className="text-2xl">📞</span>
                <div>
                  <h4 className="text-xs font-bold text-muted uppercase tracking-wider">{isHindi ? "मंदिर हेल्पलाइन" : "Mandir Helpline"}</h4>
                  <p className="text-sm font-semibold font-mono text-fg">{schedule?.helpline || "+91 98765 43210"}</p>
                </div>
              </Card>
              <Card className="p-4 flex-1 flex items-center gap-3">
                <span className="text-2xl">📍</span>
                <div>
                  <h4 className="text-xs font-bold text-muted uppercase tracking-wider">{isHindi ? "मंदिर परिसर" : "Mandir Campus"}</h4>
                  <p className="text-xs font-medium text-fg">{schedule?.address || "Main Temple Road, Holy Sanctum"}</p>
                </div>
              </Card>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 2: PUJAS & SANKALP */}
        {/* ========================================================================= */}
        {activeTab === "pujas" && (
          <div className="flex flex-col gap-6">
            <div>
              <h2 className="text-xl sm:text-2xl font-bold font-spectral">{t.portal.pujasHeading}</h2>
              <p className="text-xs sm:text-sm text-muted mt-1">{t.portal.pujasSubtitle}</p>
            </div>

            {pujas.length === 0 ? (
              <div className="text-center py-12 text-muted text-sm">{t.common.loading}</div>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {pujas.map((item) => (
                  <Card key={item.code} className="p-5 flex flex-col justify-between hover:border-primary/50 transition-all">
                    <div>
                      <div className="flex items-start justify-between gap-2 mb-2">
                        <span className="rounded-full bg-primary/10 border border-primary/20 px-2 py-0.5 text-[11px] font-semibold text-primary">
                          {item.deity}
                        </span>
                        <span className="text-base font-bold font-mono text-primary-strong">
                          ₹{item.dakshinaRupees.toLocaleString("en-IN")}
                        </span>
                      </div>
                      <h3 className="text-base font-bold font-spectral text-fg">{item.name}</h3>
                      <p className="text-xs text-muted mt-2 leading-relaxed">{item.description}</p>
                      <div className="mt-3 flex items-center gap-3 text-xs text-muted">
                        <span>⏱️ {item.duration}</span>
                        {item.prasadIncluded ? (
                          <span className="text-emerald-700 font-medium">✓ {isHindi ? "महाप्रसाद सहित" : "Holy Prasad Included"}</span>
                        ) : null}
                      </div>
                    </div>

                    <Button
                      onClick={() => {
                        setBookingPujaModal(item);
                        setPujaDate(new Date(Date.now() + 86400000).toISOString().split("T")[0] ?? "");
                        setPujaError(null);
                      }}
                      className="mt-4 text-xs py-2"
                    >
                      {t.portal.bookPuja}
                    </Button>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 3: SEVA & DAAN (DONATIONS) */}
        {/* ========================================================================= */}
        {activeTab === "donate" && (
          <div className="mx-auto max-w-2xl">
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
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 4: UTSAVS & CALENDAR */}
        {/* ========================================================================= */}
        {activeTab === "events" && (
          <div className="flex flex-col gap-6">
            <div>
              <h2 className="text-xl sm:text-2xl font-bold font-spectral">{t.portal.eventsHeading}</h2>
              <p className="text-xs sm:text-sm text-muted mt-1">{t.portal.eventsSubtitle}</p>
            </div>

            {events.length === 0 ? (
              <div className="text-center py-12 text-muted text-sm">{t.common.loading}</div>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2">
                {events.map((event) => (
                  <Card key={event.code} className="p-5 flex flex-col justify-between">
                    <div>
                      <div className="flex items-center justify-between gap-2 mb-2">
                        <span className="rounded-full bg-primary/10 border border-primary/20 px-2.5 py-0.5 text-xs font-mono font-semibold text-primary">
                          📅 {event.date}
                        </span>
                        <span className="text-xs text-muted font-mono">{event.timeRange}</span>
                      </div>
                      <h3 className="text-lg font-bold font-spectral text-fg">{event.name}</h3>
                      <p className="text-xs text-muted mt-2 leading-relaxed">{event.description}</p>
                      {event.highlights ? (
                        <div className="mt-3 rounded-[8px] bg-surface-2 p-2.5 text-xs text-muted">
                          <span className="font-semibold text-fg">{isHindi ? "विशेष आकर्षण" : "Highlights"}:</span> {event.highlights}
                        </div>
                      ) : null}
                    </div>

                    <div className="mt-4 pt-3 border-t border-line flex items-center justify-between">
                      <span className="text-xs text-emerald-700 font-semibold">
                        ● {isHindi ? "पंजीकरण चालू" : "E-Tokens Open"}
                      </span>
                      <Button
                        onClick={() => {
                          setPassEventModal(event);
                          setPassVisitDate(event.date);
                          setPassError(null);
                        }}
                        className="text-xs px-3 py-1.5"
                      >
                        {t.portal.bookPass}
                      </Button>
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 5: SEVAK HUB */}
        {/* ========================================================================= */}
        {activeTab === "volunteer" && (
          <div className="mx-auto max-w-2xl">
            <Card className="p-6 sm:p-8">
              {sevakSuccess ? (
                <div className="text-center py-6 flex flex-col items-center gap-4">
                  <div className="size-14 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center text-2xl font-bold">
                    ✓
                  </div>
                  <div>
                    <h3 className="text-xl font-bold font-spectral text-fg">
                      {isHindi ? "जय श्री राम! सेवादार पंजीकरण सफल" : "Dhanyavad! Registered as Sevak"}
                    </h3>
                    <p className="text-xs sm:text-sm text-muted mt-2 max-w-md mx-auto">
                      {sevakSuccess.message}
                    </p>
                  </div>
                  <div className="rounded-[12px] bg-surface-2 border border-line p-4 w-full text-left text-xs space-y-1">
                    <div><span className="font-semibold text-muted">{isHindi ? "सेवादार" : "Sevak"}:</span> {sevakSuccess.fullName}</div>
                    <div><span className="font-semibold text-muted">{t.portal.sevaAreaLabel}:</span> {sevakSuccess.sevaArea}</div>
                    <div><span className="font-semibold text-muted">{t.portal.availableDaysLabel}:</span> {sevakSuccess.availableDays}</div>
                  </div>
                  <Button variant="secondary" onClick={() => setSevakSuccess(null)} className="text-xs">
                    {isHindi ? "नया पंजीकरण करें" : "Register Another Sevak"}
                  </Button>
                </div>
              ) : (
                <form onSubmit={handleVolunteerSubmit} className="flex flex-col gap-5">
                  <div>
                    <h3 className="text-lg font-bold font-spectral">{t.portal.volunteerHeading}</h3>
                    <p className="text-xs text-muted mt-1">{t.portal.volunteerSubtitle}</p>
                  </div>

                  {sevakError ? <Alert tone="danger">{sevakError}</Alert> : null}

                  <div className="grid gap-4 sm:grid-cols-2">
                    <TextField
                      label={t.portal.donorName}
                      required
                      value={sevakName}
                      onChange={(e) => setSevakName(e.target.value)}
                      placeholder={isHindi ? "अपना पूरा नाम" : "Your full name"}
                    />
                    <TextField
                      label={t.portal.phoneOrEmail}
                      required
                      value={sevakContact}
                      onChange={(e) => setSevakContact(e.target.value)}
                      placeholder={isHindi ? "मोबाइल या ईमेल" : "+91 98765 43210"}
                    />
                  </div>

                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium">{t.portal.sevaAreaLabel}</label>
                    <select
                      value={sevaArea}
                      onChange={(e) => setSevaArea(e.target.value)}
                      className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                    >
                      <option value="Mahaprasad & Annadanam Bhandara">
                        {isHindi ? "महाप्रसाद एवं अन्नदान भण्डारा सेवा" : "Mahaprasad & Annadanam Bhandara"}
                      </option>
                      <option value="Darshan Queue & Crowd Management">
                        {isHindi ? "दर्शन पंक्ति एवं भीड़ व्यवस्था सेवा" : "Darshan Queue & Crowd Management"}
                      </option>
                      <option value="Mandir Floral & Campus Decoration">
                        {isHindi ? "मंदिर एवं पुष्प सज्जा सेवा" : "Mandir Floral & Campus Decoration"}
                      </option>
                      <option value="Footwear Stand & Cleanliness Seva">
                        {isHindi ? "जूता-चप्पल स्टैंड एवं स्वच्छता सेवा" : "Footwear Stand & Cleanliness Seva"}
                      </option>
                      <option value="Jal Seva & Pilgrim Assistance">
                        {isHindi ? "जल सेवा एवं तीर्थयात्री सहायता" : "Jal Seva & Pilgrim Assistance"}
                      </option>
                    </select>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-1">
                      <label className="text-sm font-medium">{t.portal.availableDaysLabel}</label>
                      <select
                        value={availableDays}
                        onChange={(e) => setAvailableDays(e.target.value)}
                        className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                      >
                        <option value="Weekends">{isHindi ? "शनिवार एवं रविवार" : "Weekends"}</option>
                        <option value="Sundays Only">{isHindi ? "केवल रविवार" : "Sundays Only"}</option>
                        <option value="Major Festivals Only">{isHindi ? "केवल प्रमुख पर्व व उत्सव" : "Major Festivals Only"}</option>
                        <option value="Daily Morning">{isHindi ? "प्रतिदिन प्रातः काल" : "Daily Morning"}</option>
                        <option value="Daily Evening">{isHindi ? "प्रतिदिन सायं काल" : "Daily Evening"}</option>
                        <option value="Flexible">{isHindi ? "यथावश्यक / जब भी आवश्यकता हो" : "Flexible / On Call"}</option>
                      </select>
                    </div>

                    <div className="flex flex-col gap-1">
                      <label className="text-sm font-medium">{t.portal.shiftPreferenceLabel}</label>
                      <select
                        value={shiftPreference}
                        onChange={(e) => setShiftPreference(e.target.value)}
                        className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                      >
                        <option value="Morning">{isHindi ? "प्रातः काल (06:00 AM - 12:00 PM)" : "Morning (6 AM - 12 PM)"}</option>
                        <option value="Evening">{isHindi ? "सायं काल (04:00 PM - 09:30 PM)" : "Evening (4 PM - 9:30 PM)"}</option>
                        <option value="Full Day">{isHindi ? "पूरा दिन (विशेष उत्सव पर)" : "Full Day (Festivals)"}</option>
                      </select>
                    </div>
                  </div>

                  <TextField
                    label={isHindi ? "विशेष कौशल अथवा अनुभव (वैकल्पिक)" : "Special Skills or Experience (Optional)"}
                    value={sevakNotes}
                    onChange={(e) => setSevakNotes(e.target.value)}
                    placeholder={isHindi ? "उदा. भोजन निर्माण, प्राथमिक चिकित्सा, संस्कृत श्लोक पाठ" : "e.g. Cooking, First Aid, Sound System"}
                  />

                  <Button type="submit" busy={sevakBusy} className="py-2.5 text-sm font-semibold">
                    {t.portal.submitVolunteer}
                  </Button>
                </form>
              )}
            </Card>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 6: MY MANDIR (DEVOTEE DASHBOARD) */}
        {/* ========================================================================= */}
        {activeTab === "my-mandir" && (
          <div className="flex flex-col gap-6">
            {!me ? (
              <Card className="p-8 text-center flex flex-col items-center gap-4 max-w-md mx-auto">
                <div className="size-14 rounded-full bg-primary/10 flex items-center justify-center text-primary text-2xl">
                  🕉️
                </div>
                <div>
                  <h3 className="text-lg font-bold font-spectral">{t.portal.signInDevotee}</h3>
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
            ) : (
              <div className="flex flex-col gap-6">
                {/* Devotee Profile Header Card */}
                <Card className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <div className="size-12 rounded-full bg-primary/10 text-primary flex items-center justify-center text-xl font-bold font-spectral">
                      {me.fullName.charAt(0)}
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <h3 className="text-base font-bold text-fg">{me.fullName}</h3>
                        <span className="rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 px-2 py-0.5 text-[10px] font-semibold">
                          ✓ {isHindi ? "सत्यापित भक्त" : "Verified Devotee"}
                        </span>
                      </div>
                      <p className="text-xs text-muted">
                        {me.phone || me.email || ""} {me.city ? `· ${me.city}` : ""}
                      </p>
                    </div>
                  </div>

                  {/* Sub-tab Switcher */}
                  <div className="inline-flex rounded-[10px] bg-surface-2 p-1 border border-line">
                    <button
                      type="button"
                      onClick={() => setMyMandirSubTab("donations")}
                      className={`rounded-[8px] px-3 py-1 text-xs font-semibold transition-all ${
                        myMandirSubTab === "donations"
                          ? "bg-surface text-primary shadow-xs"
                          : "text-muted hover:text-fg"
                      }`}
                    >
                      🪔 {t.portal.historyTab} ({myDonations.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setMyMandirSubTab("pujas")}
                      className={`rounded-[8px] px-3 py-1 text-xs font-semibold transition-all ${
                        myMandirSubTab === "pujas"
                          ? "bg-surface text-primary shadow-xs"
                          : "text-muted hover:text-fg"
                      }`}
                    >
                      🕉️ {t.portal.myPujaBookings} ({myPujas.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setMyMandirSubTab("passes")}
                      className={`rounded-[8px] px-3 py-1 text-xs font-semibold transition-all ${
                        myMandirSubTab === "passes"
                          ? "bg-surface text-primary shadow-xs"
                          : "text-muted hover:text-fg"
                      }`}
                    >
                      🎫 {t.portal.myPasses} ({myPasses.length})
                    </button>
                  </div>
                </Card>

                {/* SubTab 1: Donations */}
                {myMandirSubTab === "donations" && (
                  <div>
                    {myDonations.length === 0 ? (
                      <Card className="p-8 text-center text-muted text-sm">
                        {t.portal.noDonationsYet}
                      </Card>
                    ) : (
                      <div className="grid gap-3">
                        {myDonations.map((item) => (
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

                {/* SubTab 2: Pujas */}
                {myMandirSubTab === "pujas" && (
                  <div>
                    {myPujas.length === 0 ? (
                      <Card className="p-8 text-center text-muted text-sm">
                        {isHindi ? "आपने अभी तक कोई पूजा बुक नहीं की है।" : "No booked pujas found on this account."}
                      </Card>
                    ) : (
                      <div className="grid gap-3">
                        {myPujas.map((item) => (
                          <Card key={item.id} className="p-4 flex items-center justify-between gap-4">
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="font-bold text-sm text-fg">{item.pujaName}</span>
                                <span className="rounded-full bg-emerald-100 text-emerald-800 px-2 py-0.5 text-[10px] font-semibold">
                                  {item.status}
                                </span>
                              </div>
                              <p className="text-xs text-muted mt-1">
                                📅 {item.pujaDate} · {item.timeSlot}
                              </p>
                              {item.gotra ? (
                                <p className="text-[11px] text-muted/80">
                                  {isHindi ? "गोत्र" : "Gotra"}: {item.gotra}
                                  {item.familyMembers ? ` · ${item.familyMembers}` : ""}
                                </p>
                              ) : null}
                            </div>
                            <Button
                              variant="secondary"
                              className="text-xs px-3 py-1.5"
                              onClick={() => setConfirmedPujaBooking(item)}
                            >
                              {isHindi ? "संकल्प पत्र देखें" : "View Sankalp Slip"}
                            </Button>
                          </Card>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* SubTab 3: Darshan Passes */}
                {myMandirSubTab === "passes" && (
                  <div>
                    {myPasses.length === 0 ? (
                      <Card className="p-8 text-center text-muted text-sm">
                        {isHindi ? "आपके पास कोई सक्रिय दर्शन पास नहीं है।" : "No active darshan passes found."}
                      </Card>
                    ) : (
                      <div className="grid gap-3">
                        {myPasses.map((pass) => (
                          <Card key={pass.id} className="p-4 flex items-center justify-between gap-4">
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="font-bold text-sm text-fg">{pass.eventName}</span>
                                <span className="font-mono text-xs text-primary font-semibold">
                                  {pass.passNumber}
                                </span>
                              </div>
                              <p className="text-xs text-muted mt-1">
                                📅 {pass.visitDate} · {pass.timeSlot}
                              </p>
                              <p className="text-[11px] text-muted/80">
                                {isHindi ? "भक्त संख्या" : "Attendees"}: {pass.attendeeCount}
                              </p>
                            </div>
                            <Button
                              variant="secondary"
                              className="text-xs px-3 py-1.5"
                              onClick={() => setConfirmedPass(pass)}
                            >
                              {isHindi ? "QR पास देखें" : "View E-Token QR"}
                            </Button>
                          </Card>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </main>

      {/* ========================================================================= */}
      {/* MODALS */}
      {/* ========================================================================= */}

      {/* 1. Instant Donation Receipt Modal */}
      {activeReceipt ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md bg-surface border border-line rounded-[16px] shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
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

      {/* 2. Book Puja & Sankalp Modal */}
      {bookingPujaModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg bg-surface border border-line rounded-[16px] shadow-xl p-6 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-150 max-h-[90dvh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-lg font-bold font-spectral">{t.portal.bookPuja}</h4>
                <p className="text-xs text-muted">{bookingPujaModal.name} · ₹{bookingPujaModal.dakshinaRupees}</p>
              </div>
              <button
                type="button"
                onClick={() => setBookingPujaModal(null)}
                className="text-muted hover:text-fg text-sm"
              >
                ✕
              </button>
            </div>

            {pujaError ? <Alert tone="danger">{pujaError}</Alert> : null}

            <form onSubmit={handleBookPuja} className="flex flex-col gap-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <TextField
                  label={t.portal.donorName}
                  required
                  value={donorName}
                  onChange={(e) => setDonorName(e.target.value)}
                  placeholder={isHindi ? "यजमान का नाम" : "Yajman / Devotee Name"}
                />
                <TextField
                  label={t.portal.phoneOrEmail}
                  value={contact}
                  onChange={(e) => setContact(e.target.value)}
                  placeholder="+91 98765 43210"
                />
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <TextField
                  label={t.portal.pujaDateLabel}
                  type="date"
                  required
                  value={pujaDate}
                  onChange={(e) => setPujaDate(e.target.value)}
                />
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium">{t.portal.timeSlotLabel}</label>
                  <select
                    value={pujaSlot}
                    onChange={(e) => setPujaSlot(e.target.value)}
                    className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                  >
                    <option value="07:00 AM - 08:30 AM">07:00 AM - 08:30 AM (प्रातः)</option>
                    <option value="09:00 AM - 10:30 AM">09:00 AM - 10:30 AM (पूर्वाह्न)</option>
                    <option value="11:00 AM - 12:30 PM">11:00 AM - 12:30 PM (मध्याह्न)</option>
                    <option value="05:00 PM - 06:30 PM">05:00 PM - 06:30 PM (सायं)</option>
                    <option value="07:00 PM - 08:30 PM">07:00 PM - 08:30 PM (संध्या)</option>
                  </select>
                </div>
              </div>

              {/* Sacred Sankalpam Fields */}
              <div className="rounded-[12px] bg-primary/5 border border-primary/20 p-4 space-y-3">
                <h5 className="text-xs font-bold text-primary-strong uppercase tracking-wider flex items-center gap-1.5">
                  <span>🕉️</span> {t.portal.sankalpForm}
                </h5>

                <div className="grid gap-3 sm:grid-cols-3">
                  <TextField
                    label={t.portal.gotraLabel}
                    value={gotra}
                    onChange={(e) => setGotra(e.target.value)}
                    placeholder={isHindi ? "उदा. कश्यप" : "e.g. Kashyap"}
                  />
                  <TextField
                    label={t.portal.nakshatraLabel}
                    value={nakshatra}
                    onChange={(e) => setNakshatra(e.target.value)}
                    placeholder={isHindi ? "उदा. पुष्य" : "e.g. Pushya"}
                  />
                  <TextField
                    label={t.portal.rashiLabel}
                    value={rashi}
                    onChange={(e) => setRashi(e.target.value)}
                    placeholder={isHindi ? "उदा. मेष" : "e.g. Mesha"}
                  />
                </div>

                <TextField
                  label={t.portal.familyMembersLabel}
                  value={familyMembers}
                  onChange={(e) => setFamilyMembers(e.target.value)}
                  placeholder={isHindi ? "सपरिवार नाम (उदा. रमेश शर्मा, सुनीता शर्मा, राहुल शर्मा)" : "Names of family members for Sankalpam"}
                />
              </div>

              <div className="rounded-[8px] bg-surface-2 p-2.5 text-xs text-muted flex justify-between items-center">
                <span>{t.portal.dakshinaLabel}:</span>
                <span className="font-mono font-bold text-base text-primary-strong">
                  ₹{bookingPujaModal.dakshinaRupees.toLocaleString("en-IN")}
                </span>
              </div>

              <Button type="submit" busy={pujaBusy} className="py-2.5 text-sm font-semibold">
                {t.portal.confirmBooking}
              </Button>
            </form>
          </div>
        </div>
      ) : null}

      {/* 3. Sacred Sankalp Confirmation Slip Modal */}
      {confirmedPujaBooking ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md bg-surface border border-line rounded-[16px] shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div id="printable-sankalp" className="p-6 flex flex-col gap-3 border-b border-dashed border-line">
              <div className="text-center pb-2 border-b border-line/60">
                <Diya className="size-10 mx-auto text-primary mb-1" />
                <h4 className="text-lg font-bold font-spectral">{confirmedPujaBooking.mandirName}</h4>
                <p className="text-xs text-primary font-semibold">
                  {isHindi ? "पवित्र पूजा संकल्प पत्र" : "Sacred Sankalpam Slip"}
                </p>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{isHindi ? "बुकिंग संख्या" : "Booking Number"}:</span>
                <span className="font-mono font-bold text-fg">{confirmedPujaBooking.bookingNumber}</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{isHindi ? "पूजा" : "Puja"}:</span>
                <span className="font-semibold text-fg">{confirmedPujaBooking.pujaName}</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{isHindi ? "यजमान" : "Yajman"}:</span>
                <span className="font-semibold text-fg">{confirmedPujaBooking.devoteeName}</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{t.portal.gotraLabel}:</span>
                <span className="font-mono text-fg">{confirmedPujaBooking.gotra || "—"}</span>
              </div>

              {confirmedPujaBooking.familyMembers ? (
                <div className="flex justify-between items-center text-xs">
                  <span className="text-muted">{isHindi ? "संकल्प सदस्य" : "Family"}:</span>
                  <span className="text-fg text-right">{confirmedPujaBooking.familyMembers}</span>
                </div>
              ) : null}

              <div className="flex justify-between items-center text-xs">
                <span className="text-muted">{isHindi ? "तिथि एवं समय" : "Date & Time"}:</span>
                <span className="font-mono text-fg">{confirmedPujaBooking.pujaDate} ({confirmedPujaBooking.timeSlot})</span>
              </div>

              <div className="rounded-[10px] bg-primary/10 border border-primary/20 p-3 flex justify-between items-center my-1">
                <span className="text-xs font-semibold text-muted">{isHindi ? "दक्षिणा" : "Dakshina"}:</span>
                <span className="text-lg font-bold font-mono text-primary-strong">
                  ₹{confirmedPujaBooking.amountRupees.toLocaleString("en-IN")}
                </span>
              </div>

              <p className="text-center text-[11px] text-muted italic">
                {isHindi
                  ? "मंदिर के आचार्यों द्वारा आपके परिवार के कल्याण, स्वास्थ्य एवं समृद्धि हेतु विधिवत संकल्प किया जाएगा।"
                  : "Priests will recite your family’s Gotra and names during the auspicious Vedic Sankalpam."}
              </p>
            </div>

            <div className="p-4 bg-surface-2 flex items-center justify-end gap-2">
              <Button variant="secondary" onClick={() => window.print()} className="text-xs">
                {isHindi ? "संकल्प पत्र प्रिंट करें" : "Print Sankalp Slip"}
              </Button>
              <Button onClick={() => setConfirmedPujaBooking(null)} className="text-xs">
                {t.portal.closeReceipt}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {/* 4. Book Darshan E-Token Modal */}
      {passEventModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md bg-surface border border-line rounded-[16px] shadow-xl p-6 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-lg font-bold font-spectral">{t.portal.bookPass}</h4>
                <p className="text-xs text-muted">{passEventModal.name} · {passEventModal.date}</p>
              </div>
              <button
                type="button"
                onClick={() => setPassEventModal(null)}
                className="text-muted hover:text-fg text-sm"
              >
                ✕
              </button>
            </div>

            {passError ? <Alert tone="danger">{passError}</Alert> : null}

            <form onSubmit={handleBookPass} className="flex flex-col gap-4">
              <TextField
                label={t.portal.donorName}
                required
                value={donorName}
                onChange={(e) => setDonorName(e.target.value)}
                placeholder={isHindi ? "प्रमुख भक्त का नाम" : "Primary Devotee Name"}
              />

              <TextField
                label={t.portal.phoneOrEmail}
                required
                value={passContact}
                onChange={(e) => setPassContact(e.target.value)}
                placeholder="+91 98765 43210"
              />

              <div className="grid gap-3 sm:grid-cols-2">
                <TextField
                  label={isHindi ? "दर्शन तिथि" : "Visit Date"}
                  type="date"
                  required
                  value={passVisitDate}
                  onChange={(e) => setPassVisitDate(e.target.value)}
                />
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium">{t.portal.attendeeCountLabel}</label>
                  <select
                    value={attendeeCount}
                    onChange={(e) => setAttendeeCount(Number(e.target.value))}
                    className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                  >
                    {[1, 2, 3, 4, 5, 6, 8, 10].map((num) => (
                      <option key={num} value={num}>
                        {num} {isHindi ? "भक्त" : "Devotees"}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium">{t.portal.timeSlotLabel}</label>
                <select
                  value={passSlot}
                  onChange={(e) => setPassSlot(e.target.value)}
                  className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                >
                  <option value="06:00 AM - 08:00 AM">06:00 AM - 08:00 AM (प्रातः)</option>
                  <option value="08:00 AM - 10:00 AM">08:00 AM - 10:00 AM (पूर्वाह्न)</option>
                  <option value="04:00 PM - 06:00 PM">04:00 PM - 06:00 PM (सायं)</option>
                  <option value="06:00 PM - 08:00 PM">06:00 PM - 08:00 PM (संध्या)</option>
                </select>
              </div>

              <div className="rounded-[8px] bg-primary/10 p-2.5 text-xs text-primary-strong">
                {t.portal.eTokenNotice}
              </div>

              <Button type="submit" busy={passBusy} className="py-2.5 text-sm font-semibold">
                {t.portal.bookPass}
              </Button>
            </form>
          </div>
        </div>
      ) : null}

      {/* 5. Darshan E-Token Pass Modal (with QR Code) */}
      {confirmedPass ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-sm bg-surface border border-line rounded-[16px] shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div id="printable-pass" className="p-6 flex flex-col gap-3 text-center border-b border-dashed border-line">
              <Diya className="size-8 mx-auto text-primary" />
              <h4 className="text-base font-bold font-spectral">{confirmedPass.mandirName}</h4>
              <span className="rounded-full bg-primary/10 border border-primary/20 px-3 py-0.5 text-xs font-semibold text-primary mx-auto">
                {confirmedPass.eventName}
              </span>

              {/* QR Code */}
              <div className="my-2">
                <QrCodeSvg value={confirmedPass.qrString} size={150} />
              </div>

              <div className="font-mono text-sm font-bold text-fg tracking-wider">
                {confirmedPass.passNumber}
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs border-t border-line/60 pt-3 text-left">
                <div>
                  <span className="text-muted block">{isHindi ? "प्रमुख भक्त" : "Devotee"}:</span>
                  <span className="font-semibold text-fg">{confirmedPass.primaryDevoteeName}</span>
                </div>
                <div>
                  <span className="text-muted block">{isHindi ? "कुल संख्या" : "Count"}:</span>
                  <span className="font-semibold text-fg">{confirmedPass.attendeeCount} {isHindi ? "भक्त" : "Persons"}</span>
                </div>
                <div>
                  <span className="text-muted block">{isHindi ? "दर्शन तिथि" : "Date"}:</span>
                  <span className="font-semibold text-fg">{confirmedPass.visitDate}</span>
                </div>
                <div>
                  <span className="text-muted block">{isHindi ? "समय स्लॉट" : "Slot"}:</span>
                  <span className="font-semibold text-fg">{confirmedPass.timeSlot}</span>
                </div>
              </div>

              <p className="text-[11px] text-muted mt-1 leading-tight">
                {t.portal.eTokenNotice}
              </p>
            </div>

            <div className="p-4 bg-surface-2 flex items-center justify-end gap-2">
              <Button variant="secondary" onClick={() => window.print()} className="text-xs">
                {isHindi ? "पास प्रिंट करें" : "Print Pass"}
              </Button>
              <Button onClick={() => setConfirmedPass(null)} className="text-xs">
                {t.portal.closeReceipt}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {/* 6. Devotee Auth Modal (Phone / Email OTP) */}
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
        <div className="mx-auto max-w-5xl px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Diya className="size-4 text-primary" />
            <span className="font-semibold">{mandirTitle}</span>
            <span>·</span>
            <span className="font-mono text-[11px]">Mandir Center · Powered by SevaCenter</span>
          </div>
          <div className="flex items-center gap-3">
            <Link href="/login" className="hover:text-fg font-medium">
              {isHindi ? "मंदिर ट्रस्टी / स्टाफ़ लॉगिन →" : "Trustee & Staff Login →"}
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
