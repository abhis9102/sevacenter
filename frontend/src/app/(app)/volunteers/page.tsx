"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import type { SevakAdminItem } from "@/lib/types";

export default function AdminVolunteersPage() {
  const { lang, t } = useLanguage();
  const isHindi = lang === "hi";

  const [volunteers, setVolunteers] = useState<SevakAdminItem[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [filterArea, setFilterArea] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  // Assign modal state
  const [assigningSevak, setAssigningSevak] = useState<SevakAdminItem | null>(null);
  const [newStatus, setNewStatus] = useState<string>("APPROVED");
  const [newTeam, setNewTeam] = useState<string>("Kitchen Seva Team A");
  const [newEvent, setNewEvent] = useState<string>("Upcoming Utsav");
  const [assigningBusy, setAssigningBusy] = useState<boolean>(false);
  const [assignError, setAssignError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    void (async () => {
      try {
        const params = new URLSearchParams();
        if (filterArea) params.set("sevaArea", filterArea);
        if (filterStatus) params.set("status", filterStatus);
        const data = await api.get<SevakAdminItem[]>(`/mandir/volunteers?${params.toString()}`);
        if (mounted) setVolunteers(data);
      } catch (err) {
        if (mounted) setError(describeError(err, t.errors));
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, [filterArea, filterStatus, t.errors]);

  async function handleSaveAssignment(e: React.FormEvent) {
    e.preventDefault();
    if (!assigningSevak) return;
    setAssignError(null);
    setAssigningBusy(true);

    try {
      const updated = await api.request<SevakAdminItem>(`/mandir/volunteers/${assigningSevak.id}/status`, {
        method: "PUT",
        json: {
          status: newStatus,
          assignedTeam: newTeam.trim() || null,
          assignedEvent: newEvent.trim() || null,
        },
      });
      setVolunteers((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));
      setAssigningSevak(null);
    } catch (err) {
      setAssignError(describeError(err, t.errors));
    } finally {
      setAssigningBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2">
          <h1 className="text-xl sm:text-2xl font-bold font-spectral tracking-tight">
            {isHindi ? "मंदिर सेवादार (स्वयंसेवक) प्रबंधन" : "Sevak & Volunteer Hub"}
          </h1>
          <span className="rounded-full bg-primary/10 border border-primary/20 px-2.5 py-0.5 text-xs font-semibold text-primary">
            Planning Center Volunteers
          </span>
        </div>
        <p className="text-xs sm:text-sm text-muted mt-1">
          {isHindi
            ? "मंदिर सेंटर से प्राप्त सेवादार आवेदनों की समीक्षा करें एवं उन्हें भंडारा, दर्शन व्यवस्था और उत्सवों में तैनात करें।"
            : "Review volunteer applications from Mandir Center, organize teams, and assign sevaks to festivals & shifts."}
        </p>
      </div>

      {/* Filters Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-surface p-4 rounded-[12px] border border-line">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5">
            <label className="text-xs font-semibold text-fg">{isHindi ? "सेवा क्षेत्र:" : "Seva Area:"}</label>
            <select
              value={filterArea}
              onChange={(e) => setFilterArea(e.target.value)}
              className="rounded-[8px] border border-line bg-surface px-2.5 py-1 text-xs text-fg focus:border-primary focus:outline-none"
            >
              <option value="">{isHindi ? "सभी क्षेत्र (All Areas)" : "All Areas"}</option>
              <option value="Mahaprasad & Annadanam Bhandara">{isHindi ? "महाप्रसाद / भंडारा" : "Mahaprasad / Bhandara"}</option>
              <option value="Darshan Queue & Crowd Management">{isHindi ? "दर्शन पंक्ति व्यवस्था" : "Darshan Queue"}</option>
              <option value="Mandir Floral & Campus Decoration">{isHindi ? "मंदिर एवं पुष्प सज्जा" : "Floral Decoration"}</option>
              <option value="Footwear Stand & Cleanliness Seva">{isHindi ? "जूता स्टैंड व स्वच्छता" : "Footwear Stand"}</option>
              <option value="Jal Seva & Pilgrim Assistance">{isHindi ? "जल सेवा व तीर्थयात्री" : "Jal Seva"}</option>
            </select>
          </div>

          <div className="flex items-center gap-1.5">
            <label className="text-xs font-semibold text-fg">{isHindi ? "स्थिति:" : "Status:"}</label>
            <select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value)}
              className="rounded-[8px] border border-line bg-surface px-2.5 py-1 text-xs text-fg focus:border-primary focus:outline-none"
            >
              <option value="">{isHindi ? "सभी (All)" : "All Status"}</option>
              <option value="PENDING">{isHindi ? "लंबित (Pending)" : "Pending"}</option>
              <option value="APPROVED">{isHindi ? "स्वीकृत (Approved)" : "Approved"}</option>
              <option value="ASSIGNED">{isHindi ? "तैनात (Assigned)" : "Assigned"}</option>
            </select>
          </div>
        </div>

        <span className="text-xs text-muted font-medium">
          {isHindi ? `कुल ${volunteers.length} सेवादार` : `${volunteers.length} Sevaks`}
        </span>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}

      {loading ? (
        <Card className="p-12 text-center text-muted text-sm">
          {t.common.loading}
        </Card>
      ) : volunteers.length === 0 ? (
        <Card className="p-12 text-center text-muted text-sm">
          <span className="text-3xl block mb-2">🤝</span>
          {isHindi ? "कोई सेवादार रिकॉर्ड नहीं मिला।" : "No volunteer applications found matching the selected filter."}
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {volunteers.map((v) => (
            <Card key={v.id} className="p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-2 mb-2">
                  <div>
                    <h3 className="text-base font-bold text-fg font-spectral">{v.fullName}</h3>
                    <p className="font-mono text-xs text-muted">{v.contact}</p>
                  </div>
                  <span
                    className={`rounded-full px-2.5 py-0.5 text-[10px] font-semibold ${
                      v.status === "ASSIGNED"
                        ? "bg-purple-100 text-purple-800"
                        : v.status === "APPROVED"
                        ? "bg-emerald-100 text-emerald-800"
                        : "bg-amber-100 text-amber-800"
                    }`}
                  >
                    {v.status}
                  </span>
                </div>

                <div className="text-xs space-y-1 bg-surface-2 p-3 rounded-[10px] border border-line/60 my-2">
                  <div>
                    <span className="font-semibold text-muted">{isHindi ? "सेवा क्षेत्र:" : "Seva Area:"}</span>{" "}
                    <span className="text-fg font-medium">{v.sevaArea}</span>
                  </div>
                  <div>
                    <span className="font-semibold text-muted">{isHindi ? "उपलब्धता / पाली:" : "Availability / Shift:"}</span>{" "}
                    <span className="text-fg">{v.availableDays} ({v.shiftPreference})</span>
                  </div>
                  {v.notes ? (
                    <div>
                      <span className="font-semibold text-muted">{isHindi ? "विशेष कौशल:" : "Skills / Notes:"}</span>{" "}
                      <span className="text-muted italic">{v.notes}</span>
                    </div>
                  ) : null}
                  {v.assignedTeam ? (
                    <div className="pt-1 border-t border-line/50 text-primary-strong font-medium">
                      ✓ {isHindi ? "टीम:" : "Team:"} {v.assignedTeam} {v.assignedEvent ? `(${v.assignedEvent})` : ""}
                    </div>
                  ) : null}
                </div>
              </div>

              <div className="mt-2 pt-2 border-t border-line flex justify-end">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setAssigningSevak(v);
                    setNewStatus(v.status === "PENDING" ? "APPROVED" : v.status);
                    setNewTeam(v.assignedTeam || "Mahaprasad Kitchen Team");
                    setNewEvent(v.assignedEvent || "Upcoming Festival");
                    setAssignError(null);
                  }}
                  className="text-xs px-3 py-1"
                >
                  {isHindi ? "तैनात / स्थिति बदलें" : "Assign / Update"}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Assign / Status Modal */}
      {assigningSevak ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md bg-surface border border-line rounded-[16px] shadow-xl p-6 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between">
              <h4 className="text-lg font-bold font-spectral">
                {isHindi ? "सेवादार तैनाती एवं अनुमोदन" : "Assign Sevak & Team"}
              </h4>
              <button
                type="button"
                onClick={() => setAssigningSevak(null)}
                className="text-muted hover:text-fg text-sm"
              >
                ✕
              </button>
            </div>

            {assignError ? <Alert tone="danger">{assignError}</Alert> : null}

            <form onSubmit={handleSaveAssignment} className="flex flex-col gap-4">
              <div className="text-xs bg-surface-2 p-3 rounded-[10px]">
                <div className="font-bold text-sm text-fg">{assigningSevak.fullName}</div>
                <div className="text-muted">{assigningSevak.contact} · {assigningSevak.sevaArea}</div>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium">{isHindi ? "स्थिति (Status)" : "Status"}</label>
                <select
                  value={newStatus}
                  onChange={(e) => setNewStatus(e.target.value)}
                  className="w-full rounded-[10px] border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-primary focus:outline-none"
                >
                  <option value="PENDING">{isHindi ? "लंबित (Pending)" : "Pending"}</option>
                  <option value="APPROVED">{isHindi ? "स्वीकृत (Approved)" : "Approved"}</option>
                  <option value="ASSIGNED">{isHindi ? "तैनात (Assigned to Team)" : "Assigned to Team"}</option>
                </select>
              </div>

              <TextField
                label={isHindi ? "तैनात सेवा टीम (Assigned Team)" : "Assigned Team"}
                value={newTeam}
                onChange={(e) => setNewTeam(e.target.value)}
                placeholder="Kitchen Seva Team A"
              />

              <TextField
                label={isHindi ? "उत्सव अथवा जिम्मेदारी (Event / Duty)" : "Event or Duty"}
                value={newEvent}
                onChange={(e) => setNewEvent(e.target.value)}
                placeholder="Maha Shivratri Mahotsav"
              />

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-line">
                <Button variant="secondary" onClick={() => setAssigningSevak(null)}>
                  {t.common.cancel}
                </Button>
                <Button type="submit" busy={assigningBusy}>
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
