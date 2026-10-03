"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import type { DarshanPassAdminItem, MandirEventAdminItem } from "@/lib/types";

export default function AdminEventsPage() {
  const { lang, t } = useLanguage();
  const isHindi = lang === "hi";

  const [activeTab, setActiveTab] = useState<"checkin" | "events">("checkin");

  // Gate Check-in state
  const [tokenInput, setTokenInput] = useState<string>("");
  const [checkingIn, setCheckingIn] = useState<boolean>(false);
  const [checkInError, setCheckInError] = useState<string | null>(null);
  const [recentCheckedIn, setRecentCheckedIn] = useState<DarshanPassAdminItem | null>(null);

  // Events list state
  const [events, setEvents] = useState<MandirEventAdminItem[]>([]);
  const [selectedEventCode, setSelectedEventCode] = useState<string>("");
  const [eventPasses, setEventPasses] = useState<DarshanPassAdminItem[] | null>(null);

  // Add/Edit Event modal state
  const [showEventModal, setShowEventModal] = useState<boolean>(false);
  const [editingEvent, setEditingEvent] = useState<MandirEventAdminItem | null>(null);
  const [eventFormError, setEventFormError] = useState<string | null>(null);
  const [savingEvent, setSavingEvent] = useState<boolean>(false);

  // Event form fields
  const [formCode, setFormCode] = useState<string>("");
  const [formName, setFormName] = useState<string>("");
  const [formDate, setFormDate] = useState<string>("");
  const [formTimeRange, setFormTimeRange] = useState<string>("06:00 PM – 10:00 PM");
  const [formDescription, setFormDescription] = useState<string>("");
  const [formHighlights, setFormHighlights] = useState<string>("");
  const [formRegOpen, setFormRegOpen] = useState<boolean>(true);
  const [formCapacity, setFormCapacity] = useState<string>("1000");
  const [formActive, setFormActive] = useState<boolean>(true);

  // Load events on mount
  useEffect(() => {
    let mounted = true;
    void (async () => {
      try {
        const data = await api.get<MandirEventAdminItem[]>("/mandir/events");
        if (mounted) {
          setEvents(data);
          if (data.length > 0 && !selectedEventCode) {
            setSelectedEventCode(data[0]?.code ?? "");
          }
        }
      } catch {}
    })();
    return () => {
      mounted = false;
    };
  }, [selectedEventCode]);

  // Load passes for selected event
  useEffect(() => {
    if (selectedEventCode) {
      let mounted = true;
      api
        .get<DarshanPassAdminItem[]>(`/mandir/events/${selectedEventCode}/passes`)
        .then((list) => {
          if (mounted) setEventPasses(list);
        })
        .catch(() => {});
      return () => {
        mounted = false;
      };
    }
  }, [selectedEventCode]);

  async function handleCheckIn(e: React.FormEvent) {
    e.preventDefault();
    setCheckInError(null);
    if (!tokenInput.trim()) return;

    setCheckingIn(true);
    try {
      const res = await api.request<DarshanPassAdminItem>("/mandir/passes/check-in", {
        method: "POST",
        json: { tokenOrPassNumber: tokenInput.trim() },
      });
      setRecentCheckedIn(res);
      setTokenInput("");
      // Update in eventPasses if present
      setEventPasses((prev) => (prev ? prev.map((p) => (p.passNumber === res.passNumber ? res : p)) : null));
    } catch (err) {
      setCheckInError(describeError(err, t.errors));
    } finally {
      setCheckingIn(false);
    }
  }

  function openCreateModal() {
    setEditingEvent(null);
    setFormCode("");
    setFormName("");
    setFormDate(new Date(Date.now() + 86400000 * 7).toISOString().split("T")[0] ?? "");
    setFormTimeRange("06:00 PM – 10:00 PM");
    setFormDescription("");
    setFormHighlights("");
    setFormRegOpen(true);
    setFormCapacity("1000");
    setFormActive(true);
    setEventFormError(null);
    setShowEventModal(true);
  }

  function openEditModal(event: MandirEventAdminItem) {
    setEditingEvent(event);
    setFormCode(event.code);
    setFormName(event.name);
    setFormDate(event.eventDate);
    setFormTimeRange(event.timeRange);
    setFormDescription(event.description || "");
    setFormHighlights(event.highlights || "");
    setFormRegOpen(event.registrationOpen);
    setFormCapacity(String(event.maxCapacity || 500));
    setFormActive(event.active);
    setEventFormError(null);
    setShowEventModal(true);
  }

  async function handleSaveEvent(e: React.FormEvent) {
    e.preventDefault();
    setEventFormError(null);
    if (!formName.trim()) {
      setEventFormError(isHindi ? "उत्सव का नाम अनिवार्य है" : "Event name is required");
      return;
    }

    setSavingEvent(true);
    try {
      if (editingEvent) {
        const updated = await api.request<MandirEventAdminItem>(`/mandir/events/${editingEvent.id}`, {
          method: "PUT",
          json: {
            name: formName.trim(),
            eventDate: formDate,
            timeRange: formTimeRange.trim(),
            description: formDescription.trim() || null,
            highlights: formHighlights.trim() || null,
            registrationOpen: formRegOpen,
            maxCapacity: Number(formCapacity) || 500,
            active: formActive,
          },
        });
        setEvents((prev) => prev.map((ev) => (ev.id === updated.id ? updated : ev)));
      } else {
        const code = formCode.trim() || formName.trim().toUpperCase().replace(/[^A-Z0-9]/g, "_");
        const created = await api.request<MandirEventAdminItem>("/mandir/events", {
          method: "POST",
          json: {
            code,
            name: formName.trim(),
            eventDate: formDate,
            timeRange: formTimeRange.trim(),
            description: formDescription.trim() || null,
            highlights: formHighlights.trim() || null,
            registrationOpen: formRegOpen,
            maxCapacity: Number(formCapacity) || 500,
            active: formActive,
          },
        });
        setEvents((prev) => [...prev, created]);
      }
      setShowEventModal(false);
    } catch (err) {
      setEventFormError(describeError(err, t.errors));
    } finally {
      setSavingEvent(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl sm:text-2xl font-bold font-spectral tracking-tight">
              {isHindi ? "उत्सव एवं प्रवेश पास प्रबंधन" : "Utsavs & Gate Pass Check-in"}
            </h1>
            <span className="rounded-full bg-primary/10 border border-primary/20 px-2.5 py-0.5 text-xs font-semibold text-primary">
              Planning Center Registrations
            </span>
          </div>
          <p className="text-xs sm:text-sm text-muted mt-1">
            {isHindi
              ? "मंदिर द्वार पर QR कोड स्कैन कर भक्तों का त्वरित प्रवेश एवं धार्मिक उत्सवों का आयोजन करें।"
              : "Check in devotees at the temple gate using pass numbers/QR codes and publish upcoming festivals."}
          </p>
        </div>

        {/* Tab switcher */}
        <div className="inline-flex rounded-[10px] bg-surface-2 p-1 border border-line">
          <button
            type="button"
            onClick={() => setActiveTab("checkin")}
            className={`rounded-[8px] px-3.5 py-1.5 text-xs font-semibold transition-all ${
              activeTab === "checkin"
                ? "bg-surface text-primary shadow-xs"
                : "text-muted hover:text-fg"
            }`}
          >
            🎫 {isHindi ? "गेट चेक-इन (Gate Entry)" : "Gate Check-in"}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("events")}
            className={`rounded-[8px] px-3.5 py-1.5 text-xs font-semibold transition-all ${
              activeTab === "events"
                ? "bg-surface text-primary shadow-xs"
                : "text-muted hover:text-fg"
            }`}
          >
            📅 {isHindi ? "उत्सव कैलेंडर (Festivals)" : "Festivals & Passes"}
          </button>
        </div>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: GATE PASS CHECK-IN */}
      {/* ========================================================================= */}
      {activeTab === "checkin" && (
        <div className="grid gap-6 lg:grid-cols-3">
          {/* Left Column: Check-in Scanner input */}
          <div className="lg:col-span-1 flex flex-col gap-4">
            <Card className="p-6">
              <h3 className="text-base font-bold font-spectral text-fg mb-1">
                {isHindi ? "द्वार प्रवेश चेक-इन" : "Gate Entry Check-in"}
              </h3>
              <p className="text-xs text-muted mb-4">
                {isHindi
                  ? "भक्त का पास नंबर दर्ज करें अथवा स्कैनर से QR टोकन स्कैन करें:"
                  : "Scan QR code or enter devotee pass number to grant entry:"}
              </p>

              {checkInError ? <Alert tone="danger" className="mb-4">{checkInError}</Alert> : null}

              <form onSubmit={handleCheckIn} className="flex flex-col gap-3">
                <TextField
                  label={isHindi ? "पास नंबर अथवा QR टोकन" : "Pass Number or Scanned QR"}
                  required
                  placeholder="PASS-1-123456 or MANDIR_TOKEN:..."
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                  className="font-mono text-sm"
                  autoFocus
                />
                <Button type="submit" busy={checkingIn} className="py-2.5 text-sm font-semibold">
                  ✓ {isHindi ? "प्रवेश सत्यापित करें" : "Verify & Check In"}
                </Button>
              </form>
            </Card>

            {/* Recent Check-in Card */}
            {recentCheckedIn ? (
              <Card className="p-5 border-emerald-500/40 bg-emerald-500/5 flex flex-col gap-2">
                <div className="flex items-center gap-2 text-emerald-800 font-bold text-sm">
                  <span className="size-6 rounded-full bg-emerald-200 text-emerald-900 flex items-center justify-center text-xs">
                    ✓
                  </span>
                  <span>{isHindi ? "प्रवेश स्वीकृत (Checked In!)" : "Entry Approved · Checked In"}</span>
                </div>
                <div className="text-xs space-y-1 mt-1 text-fg">
                  <div>
                    <span className="text-muted font-medium">{isHindi ? "प्रमुख भक्त:" : "Devotee:"}</span>{" "}
                    <span className="font-bold">{recentCheckedIn.primaryDevoteeName}</span>
                  </div>
                  <div>
                    <span className="text-muted font-medium">{isHindi ? "कुल संख्या:" : "Attendees:"}</span>{" "}
                    <span className="font-bold font-mono">{recentCheckedIn.attendeeCount} {isHindi ? "व्यक्ति" : "Devotees"}</span>
                  </div>
                  <div>
                    <span className="text-muted font-medium">{isHindi ? "उत्सव:" : "Event:"}</span>{" "}
                    <span>{recentCheckedIn.eventName}</span>
                  </div>
                  <div>
                    <span className="text-muted font-medium">{isHindi ? "पास नंबर:" : "Pass #:"}</span>{" "}
                    <span className="font-mono">{recentCheckedIn.passNumber}</span>
                  </div>
                </div>
              </Card>
            ) : null}
          </div>

          {/* Right Column: Event passes roster */}
          <div className="lg:col-span-2 flex flex-col gap-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-surface p-4 rounded-[12px] border border-line">
              <div className="flex items-center gap-2">
                <label className="text-xs font-semibold text-fg">
                  {isHindi ? "उत्सव चुनें:" : "Filter by Festival:"}
                </label>
                <select
                  value={selectedEventCode}
                  onChange={(e) => setSelectedEventCode(e.target.value)}
                  className="rounded-[8px] border border-line bg-surface px-2.5 py-1 text-xs text-fg focus:border-primary focus:outline-none"
                >
                  {events.map((ev) => (
                    <option key={ev.code} value={ev.code}>
                      {ev.name} ({ev.eventDate})
                    </option>
                  ))}
                </select>
              </div>

              <span className="text-xs text-muted font-medium">
                {eventPasses ? (isHindi ? `कुल ${eventPasses.length} पास जारी` : `${eventPasses.length} Issued Passes`) : null}
              </span>
            </div>

            {eventPasses === null ? (
              <Card className="p-12 text-center text-muted text-sm">
                {t.common.loading}
              </Card>
            ) : eventPasses.length === 0 ? (
              <Card className="p-12 text-center text-muted text-sm">
                <span className="text-3xl block mb-2">🎫</span>
                {isHindi ? "इस उत्सव के लिए अभी तक कोई पास नहीं बना है।" : "No darshan passes issued for this festival yet."}
              </Card>
            ) : (
              <div className="grid gap-3">
                {eventPasses.map((pass) => (
                  <Card key={pass.id} className="p-4 flex items-center justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-sm text-fg">{pass.primaryDevoteeName}</span>
                        <span className="font-mono text-xs text-primary font-semibold">
                          {pass.passNumber}
                        </span>
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                            pass.checkedIn
                              ? "bg-emerald-100 text-emerald-800"
                              : "bg-blue-100 text-blue-800"
                          }`}
                        >
                          {pass.checkedIn ? (isHindi ? "प्रवेश हो चुका" : "Checked In") : (isHindi ? "सक्रिय (Not Entered)" : "Active")}
                        </span>
                      </div>
                      <p className="text-xs text-muted mt-1">
                        📅 {pass.visitDate} · {pass.timeSlot} · {pass.attendeeCount} {isHindi ? "भक्त" : "Persons"}
                      </p>
                    </div>

                    {!pass.checkedIn ? (
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setTokenInput(pass.passNumber);
                        }}
                        className="text-xs px-3 py-1.5 shrink-0"
                      >
                        {isHindi ? "चेक-इन करें" : "Check In"}
                      </Button>
                    ) : (
                      <span className="text-xs text-emerald-700 font-medium">
                        ✓ {isHindi ? "प्रविष्ट" : "Inside"}
                      </span>
                    )}
                  </Card>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: FESTIVALS & PASSES CONFIGURATION */}
      {/* ========================================================================= */}
      {activeTab === "events" && (
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted">
              {isHindi ? "मंदिर सेंटर पर भक्तों के लिए आयोजित धार्मिक उत्सव व पर्व:" : "Festivals and holy occasions published on Mandir Center:"}
            </p>
            <Button onClick={openCreateModal} className="text-xs">
              + {isHindi ? "नया उत्सव जोड़ें" : "Create Festival / Event"}
            </Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            {events.map((ev) => (
              <Card key={ev.id} className="p-5 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <span className="rounded-full bg-primary/10 border border-primary/20 px-2.5 py-0.5 text-xs font-mono font-semibold text-primary">
                      📅 {ev.eventDate}
                    </span>
                    <span className="text-xs text-muted font-mono">{ev.timeRange}</span>
                  </div>
                  <h3 className="text-lg font-bold font-spectral text-fg">{ev.name}</h3>
                  <p className="text-xs text-muted mt-2 leading-relaxed">{ev.description}</p>
                  {ev.highlights ? (
                    <div className="mt-3 rounded-[8px] bg-surface-2 p-2.5 text-xs text-muted">
                      <span className="font-semibold text-fg">{isHindi ? "आकर्षण" : "Highlights"}:</span> {ev.highlights}
                    </div>
                  ) : null}
                </div>

                <div className="mt-4 pt-3 border-t border-line flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span
                      className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                        ev.registrationOpen ? "bg-emerald-100 text-emerald-800" : "bg-stone-200 text-stone-700"
                      }`}
                    >
                      {ev.registrationOpen ? (isHindi ? "ई-टोकन खुला है" : "Passes Open") : (isHindi ? "पास बंद" : "Closed")}
                    </span>
                    <span className="text-xs text-muted">
                      {isHindi ? `क्षमता: ${ev.maxCapacity}` : `Cap: ${ev.maxCapacity}`}
                    </span>
                  </div>

                  <Button variant="secondary" onClick={() => openEditModal(ev)} className="text-xs px-2.5 py-1">
                    {t.common.edit}
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Add / Edit Event Modal */}
      {showEventModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg bg-surface border border-line rounded-[16px] shadow-xl p-6 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-150 max-h-[90dvh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <h4 className="text-lg font-bold font-spectral">
                {editingEvent
                  ? (isHindi ? "उत्सव विवरण संपादित करें" : "Edit Festival Details")
                  : (isHindi ? "नया उत्सव / पर्व जोड़ें" : "Create New Festival")}
              </h4>
              <button
                type="button"
                onClick={() => setShowEventModal(false)}
                className="text-muted hover:text-fg text-sm"
              >
                ✕
              </button>
            </div>

            {eventFormError ? <Alert tone="danger">{eventFormError}</Alert> : null}

            <form onSubmit={handleSaveEvent} className="flex flex-col gap-4">
              <TextField
                label={isHindi ? "उत्सव का नाम" : "Festival Name"}
                required
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder={isHindi ? "उदा. हनुमान जयंती जन्मोत्सव" : "e.g. Hanuman Jayanti Mahotsav"}
              />

              <div className="grid gap-3 sm:grid-cols-2">
                <TextField
                  label={isHindi ? "उत्सव तिथि" : "Event Date"}
                  type="date"
                  required
                  value={formDate}
                  onChange={(e) => setFormDate(e.target.value)}
                />
                <TextField
                  label={isHindi ? "समय सीमा" : "Time Range"}
                  required
                  value={formTimeRange}
                  onChange={(e) => setFormTimeRange(e.target.value)}
                  placeholder="06:00 PM – 10:00 PM"
                />
              </div>

              <TextField
                label={isHindi ? "विवरण" : "Description"}
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                placeholder={isHindi ? "उत्सव का आध्यात्मिक महत्व..." : "Details of celebration..."}
              />

              <TextField
                label={isHindi ? "विशेष आकर्षण" : "Highlights"}
                value={formHighlights}
                onChange={(e) => setFormHighlights(e.target.value)}
                placeholder={isHindi ? "उदा. 56 भोग, सुंदरकाण्ड पाठ, भंडारा" : "e.g. Mahaprasad, Bhajan Sandhya"}
              />

              <TextField
                label={isHindi ? "अधिकतम पास क्षमता" : "Maximum Pass Capacity"}
                type="number"
                value={formCapacity}
                onChange={(e) => setFormCapacity(e.target.value)}
              />

              <div className="flex items-center gap-4 text-xs">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formRegOpen}
                    onChange={(e) => setFormRegOpen(e.target.checked)}
                    className="accent-primary"
                  />
                  <span>{isHindi ? "ई-टोकन पास पंजीकरण खुला रखें" : "Registration Open for Passes"}</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formActive}
                    onChange={(e) => setFormActive(e.target.checked)}
                    className="accent-primary"
                  />
                  <span>{isHindi ? "सक्रिय (मंदिर सेंटर पर दिखेगा)" : "Active on Portal"}</span>
                </label>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-line">
                <Button variant="secondary" onClick={() => setShowEventModal(false)}>
                  {t.common.cancel}
                </Button>
                <Button type="submit" busy={savingEvent}>
                  {t.common.save}
                </Button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
}
