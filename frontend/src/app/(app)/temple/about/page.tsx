"use client";

import { useEffect, useState } from "react";

import { AboutPhotos } from "@/components/AboutPhotos";
import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { TempleTabs } from "@/components/TempleTabs";
import { Alert, Button, Card, PageHeader } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole } from "@/lib/types";

/**
 * Mandir Center → About & history: the text and photos of the public About page. Saves through
 * PUT /temple, which replaces the whole temple profile, so the loaded profile goes back with only
 * About and History changed (never blanking the deity, hours or aartis).
 */
export default function TempleAboutPage() {
  const me = useMe();
  const canEdit = hasRole(me.role, "LEADER");
  const [profile, setProfile] = useState<Record<string, unknown> | null>(null);
  const [about, setAbout] = useState("");
  const [history, setHistory] = useState("");
  const [showAbout, setShowAbout] = useState(true);
  const [showHistory, setShowHistory] = useState(true);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<Record<string, unknown>>("/temple").then((p) => {
      setProfile(p);
      setAbout((p.about as string | null) ?? "");
      setHistory((p.history as string | null) ?? "");
      setShowAbout(p.showAbout !== false);
      setShowHistory(p.showHistory !== false);
    }).catch((err) => setNotice({ tone: "danger", text: describeError(err) }));
  }, []);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!profile) return;
    setBusy(true);
    setNotice(null);
    try {
      const saved = await api.request<Record<string, unknown>>("/temple", {
        method: "PUT", json: { ...profile, about: about.trim() || null, history: history.trim() || null, showAbout, showHistory },
      });
      setProfile(saved);
      setNotice({ tone: "success", text: "Saved. It's live on your Mandir Center's About page." });
    } catch (err) {
      setNotice({ tone: "danger", text: err instanceof ApiError ? describeError(err) : String(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Mandir Center" description="The About page devotees see: who you are, your history, and photos of both." />
      <TempleTabs />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}
      <form method="post" onSubmit={save} className="flex flex-col gap-6">
        <Card className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-semibold">About the temple</h2>
            <Toggle label="Show on Mandir Center" checked={showAbout} onChange={setShowAbout} disabled={!canEdit} />
          </div>
          <textarea rows={6} maxLength={4000} value={about} onChange={(e) => setAbout(e.target.value)} disabled={!canEdit || !profile}
                    placeholder="Who the temple is dedicated to, what devotees can expect, festivals it is known for"
                    aria-label="About the temple" className="rounded-[10px] border border-line bg-surface px-3 py-2 text-sm" />
          <span className="text-xs text-muted">{about.length} / 4000 · plain text, line breaks kept · remember to Save</span>
          <AboutPhotos section="ABOUT" canEdit={canEdit} />
        </Card>
        <Card className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-semibold">History</h2>
            <Toggle label="Show on Mandir Center" checked={showHistory} onChange={setShowHistory} disabled={!canEdit} />
          </div>
          <textarea rows={8} maxLength={8000} value={history} onChange={(e) => setHistory(e.target.value)} disabled={!canEdit || !profile}
                    placeholder="When and by whom it was founded, renovations, stories devotees should know"
                    aria-label="History" className="rounded-[10px] border border-line bg-surface px-3 py-2 text-sm" />
          <span className="text-xs text-muted">{history.length} / 8000</span>
          <AboutPhotos section="HISTORY" canEdit={canEdit} />
        </Card>
        {canEdit ? <div><Button type="submit" busy={busy} disabled={!profile}>Save About & history</Button></div>
          : <p className="text-sm text-muted">Only leaders can edit the About page.</p>}
      </form>
    </div>
  );
}

/** A labelled switch: hidden sections keep their text and photos, they just aren't published. */
function Toggle({ label, checked, onChange, disabled }: {
  label: string; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean;
}) {
  return (
    <label className="flex cursor-pointer items-center gap-2 text-sm">
      <span className={checked ? "text-fg" : "text-muted"}>{checked ? label : "Hidden from Mandir Center"}</span>
      <button type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled}
              onClick={() => onChange(!checked)}
              className={`relative h-6 w-11 rounded-full transition-colors ${checked ? "bg-primary" : "bg-line"} disabled:opacity-50`}>
        <span className={`absolute top-0.5 size-5 rounded-full bg-white shadow transition-all ${checked ? "left-[22px]" : "left-0.5"}`} />
      </button>
    </label>
  );
}
