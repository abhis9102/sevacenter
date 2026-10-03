"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import type { AartiTiming, MandirScheduleDto } from "@/lib/types";

export default function AdminMandirCenterPage() {
  const { lang, t } = useLanguage();
  const isHindi = lang === "hi";

  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<boolean>(false);

  // Form fields
  const [mandirName, setMandirName] = useState<string>("");
  const [morningHours, setMorningHours] = useState<string>("05:00 AM – 01:00 PM");
  const [eveningHours, setEveningHours] = useState<string>("04:00 PM – 09:30 PM");
  const [isOpenOverride, setIsOpenOverride] = useState<string>("AUTO");
  const [deity, setDeity] = useState<string>("Pradhan Devata");
  const [address, setAddress] = useState<string>("Temple Road, Central Sanctum");
  const [helpline, setHelpline] = useState<string>("+91 98765 43210");
  const [panchangTithi, setPanchangTithi] = useState<string>("Shukla Paksha Ekadashi / Trayodashi");
  const [nakshatra, setNakshatra] = useState<string>("Rohini / Uttara Phalguni");
  const [specialAnnouncement, setSpecialAnnouncement] = useState<string>("");
  const [aartis, setAartis] = useState<AartiTiming[]>([
    { name: "Mangala Aarti", time: "05:30 AM", description: "Morning awakening prayer and first sacred darshan" },
    { name: "Shringar Darshan", time: "07:30 AM", description: "Adorning the deity with fresh flowers and sacred vastram" },
    { name: "Rajbhog Aarti", time: "12:00 PM", description: "Noon sacred bhog offering followed by afternoon temple rest" },
    { name: "Sandhya Aarti", time: "07:00 PM", description: "Evening deepam aarti with holy chantings and bhajans" },
    { name: "Shayan Aarti", time: "09:00 PM", description: "Night closing prayer and bedtime lullaby for the deity" },
  ]);

  useEffect(() => {
    let mounted = true;
    void (async () => {
      try {
        const s = await api.get<MandirScheduleDto>("/mandir/settings");
        if (mounted) {
          setMandirName(s.mandirName);
          setMorningHours(s.morningHours);
          setEveningHours(s.eveningHours);
          setIsOpenOverride(s.isOpenOverride === null ? "AUTO" : s.isOpenOverride ? "OPEN" : "CLOSED");
          if (s.deity) setDeity(s.deity);
          if (s.address) setAddress(s.address);
          if (s.helpline) setHelpline(s.helpline);
          if (s.panchangTithi) setPanchangTithi(s.panchangTithi);
          if (s.nakshatra) setNakshatra(s.nakshatra);
          setSpecialAnnouncement(s.specialAnnouncement || "");
          if (s.aartis && s.aartis.length > 0) setAartis(s.aartis);
        }
      } catch (err) {
        if (mounted) setError(describeError(err, t.errors));
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, [t.errors]);

  async function handleSaveSettings(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    setSaving(true);

    const overrideVal = isOpenOverride === "AUTO" ? null : isOpenOverride === "OPEN";

    try {
      const updated = await api.request<MandirScheduleDto>("/mandir/settings", {
        method: "PUT",
        json: {
          morningHours: morningHours.trim(),
          eveningHours: eveningHours.trim(),
          isOpenOverride: overrideVal,
          deity: deity.trim(),
          address: address.trim(),
          helpline: helpline.trim(),
          panchangTithi: panchangTithi.trim(),
          nakshatra: nakshatra.trim(),
          specialAnnouncement: specialAnnouncement.trim() || null,
          aartis,
        },
      });
      setMandirName(updated.mandirName);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 4000);
    } catch (err) {
      setError(describeError(err, t.errors));
    } finally {
      setSaving(false);
    }
  }

  function handleAartiChange(index: number, field: keyof AartiTiming, value: string) {
    setAartis((prev) => {
      const next = [...prev];
      const item = next[index];
      if (item) {
        next[index] = { ...item, [field]: value };
      }
      return next;
    });
  }

  return (
    <div className="flex flex-col gap-6 max-w-4xl">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl sm:text-2xl font-bold font-spectral tracking-tight">
              {isHindi ? "मंदिर सेंटर (सार्वजनिक पोर्टल) सेटिंग्स" : "Mandir Center Configuration"}
            </h1>
            <span className="rounded-full bg-primary/10 border border-primary/20 px-2.5 py-0.5 text-xs font-semibold text-primary">
              Church Center Publisher
            </span>
          </div>
          <p className="text-xs sm:text-sm text-muted mt-1">
            {isHindi
              ? `${mandirName ? mandirName + " के लिए " : ""}दैनिक दर्शन समय, आज का पंचांग, दैनिक आरतियां एवं विशेष घोषणाएं प्रबंधित करें जो भक्तों को मंदिर पोर्टल पर दिखेंगी।`
              : `Manage Darshan hours, sacred Panchang, 5 Aartis schedule, and active announcements displayed on Mandir Center${mandirName ? ` for ${mandirName}` : ""}.`}
          </p>
        </div>

        <Link
          href="/portal"
          target="_blank"
          className="inline-flex items-center gap-1.5 rounded-[10px] border border-primary/30 bg-primary/10 px-3.5 py-2 text-xs font-semibold text-primary-strong hover:bg-primary/20 transition-all shrink-0"
        >
          <span>👁️</span>
          <span>{isHindi ? "पब्लिक मंदिर सेंटर देखें →" : "Preview Mandir Center →"}</span>
        </Link>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {success ? (
        <Alert tone="success">
          ✓ {isHindi ? "मंदिर सेटिंग्स सफलतापूर्वक सुरक्षित की गईं!" : "Mandir Center settings saved successfully!"}
        </Alert>
      ) : null}

      {loading ? (
        <Card className="p-12 text-center text-muted text-sm">
          {t.common.loading}
        </Card>
      ) : (
        <form onSubmit={handleSaveSettings} className="flex flex-col gap-6">
        {/* 1. Darshan Timings & Live Status */}
        <Card className="p-6">
          <h3 className="text-base font-bold font-spectral text-fg mb-1">
            ⏰ {isHindi ? "दर्शन समय एवं मंदिर लाइव स्थिति" : "Darshan Timings & Live Status"}
          </h3>
          <p className="text-xs text-muted mb-4">
            {isHindi ? "भक्तों के दर्शन हेतु प्रातः एवं सायं काल की समय सारिणी:" : "Morning & evening opening hours shown to devotees:"}
          </p>

          <div className="grid gap-4 sm:grid-cols-3">
            <TextField
              label={isHindi ? "प्रातः दर्शन समय" : "Morning Hours"}
              required
              value={morningHours}
              onChange={(e) => setMorningHours(e.target.value)}
              placeholder="05:00 AM – 01:00 PM"
            />
            <TextField
              label={isHindi ? "सायं दर्शन समय" : "Evening Hours"}
              required
              value={eveningHours}
              onChange={(e) => setEveningHours(e.target.value)}
              placeholder="04:00 PM – 09:30 PM"
            />
            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium">{isHindi ? "लाइव स्थिति ओवरराइड" : "Live Status Override"}</label>
              <select
                value={isOpenOverride}
                onChange={(e) => setIsOpenOverride(e.target.value)}
                className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
              >
                <option value="AUTO">{isHindi ? "स्वचालित (Auto · समय अनुसार)" : "Auto (Based on hours)"}</option>
                <option value="OPEN">{isHindi ? "खुला रखें (Force Open)" : "Force Open 🟢"}</option>
                <option value="CLOSED">{isHindi ? "विश्राम / बंद (Force Closed)" : "Force Closed 🌙"}</option>
              </select>
            </div>
          </div>
        </Card>

        {/* 2. Active Announcement Banner */}
        <Card className="p-6">
          <h3 className="text-base font-bold font-spectral text-fg mb-1">
            📢 {isHindi ? "सक्रिय मंदिर सूचना / घोषणा बैनर" : "Active Mandir Announcement Banner"}
          </h3>
          <p className="text-xs text-muted mb-4">
            {isHindi
              ? "यह सूचना मंदिर सेंटर के होम पेज पर शीर्ष पर विशेष बैनर के रूप में दिखेगी:"
              : "This announcement banner is highlighted prominently at the top of Mandir Center:"}
          </p>

          <TextField
            label={isHindi ? "घोषणा संदेश (खाली छोड़ने पर बैनर छुप जाएगा)" : "Announcement Message (Leave blank to hide)"}
            value={specialAnnouncement}
            onChange={(e) => setSpecialAnnouncement(e.target.value)}
            placeholder={isHindi ? "उदा. आगामी सोमवार को विशेष रुद्राभिषेक एवं महाप्रसाद वितरण..." : "e.g. Special Darshan arrangements for upcoming festival..."}
          />
        </Card>

        {/* 3. Sacred Panchang & Mandir Details */}
        <Card className="p-6">
          <h3 className="text-base font-bold font-spectral text-fg mb-1">
            🪔 {isHindi ? "दैनिक पंचांग एवं मंदिर परिचय" : "Daily Sacred Panchang & Temple Details"}
          </h3>
          <p className="text-xs text-muted mb-4">
            {isHindi ? "आज की पावन तिथि, नक्षत्र एवं मंदिर का संपर्क विवरण:" : "Today's sacred tithi, nakshatra, and contact information:"}
          </p>

          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label={isHindi ? "आज की तिथि" : "Today's Tithi"}
              required
              value={panchangTithi}
              onChange={(e) => setPanchangTithi(e.target.value)}
              placeholder="Shukla Paksha Ekadashi"
            />
            <TextField
              label={isHindi ? "नक्षत्र" : "Nakshatra"}
              required
              value={nakshatra}
              onChange={(e) => setNakshatra(e.target.value)}
              placeholder="Pushya Nakshatra"
            />
            <TextField
              label={isHindi ? "आराध्य देवता" : "Presiding Deity"}
              value={deity}
              onChange={(e) => setDeity(e.target.value)}
              placeholder="Pradhan Devata"
            />
            <TextField
              label={isHindi ? "मंदिर हेल्पलाइन / पुजारी संपर्क" : "Helpline / Priest Phone"}
              value={helpline}
              onChange={(e) => setHelpline(e.target.value)}
              placeholder="+91 98765 43210"
            />
            <div className="sm:col-span-2">
              <TextField
                label={isHindi ? "मंदिर का पता / स्थान" : "Temple Campus Address"}
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="Temple Road, Central Sanctum"
              />
            </div>
          </div>
        </Card>

        {/* 4. Daily 5 Aartis Timetable */}
        <Card className="p-6">
          <h3 className="text-base font-bold font-spectral text-fg mb-1">
            🕯️ {isHindi ? "दैनिक 5 आरतियां समय सारिणी" : "Daily 5 Aartis Timetable"}
          </h3>
          <p className="text-xs text-muted mb-4">
            {isHindi ? "मंदिर में होने वाली नित्य आरतियों के नाम, समय एवं आध्यात्मिक विवरण:" : "Configure the names, times, and descriptions of daily aartis:"}
          </p>

          <div className="flex flex-col gap-4">
            {aartis.map((aarti, idx) => (
              <div key={idx} className="p-4 rounded-[12px] bg-surface-2 border border-line grid gap-3 sm:grid-cols-3">
                <TextField
                  label={`${isHindi ? "आरती" : "Aarti"} #${idx + 1}`}
                  value={aarti.name}
                  onChange={(e) => handleAartiChange(idx, "name", e.target.value)}
                  placeholder="Aarti Name"
                />
                <TextField
                  label={isHindi ? "समय" : "Time"}
                  value={aarti.time}
                  onChange={(e) => handleAartiChange(idx, "time", e.target.value)}
                  placeholder="07:00 PM"
                />
                <TextField
                  label={isHindi ? "विवरण / भावार्थ" : "Description"}
                  value={aarti.description}
                  onChange={(e) => handleAartiChange(idx, "description", e.target.value)}
                  placeholder="Description..."
                />
              </div>
            ))}
          </div>
        </Card>

        {/* Save button */}
        <div className="flex justify-end gap-3">
          <Button type="submit" busy={saving} className="px-6 py-2.5 text-sm font-semibold">
            {t.common.save}
          </Button>
        </div>
      </form>
      )}
    </div>
  );
}
