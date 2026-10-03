"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import type { PujaBookingAdminItem, PujaCatalogItem } from "@/lib/types";

export default function AdminPujasPage() {
  const { lang, t } = useLanguage();
  const isHindi = lang === "hi";

  const [activeTab, setActiveTab] = useState<"sankalp" | "catalog">("sankalp");

  // Sankalp Roster state
  const [selectedDate, setSelectedDate] = useState<string>(() => new Date().toISOString().split("T")[0] ?? "");
  const [bookings, setBookings] = useState<PujaBookingAdminItem[] | null>(null);
  const [sankalpError, setSankalpError] = useState<string | null>(null);
  const [completingId, setCompletingId] = useState<number | null>(null);

  // Catalog state
  const [catalog, setCatalog] = useState<PujaCatalogItem[] | null>(null);
  const [showAddModal, setShowAddModal] = useState<boolean>(false);
  const [editingPuja, setEditingPuja] = useState<PujaCatalogItem | null>(null);
  const [pujaFormError, setPujaFormError] = useState<string | null>(null);
  const [savingPuja, setSavingPuja] = useState<boolean>(false);

  // New/Edit Puja Form fields
  const [formCode, setFormCode] = useState<string>("");
  const [formName, setFormName] = useState<string>("");
  const [formDeity, setFormDeity] = useState<string>("");
  const [formDuration, setFormDuration] = useState<string>("45 mins");
  const [formDakshina, setFormDakshina] = useState<string>("1100");
  const [formDescription, setFormDescription] = useState<string>("");
  const [formPrasad, setFormPrasad] = useState<boolean>(true);
  const [formActive, setFormActive] = useState<boolean>(true);

  // Load bookings when sankalp tab or date changes
  useEffect(() => {
    let mounted = true;
    const query = selectedDate ? `?date=${selectedDate}` : "";
    api
      .get<PujaBookingAdminItem[]>(`/mandir/pujas/bookings${query}`)
      .then((data) => {
        if (mounted) setBookings(data);
      })
      .catch((err) => {
        if (mounted) setSankalpError(describeError(err, t.errors));
      });
    return () => {
      mounted = false;
    };
  }, [selectedDate, t.errors]);

  // Load catalog when catalog tab is opened
  useEffect(() => {
    if (activeTab === "catalog" && catalog === null) {
      let mounted = true;
      api
        .get<PujaCatalogItem[]>("/mandir/pujas")
        .then((list) => {
          if (mounted) setCatalog(list);
        })
        .catch((err) => {
          if (mounted) setPujaFormError(describeError(err, t.errors));
        });
      return () => {
        mounted = false;
      };
    }
  }, [activeTab, catalog, t.errors]);

  async function handleMarkComplete(id: number) {
    setCompletingId(id);
    try {
      const updated = await api.request<PujaBookingAdminItem>(`/mandir/pujas/bookings/${id}/complete`, {
        method: "POST",
        json: { priestName: "Mandir Acharya" },
      });
      setBookings((prev) => (prev ? prev.map((b) => (b.id === id ? updated : b)) : null));
    } catch (err) {
      setSankalpError(describeError(err, t.errors));
    } finally {
      setCompletingId(null);
    }
  }

  function openCreateModal() {
    setEditingPuja(null);
    setFormCode("");
    setFormName("");
    setFormDeity("Pradhan Devata");
    setFormDuration("45 mins");
    setFormDakshina("1100");
    setFormDescription("");
    setFormPrasad(true);
    setFormActive(true);
    setPujaFormError(null);
    setShowAddModal(true);
  }

  function openEditModal(item: PujaCatalogItem) {
    setEditingPuja(item);
    setFormCode(item.code);
    setFormName(item.name);
    setFormDeity(item.deity);
    setFormDuration(item.duration);
    setFormDakshina(String(item.dakshinaRupees));
    setFormDescription(item.description || "");
    setFormPrasad(item.prasadIncluded);
    setFormActive(item.active);
    setPujaFormError(null);
    setShowAddModal(true);
  }

  async function handleSavePuja(e: React.FormEvent) {
    e.preventDefault();
    setPujaFormError(null);
    if (!formName.trim()) {
      setPujaFormError(isHindi ? "पूजा का नाम अनिवार्य है" : "Puja name is required");
      return;
    }

    setSavingPuja(true);
    try {
      if (editingPuja) {
        const updated = await api.request<PujaCatalogItem>(`/mandir/pujas/${editingPuja.id}`, {
          method: "PUT",
          json: {
            name: formName.trim(),
            deity: formDeity.trim(),
            duration: formDuration.trim(),
            dakshinaRupees: Number(formDakshina) || 0,
            description: formDescription.trim() || null,
            prasadIncluded: formPrasad,
            active: formActive,
            displayOrder: editingPuja.displayOrder,
          },
        });
        setCatalog((prev) => (prev ? prev.map((p) => (p.id === updated.id ? updated : p)) : null));
      } else {
        const code = formCode.trim() || formName.trim().toUpperCase().replace(/[^A-Z0-9]/g, "_");
        const created = await api.request<PujaCatalogItem>("/mandir/pujas", {
          method: "POST",
          json: {
            code,
            name: formName.trim(),
            deity: formDeity.trim(),
            duration: formDuration.trim(),
            dakshinaRupees: Number(formDakshina) || 0,
            description: formDescription.trim() || null,
            prasadIncluded: formPrasad,
            active: formActive,
            displayOrder: (catalog?.length ?? 0) + 1,
          },
        });
        setCatalog((prev) => (prev ? [...prev, created] : [created]));
      }
      setShowAddModal(false);
    } catch (err) {
      setPujaFormError(describeError(err, t.errors));
    } finally {
      setSavingPuja(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl sm:text-2xl font-bold font-spectral tracking-tight">
              {isHindi ? "पूजा एवं ससंकल्प प्रबंधन" : "Pujas & Sacred Sankalp"}
            </h1>
            <span className="rounded-full bg-primary/10 border border-primary/20 px-2.5 py-0.5 text-xs font-semibold text-primary">
              Planning Center Services
            </span>
          </div>
          <p className="text-xs sm:text-sm text-muted mt-1">
            {isHindi
              ? "आचार्यों हेतु ससंकल्प नामावली देखें एवं मंदिर पूजा कैटलॉग प्रबंधित करें।"
              : "View priest recitation rosters with devotee Gotra and family names, and configure the temple puja catalog."}
          </p>
        </div>

        {/* Tab switcher */}
        <div className="inline-flex rounded-[10px] bg-surface-2 p-1 border border-line">
          <button
            type="button"
            onClick={() => setActiveTab("sankalp")}
            className={`rounded-[8px] px-3.5 py-1.5 text-xs font-semibold transition-all ${
              activeTab === "sankalp"
                ? "bg-surface text-primary shadow-xs"
                : "text-muted hover:text-fg"
            }`}
          >
            📜 {isHindi ? "संकल्प नामावली (पुजारी व्यू)" : "Priest Sankalp Roster"}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("catalog")}
            className={`rounded-[8px] px-3.5 py-1.5 text-xs font-semibold transition-all ${
              activeTab === "catalog"
                ? "bg-surface text-primary shadow-xs"
                : "text-muted hover:text-fg"
            }`}
          >
            📖 {isHindi ? "पूजा कैटलॉग एवं दक्षिणा" : "Puja Catalog & Fees"}
          </button>
        </div>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: PRIEST SANKALP ROSTER */}
      {/* ========================================================================= */}
      {activeTab === "sankalp" && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-surface p-4 rounded-[12px] border border-line">
            <div className="flex items-center gap-2">
              <label className="text-xs font-semibold text-fg">
                {isHindi ? "पूजा तिथि चुनें:" : "Select Puja Date:"}
              </label>
              <input
                type="date"
                value={selectedDate}
                onChange={(e) => setSelectedDate(e.target.value)}
                className="rounded-[8px] border border-line bg-surface px-2.5 py-1 text-xs text-fg focus:border-primary focus:outline-none"
              />
              <button
                type="button"
                onClick={() => setSelectedDate(new Date().toISOString().split("T")[0] ?? "")}
                className="text-xs text-primary font-medium hover:underline ml-1"
              >
                {isHindi ? "आज (Today)" : "Today"}
              </button>
            </div>

            <span className="text-xs text-muted font-medium">
              {bookings ? (isHindi ? `कुल ${bookings.length} पूजा संकल्प` : `${bookings.length} Sankalp Bookings`) : null}
            </span>
          </div>

          {sankalpError ? <Alert tone="danger">{sankalpError}</Alert> : null}

          {bookings === null ? (
            <Card className="p-12 text-center text-muted text-sm">
              {t.common.loading}
            </Card>
          ) : bookings.length === 0 ? (
            <Card className="p-12 text-center text-muted text-sm">
              <span className="text-3xl block mb-2">🕉️</span>
              {isHindi ? "इस तिथि के लिए कोई ससंकल्प पूजा बुक नहीं है।" : "No puja bookings scheduled for this date."}
            </Card>
          ) : (
            <div className="grid gap-4">
              {bookings.map((b) => (
                <Card key={b.id} className="p-5 flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                  <div className="flex flex-col gap-2 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-bold text-base text-fg font-spectral">{b.pujaName}</span>
                      <span className="font-mono text-xs text-muted bg-surface-2 px-2 py-0.5 rounded">
                        {b.bookingNumber}
                      </span>
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-[10px] font-semibold ${
                          b.status === "COMPLETED"
                            ? "bg-emerald-100 text-emerald-800"
                            : "bg-amber-100 text-amber-800"
                        }`}
                      >
                        {b.status}
                      </span>
                    </div>

                    <div className="grid sm:grid-cols-2 gap-2 text-xs bg-surface-2 p-3 rounded-[10px] border border-line/60">
                      <div>
                        <span className="font-semibold text-muted block">{isHindi ? "यजमान (Devotee):" : "Yajman (Devotee):"}</span>
                        <span className="text-fg font-bold text-sm">{b.devoteeName}</span>
                      </div>
                      <div>
                        <span className="font-semibold text-muted block">{isHindi ? "गोत्र / नक्षत्र / राशि:" : "Gotra / Nakshatra / Rashi:"}</span>
                        <span className="text-fg font-medium">
                          {b.gotra || "—"} / {b.nakshatra || "—"} / {b.rashi || "—"}
                        </span>
                      </div>
                      {b.familyMembers ? (
                        <div className="sm:col-span-2">
                          <span className="font-semibold text-muted block">{isHindi ? "सपरिवार संकल्प नाम:" : "Family Members for Recitation:"}</span>
                          <span className="text-primary-strong font-medium">{b.familyMembers}</span>
                        </div>
                      ) : null}
                      <div>
                        <span className="font-semibold text-muted block">{isHindi ? "समय स्लॉट:" : "Time Slot:"}</span>
                        <span className="text-fg font-mono">{b.timeSlot}</span>
                      </div>
                      <div>
                        <span className="font-semibold text-muted block">{isHindi ? "दक्षिणा / शुल्क:" : "Dakshina:"}</span>
                        <span className="text-fg font-mono font-bold">₹{(b.amountPaise / 100).toLocaleString("en-IN")}</span>
                      </div>
                    </div>

                    {b.performedBy ? (
                      <p className="text-[11px] text-emerald-700 italic">
                        ✓ {isHindi ? `आचार्य ${b.performedBy} द्वारा संकल्प संपन्न किया गया।` : `Sankalpam performed by ${b.performedBy}.`}
                      </p>
                    ) : null}
                  </div>

                  {b.status !== "COMPLETED" ? (
                    <Button
                      onClick={() => handleMarkComplete(b.id)}
                      busy={completingId === b.id}
                      className="text-xs px-3 py-2 shrink-0"
                    >
                      ✓ {isHindi ? "संकल्प संपन्न मार्क करें" : "Mark Sankalp Recited"}
                    </Button>
                  ) : null}
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: PUJA CATALOG */}
      {/* ========================================================================= */}
      {activeTab === "catalog" && (
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted">
              {isHindi ? "भक्तों के लिए मंदिर सेंटर पर प्रदर्शित होने वाली वैदिक पूजाएं:" : "Rituals offered to devotees on Mandir Center:"}
            </p>
            <Button onClick={openCreateModal} className="text-xs">
              + {isHindi ? "नई पूजा जोड़ें" : "Add New Puja"}
            </Button>
          </div>

          {loadingCatalog ? (
            <Card className="p-12 text-center text-muted text-sm">
              {t.common.loading}
            </Card>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {catalog.map((p) => (
              <Card key={p.id} className="p-5 flex flex-col justify-between">
                <div>
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <span className="rounded-full bg-primary/10 border border-primary/20 px-2 py-0.5 text-[11px] font-semibold text-primary">
                      {p.deity}
                    </span>
                    <span className="text-base font-bold font-mono text-fg">
                      ₹{p.dakshinaRupees.toLocaleString("en-IN")}
                    </span>
                  </div>
                  <h3 className="text-base font-bold font-spectral text-fg">{p.name}</h3>
                  <p className="text-xs text-muted mt-2 leading-relaxed">{p.description}</p>
                  <div className="mt-3 flex items-center gap-3 text-xs text-muted">
                    <span>⏱️ {p.duration}</span>
                    {p.prasadIncluded ? (
                      <span className="text-emerald-700 font-medium">✓ {isHindi ? "महाप्रसाद सहित" : "Prasad Included"}</span>
                    ) : null}
                  </div>
                </div>

                <div className="mt-4 pt-3 border-t border-line flex items-center justify-between">
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                      p.active ? "bg-emerald-100 text-emerald-800" : "bg-stone-200 text-stone-700"
                    }`}
                  >
                    {p.active ? (isHindi ? "सक्रिय" : "Active") : (isHindi ? "निष्क्रिय" : "Hidden")}
                  </span>
                  <Button variant="secondary" onClick={() => openEditModal(p)} className="text-xs px-2.5 py-1">
                    {t.common.edit}
                  </Button>
                </div>
              </Card>
            ))}
          </div>
          )}
        </div>
      )}

      {/* Add / Edit Puja Modal */}
      {showAddModal ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md bg-surface border border-line rounded-[16px] shadow-xl p-6 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between">
              <h4 className="text-lg font-bold font-spectral">
                {editingPuja
                  ? (isHindi ? "पूजा संपादित करें" : "Edit Puja Details")
                  : (isHindi ? "नई पूजा जोड़ें" : "Add New Puja to Catalog")}
              </h4>
              <button
                type="button"
                onClick={() => setShowAddModal(false)}
                className="text-muted hover:text-fg text-sm"
              >
                ✕
              </button>
            </div>

            {pujaFormError ? <Alert tone="danger">{pujaFormError}</Alert> : null}

            <form onSubmit={handleSavePuja} className="flex flex-col gap-4">
              <TextField
                label={isHindi ? "पूजा का नाम" : "Puja Name"}
                required
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder={isHindi ? "उदा. महा मृत्युंजय जप" : "e.g. Maha Mrityunjaya Japa"}
              />

              <div className="grid gap-3 sm:grid-cols-2">
                <TextField
                  label={isHindi ? "आराध्य देवता" : "Deity"}
                  required
                  value={formDeity}
                  onChange={(e) => setFormDeity(e.target.value)}
                  placeholder="Lord Shiva"
                />
                <TextField
                  label={isHindi ? "अनुष्ठान अवधि" : "Duration"}
                  required
                  value={formDuration}
                  onChange={(e) => setFormDuration(e.target.value)}
                  placeholder="60 mins"
                />
              </div>

              <TextField
                label={isHindi ? "दक्षिणा / सेवा राशि (₹)" : "Dakshina / Seva Amount (₹)"}
                type="number"
                required
                value={formDakshina}
                onChange={(e) => setFormDakshina(e.target.value)}
              />

              <TextField
                label={isHindi ? "विवरण / महत्व" : "Description & Significance"}
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                placeholder={isHindi ? "पूजा का धार्मिक महत्व..." : "Spiritual significance..."}
              />

              <div className="flex items-center gap-4 text-xs">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formPrasad}
                    onChange={(e) => setFormPrasad(e.target.checked)}
                    className="accent-primary"
                  />
                  <span>{isHindi ? "महाप्रसाद सम्मिलित है" : "Prasad Included"}</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formActive}
                    onChange={(e) => setFormActive(e.target.checked)}
                    className="accent-primary"
                  />
                  <span>{isHindi ? "सक्रिय (मंदिर सेंटर पर दिखेगा)" : "Active (Visible on Portal)"}</span>
                </label>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-line">
                <Button variant="secondary" onClick={() => setShowAddModal(false)}>
                  {t.common.cancel}
                </Button>
                <Button type="submit" busy={savingPuja}>
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
