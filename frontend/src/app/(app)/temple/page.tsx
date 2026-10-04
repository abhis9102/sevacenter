"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, PageHeader, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import type { LunarCalendar } from "@/lib/panchang";
import { hasRole } from "@/lib/types";

type Status = "OPEN" | "CLOSED" | null;

interface Profile {
  deity: string | null;
  address: string | null;
  helpline: string | null;
  timings: string | null;
  announcement: string | null;
  hours: { morningOpen: string | null; morningClose: string | null; eveningOpen: string | null; eveningClose: string | null } | null;
  aartis: { name: string; at: string; description: string | null }[];
  calendar: LunarCalendar;
  status: Status;
  statusNote: string | null;
}

const MAX_AARTIS = 12;
const hhmm = (t: string | null | undefined) => (t ? t.slice(0, 5) : "");
const orNull = (v: string) => v.trim() || null;

/** Edit the public temple page (ADR 0017, 0024). LEADER+; everything optional. */
export default function TempleSettingsPage() {
  const me = useMe();
  const canEdit = hasRole(me.role, "LEADER");
  const [f, setF] = useState({ deity: "", address: "", helpline: "", timings: "", announcement: "" });
  const [hours, setHours] = useState({ morningOpen: "", morningClose: "", eveningOpen: "", eveningClose: "" });
  const [aartis, setAartis] = useState<{ name: string; at: string; description: string }[]>([]);
  const [calendar, setCalendar] = useState<LunarCalendar>("AMANTA");
  const [status, setStatus] = useState<Status>(null);
  const [note, setNote] = useState("");
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const [busy, setBusy] = useState(false);
  const [statusBusy, setStatusBusy] = useState(false);

  function apply(p: Profile) {
    setF({ deity: p.deity ?? "", address: p.address ?? "", helpline: p.helpline ?? "", timings: p.timings ?? "",
           announcement: p.announcement ?? "" });
    setHours({ morningOpen: hhmm(p.hours?.morningOpen), morningClose: hhmm(p.hours?.morningClose),
               eveningOpen: hhmm(p.hours?.eveningOpen), eveningClose: hhmm(p.hours?.eveningClose) });
    setAartis(p.aartis.map((a) => ({ name: a.name, at: hhmm(a.at), description: a.description ?? "" })));
    setCalendar(p.calendar);
    setStatus(p.status);
    setNote(p.statusNote ?? "");
  }

  useEffect(() => {
    api.get<Profile>("/temple").then(apply).catch((err) => setNotice({ tone: "danger", text: describeError(err) }));
  }, []);

  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setF({ ...f, [k]: e.target.value });
  const setHour = (k: keyof typeof hours) => (e: React.ChangeEvent<HTMLInputElement>) => setHours({ ...hours, [k]: e.target.value });
  const setAarti = (i: number, k: "name" | "at" | "description", v: string) =>
    setAartis(aartis.map((a, j) => (j === i ? { ...a, [k]: v } : a)));

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice(null);
    setFields({});
    try {
      const saved = await api.request<Profile>("/temple", {
        method: "PUT",
        json: {
          ...Object.fromEntries(Object.entries(f).map(([k, v]) => [k, orNull(v)])),
          hours: Object.fromEntries(Object.entries(hours).map(([k, v]) => [k, v || null])),
          aartis: aartis.filter((a) => a.name.trim() || a.at).map((a) => ({
            name: a.name.trim(), at: a.at || null, description: orNull(a.description),
          })),
          calendar,
        },
      });
      apply(saved);
      setNotice({ tone: "success", text: "Saved. It's live on your Mandir Center." });
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setNotice({ tone: "danger", text: describeError(err) });
    } finally {
      setBusy(false);
    }
  }

  async function saveStatus(next: Status) {
    setStatusBusy(true);
    setNotice(null);
    try {
      apply(await api.request<Profile>("/temple/status", { method: "PUT", json: { status: next, note: next ? orNull(note) : null } }));
      setNotice({ tone: "success", text: next ? `Today's status is set: ${next === "OPEN" ? "open" : "closed"}.` : "Back to the regular hours." });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    } finally {
      setStatusBusy(false);
    }
  }

  const segment = (active: boolean) => `rounded-[10px] px-3 py-1.5 text-sm font-medium transition-colors ${
    active ? "bg-primary text-on-primary" : "text-fg hover:bg-surface-2"}`;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Mandir Center"
        description="Your temple's public Mandir Center: what devotees see at its own address. Leave anything blank to hide it."
        actions={<a href="/" target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-primary-strong">Open Mandir Center ↗</a>}
      />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}

      <Card className="flex flex-col gap-3">
        <div>
          <h2 className="text-base font-semibold">Today&apos;s darshan status</h2>
          <p className="text-sm text-muted">
            Normally the site shows open or closed from the hours below. Override it for today only (for a grahan, a
            special utsav); it returns to the hours at midnight.
          </p>
        </div>
        <div className="inline-flex w-fit gap-1 rounded-[12px] border border-line p-1" role="group" aria-label="Today's status">
          {([[null, "Follow the hours"], ["OPEN", "Open today"], ["CLOSED", "Closed today"]] as const).map(([value, label]) => (
            <button key={label} type="button" aria-pressed={status === value} disabled={!canEdit || statusBusy}
                    className={segment(status === value)} onClick={() => void saveStatus(value)}>
              {label}
            </button>
          ))}
        </div>
        <div className="flex max-w-xl items-end gap-2">
          <div className="flex-1">
            <TextField label="Note for devotees (optional)" placeholder="e.g. Closed for the lunar eclipse; reopens 5:30 am"
                       value={note} maxLength={120} onChange={(e) => setNote(e.target.value)} disabled={!canEdit} />
          </div>
          {status ? <Button variant="secondary" busy={statusBusy} disabled={!canEdit} onClick={() => void saveStatus(status)}>Update note</Button> : null}
        </div>
      </Card>

      <form onSubmit={onSave} className="flex flex-col gap-6">
        <Card className="flex flex-col gap-3">
          <h2 className="text-base font-semibold">Temple</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label="Presiding deity" value={f.deity} onChange={set("deity")} disabled={!canEdit} />
            <TextField label="Helpline" inputMode="tel" value={f.helpline} onChange={set("helpline")} error={fields.helpline}
                       hint="A mobile number devotees can call" disabled={!canEdit} />
          </div>
          <TextField label="Address" value={f.address} onChange={set("address")} disabled={!canEdit} />
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">Announcement</span>
            <textarea rows={3} maxLength={1000} value={f.announcement} onChange={set("announcement")} disabled={!canEdit}
                      className="rounded-[10px] border border-line bg-surface px-3 py-2" />
          </label>
          <label className="flex max-w-xl flex-col gap-1 text-sm">
            <span className="font-medium">Lunar calendar</span>
            <select value={calendar} onChange={(e) => setCalendar(e.target.value as LunarCalendar)} disabled={!canEdit}
                    className="rounded-[10px] border border-line bg-surface px-3 py-2">
              <option value="AMANTA">Amanta: months end on amavasya (Maharashtra, Gujarat, South)</option>
              <option value="PURNIMANTA">Purnimanta: months end on purnima (North India)</option>
            </select>
            <span className="text-xs text-muted">Used to name the month in the daily panchang.</span>
          </label>
        </Card>

        <Card className="flex flex-col gap-3">
          <div>
            <h2 className="text-base font-semibold">Darshan hours</h2>
            <p className="text-sm text-muted">The site shows &ldquo;open now&rdquo; or &ldquo;opens at&rdquo; from these. Leave a session blank if there isn&apos;t one.</p>
          </div>
          {fields.hours ? <Alert tone="danger">{fields.hours}</Alert> : null}
          <div className="grid gap-3 sm:grid-cols-4">
            <TextField label="Morning opens" type="time" value={hours.morningOpen} onChange={setHour("morningOpen")} disabled={!canEdit} />
            <TextField label="Morning closes" type="time" value={hours.morningClose} onChange={setHour("morningClose")} disabled={!canEdit} />
            <TextField label="Evening opens" type="time" value={hours.eveningOpen} onChange={setHour("eveningOpen")} disabled={!canEdit} />
            <TextField label="Evening closes" type="time" value={hours.eveningClose} onChange={setHour("eveningClose")} disabled={!canEdit} />
          </div>
          <TextField label="Timing notes (optional)" placeholder="e.g. Mondays open till 10:30 pm" value={f.timings}
                     onChange={set("timings")} disabled={!canEdit} />
        </Card>

        <Card className="flex flex-col gap-3">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold">Daily aarti</h2>
              <p className="text-sm text-muted">Shown in time order on the temple site; the next one is highlighted.</p>
            </div>
            {canEdit && aartis.length < MAX_AARTIS ? (
              <Button type="button" variant="secondary" onClick={() => setAartis([...aartis, { name: "", at: "", description: "" }])}>
                Add aarti
              </Button>
            ) : null}
          </div>
          {fields.aartis ? <Alert tone="danger">{fields.aartis}</Alert> : null}
          {aartis.length === 0 ? <p className="text-sm text-muted">No aartis yet.</p> : null}
          <ol className="flex flex-col gap-3">
            {aartis.map((a, i) => (
              <li key={i} className="grid items-end gap-2 rounded-[10px] border border-line p-3 sm:grid-cols-[1fr_8rem_2fr_auto]">
                <TextField label="Name" placeholder="Kakad Aarti" value={a.name} maxLength={60}
                           onChange={(e) => setAarti(i, "name", e.target.value)} disabled={!canEdit} />
                <TextField label="Time" type="time" value={a.at} onChange={(e) => setAarti(i, "at", e.target.value)} disabled={!canEdit} />
                <TextField label="About (optional)" value={a.description} maxLength={200}
                           onChange={(e) => setAarti(i, "description", e.target.value)} disabled={!canEdit} />
                {canEdit ? (
                  <Button type="button" variant="secondary" aria-label={`Remove ${a.name || "aarti"}`}
                          onClick={() => setAartis(aartis.filter((_, j) => j !== i))}>Remove</Button>
                ) : null}
              </li>
            ))}
          </ol>
        </Card>

        {canEdit ? <div><Button type="submit" busy={busy}>Save Mandir Center</Button></div>
          : <p className="text-sm text-muted">Only leaders can edit the Mandir Center.</p>}
      </form>
    </div>
  );
}
