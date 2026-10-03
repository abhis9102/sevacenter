"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, PageHeader, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole } from "@/lib/types";

interface Profile {
  deity: string | null;
  address: string | null;
  helpline: string | null;
  timings: string | null;
  announcement: string | null;
}

/** Edit the public temple page (ADR 0017). LEADER+; everything optional. */
export default function TempleSettingsPage() {
  const me = useMe();
  const canEdit = hasRole(me.role, "LEADER");
  const [f, setF] = useState({ deity: "", address: "", helpline: "", timings: "", announcement: "" });
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<Profile>("/temple").then((p) => setF({
      deity: p.deity ?? "", address: p.address ?? "", helpline: p.helpline ?? "", timings: p.timings ?? "",
      announcement: p.announcement ?? "",
    })).catch((err) => setNotice({ tone: "danger", text: describeError(err) }));
  }, []);

  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice(null);
    setFields({});
    try {
      await api.request("/temple", { method: "PUT", json: Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v.trim() || null])) });
      setNotice({ tone: "success", text: "Saved. It's live on your temple's public page." });
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setNotice({ tone: "danger", text: describeError(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Temple page" description="What devotees see on your temple's own address. Leave anything blank to hide it." />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}
      <Card className="max-w-2xl">
        <form onSubmit={onSave} className="flex flex-col gap-3">
          <TextField label="Presiding deity" value={f.deity} onChange={set("deity")} disabled={!canEdit} />
          <TextField label="Darshan timings" placeholder="e.g. 5:30 am - 12:30 pm, 4 pm - 9 pm" value={f.timings}
                     onChange={set("timings")} disabled={!canEdit} />
          <TextField label="Announcement" value={f.announcement} onChange={set("announcement")} disabled={!canEdit} />
          <TextField label="Address" value={f.address} onChange={set("address")} disabled={!canEdit} />
          <TextField label="Helpline" inputMode="tel" value={f.helpline} onChange={set("helpline")} error={fields.helpline}
                     disabled={!canEdit} />
          {canEdit ? <div><Button type="submit" busy={busy}>Save</Button></div> : <p className="text-sm text-muted">Only leaders can edit this page.</p>}
        </form>
      </Card>
    </div>
  );
}
