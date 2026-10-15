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
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<Record<string, unknown>>("/temple").then((p) => {
      setProfile(p);
      setAbout((p.about as string | null) ?? "");
      setHistory((p.history as string | null) ?? "");
    }).catch((err) => setNotice({ tone: "danger", text: describeError(err) }));
  }, []);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!profile) return;
    setBusy(true);
    setNotice(null);
    try {
      const saved = await api.request<Record<string, unknown>>("/temple", {
        method: "PUT", json: { ...profile, about: about.trim() || null, history: history.trim() || null },
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
          <h2 className="text-base font-semibold">About the temple</h2>
          <textarea rows={6} maxLength={4000} value={about} onChange={(e) => setAbout(e.target.value)} disabled={!canEdit || !profile}
                    placeholder="Who the temple is dedicated to, what devotees can expect, festivals it is known for"
                    aria-label="About the temple" className="rounded-[10px] border border-line bg-surface px-3 py-2 text-sm" />
          <span className="text-xs text-muted">{about.length} / 4000 · plain text, line breaks kept</span>
          <AboutPhotos section="ABOUT" canEdit={canEdit} />
        </Card>
        <Card className="flex flex-col gap-3">
          <h2 className="text-base font-semibold">History</h2>
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
