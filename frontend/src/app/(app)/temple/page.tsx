"use client";

import { useEffect, useRef, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { useMandirHref } from "@/components/SiteProvider";
import { Alert, Button, Card, PageHeader, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import type { LunarCalendar } from "@/lib/panchang";
import { templeImageUrl } from "@/lib/temple";
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
  about: string | null;
  history: string | null;
  imageVersion: string | null;
}

const MAX_SIDE = 1600;

/**
 * Shrinks a phone photo in the browser before upload (the server limit is 2 MB). The server
 * decodes and re-encodes whatever arrives anyway; this only keeps uploads small and fast.
 */
async function shrink(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("canvas unavailable");
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("encode failed"))), "image/jpeg", 0.85));
}

const MAX_AARTIS = 12;
const hhmm = (t: string | null | undefined) => (t ? t.slice(0, 5) : "");
const orNull = (v: string) => v.trim() || null;

/** Edit the public temple page (ADR 0017, 0024). LEADER+; everything optional. */
export default function TempleSettingsPage() {
  const me = useMe();
  const mandirHref = useMandirHref("/");
  const canEdit = hasRole(me.role, "LEADER");
  const [f, setF] = useState({ deity: "", address: "", helpline: "", timings: "", announcement: "", about: "", history: "" });
  const [imageVersion, setImageVersion] = useState<string | null>(null);
  const [imageBusy, setImageBusy] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
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
           announcement: p.announcement ?? "", about: p.about ?? "", history: p.history ?? "" });
    setImageVersion(p.imageVersion);
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

  async function onPhoto(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setImageBusy(true);
    setNotice(null);
    try {
      let blob: Blob;
      try {
        blob = await shrink(file);
      } catch {
        throw new Error("This photo couldn't be opened here. Save it as JPEG or PNG and try again.");
      }
      const form = new FormData();
      form.append("image", blob, "temple.jpg");
      const r = await api.request<{ imageVersion: string }>("/temple/image", { method: "PUT", multipart: form });
      setImageVersion(r.imageVersion);
      setNotice({ tone: "success", text: "Photo updated. It's live on your Mandir Center." });
    } catch (err) {
      setNotice({ tone: "danger", text: err instanceof ApiError ? describeError(err) : (err as Error).message });
    } finally {
      setImageBusy(false);
    }
  }

  async function removePhoto() {
    setImageBusy(true);
    setNotice(null);
    try {
      await api.request("/temple/image", { method: "DELETE" });
      setImageVersion(null);
      setNotice({ tone: "success", text: "Photo removed." });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    } finally {
      setImageBusy(false);
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
        actions={<a href={mandirHref} target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-primary-strong">Open Mandir Center ↗</a>}
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

      <Card className="flex flex-col gap-3">
        <div>
          <h2 className="text-base font-semibold">Temple photo</h2>
          <p className="text-sm text-muted">
            Shown at the top of your Mandir Center. A wide photo of the temple works best. PNG or JPEG; it is resized and
            any location data in the photo is removed before it&apos;s published.
          </p>
        </div>
        {imageVersion ? (
          // eslint-disable-next-line @next/next/no-img-element -- our own re-encoded JPEG
          <img src={templeImageUrl(imageVersion)} alt="Current temple photo"
               className="aspect-[16/9] w-full max-w-xl rounded-[12px] border border-line object-cover" />
        ) : <p className="text-sm text-muted">No photo yet.</p>}
        {canEdit ? (
          <div className="flex gap-2">
            <input ref={fileInput} type="file" accept="image/png,image/jpeg" className="hidden" onChange={(e) => void onPhoto(e)} />
            <Button type="button" variant="secondary" busy={imageBusy} onClick={() => fileInput.current?.click()}>
              {imageVersion ? "Replace photo" : "Upload photo"}
            </Button>
            {imageVersion ? (
              <Button type="button" variant="secondary" disabled={imageBusy} onClick={() => void removePhoto()}>Remove</Button>
            ) : null}
          </div>
        ) : null}
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
            <h2 className="text-base font-semibold">About and history</h2>
            <p className="text-sm text-muted">Shown on your Mandir Center as written. Plain text: line breaks are kept.</p>
          </div>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">About the temple</span>
            <textarea rows={5} maxLength={4000} value={f.about} onChange={set("about")} disabled={!canEdit}
                      placeholder="Who the temple is dedicated to, what devotees can expect, festivals it is known for"
                      className="rounded-[10px] border border-line bg-surface px-3 py-2" />
            <span className="text-xs text-muted">{f.about.length} / 4000</span>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">History</span>
            <textarea rows={7} maxLength={8000} value={f.history} onChange={set("history")} disabled={!canEdit}
                      placeholder="When and by whom it was founded, renovations, stories devotees should know"
                      className="rounded-[10px] border border-line bg-surface px-3 py-2" />
            <span className="text-xs text-muted">{f.history.length} / 8000</span>
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
