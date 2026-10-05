"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { IssuePassDialog } from "@/components/StaffDialogs";
import { ConfirmDialog, EmptyState, Pill, StaffTitle, StatCard, useView, ViewSwitcher } from "@/components/staff";
import { Alert, Button, Card, Dialog, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import {
  dateChip, formatIst, formatPassCode, istFromLocalInput, localInputFromIst, type EventPass, type StaffEvent,
} from "@/lib/events";
import { hasRole } from "@/lib/types";

const STATUS = {
  DRAFT: { tone: "neutral", label: "Draft" },
  PUBLISHED: { tone: "success", label: "Published" },
  CANCELLED: { tone: "danger", label: "Cancelled" },
} as const;
const VIEWS = ["gate", "festivals", "roster"] as const;

/**
 * Utsavs & passes (ADR 0014, 0026). Everyone on staff checks passes in at the gate and can issue a
 * walk-in pass. Leaders create, edit, publish and cancel utsavs, delete drafts and cancelled ones
 * (ADR 0029), and see the roster with contacts.
 */
export default function EventsPage() {
  const me = useMe();
  const isLeader = hasRole(me.role, "LEADER");
  const view = useView(VIEWS);
  const [events, setEvents] = useState<StaffEvent[] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [editing, setEditing] = useState<StaffEvent | "new" | null>(null);
  const [issuing, setIssuing] = useState(false);
  const [cancelling, setCancelling] = useState<StaffEvent | null>(null);
  const [deleting, setDeleting] = useState<StaffEvent | null>(null);
  const [checkIns, setCheckIns] = useState(0);
  // Fixed per visit: what counts as "live" shouldn't shift while staff are working the gate.
  const [now] = useState(() => Date.now());

  const load = useCallback(async () => {
    try {
      setEvents(await api.get<StaffEvent[]>("/events"));
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load
    void load();
  }, [load]);

  // Gate and roster work on live utsavs: published and not over, soonest first.
  const live = (events ?? []).filter((e) => e.status === "PUBLISHED" && Date.parse(e.endsAt) > now)
    .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt));
  const forRoster = (events ?? []).filter((e) => e.status !== "DRAFT")
    .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt));
  const pool = view === "gate" ? live : forRoster;
  const current = pool.find((e) => e.id === selected) ?? pool.find((e) => Date.parse(e.endsAt) > now) ?? pool[0] ?? null;

  async function changeStatus(e: StaffEvent, action: "publish" | "cancel") {
    setNotice(null);
    try {
      await api.request(`/events/${e.id}/${action}`, { method: "POST" });
      setNotice({ tone: "success", text: action === "publish" ? `“${e.title}” is now on the Mandir Center.` : `“${e.title}” was cancelled.` });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
    await load();
  }

  async function remove(e: StaffEvent) {
    setNotice(null);
    try {
      await api.request(`/events/${e.id}`, { method: "DELETE" });
      setNotice({ tone: "success", text: `“${e.title}” was deleted.` });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
    await load();
  }

  const picker = pool.length > 0 && current ? (
    <label className="flex flex-wrap items-center gap-2 text-sm font-semibold">
      Utsav
      <select value={current.id} onChange={(e) => setSelected(Number(e.target.value))}
              className="min-w-[16rem] rounded-[10px] border border-line bg-surface px-3 py-1.5 text-sm font-normal">
        {pool.map((e) => <option key={e.id} value={e.id}>{e.title} ({formatIst(e.startsAt).split(",").slice(0, 2).join(",")})</option>)}
      </select>
    </label>
  ) : null;

  return (
    <div className="flex flex-col gap-6">
      <StaffTitle
        title="Utsavs & Gate Pass Check-in"
        pill="Utsavs & Darshan"
        description="Check devotees in at the temple gate by pass code, issue passes to walk-ins, and publish upcoming utsavs."
        views={<ViewSwitcher label="Utsav views" current={view} views={[
          { id: "gate", label: "Gate Check-in" }, { id: "festivals", label: "Festivals & Passes" },
          ...(isLeader ? [{ id: "roster" as const, label: "Attendee Roster" }] : [])]} />}
      />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}

      {view === "gate" ? (
        live.length === 0 || !current ? (
          <EmptyState title="No utsav is open at the gate right now.">Publish an utsav under Festivals &amp; Passes.</EmptyState>
        ) : (
          <div className="grid items-start gap-6 lg:grid-cols-[22rem_1fr]">
            <GateCard event={current} onCheckedIn={() => setCheckIns((n) => n + 1)} />
            <div className="flex flex-col gap-4">
              <Card className="flex flex-wrap items-center justify-between gap-3 py-3">
                {picker}
                <Button variant="secondary" onClick={() => setIssuing(true)}>+ Issue walk-in pass</Button>
              </Card>
              <div className="grid gap-3 sm:grid-cols-3">
                <StatCard label="Registered" tone="primary" value={current.seatsTaken}
                          note={current.capacity !== null ? `of ${current.capacity} places` : "no limit"} />
                <StatCard label="Starts" tone="maroon" value={dateChip(current.startsAt).day}
                          note={formatIst(current.startsAt)} />
                <StatCard label="Registration" tone={current.registrationOpen ? "success" : "warning"}
                          value={current.registrationOpen ? "Open" : "Closed"} note="Gate passes still allowed" />
              </div>
              {isLeader ? <PassList event={current} compact refresh={checkIns} onChanged={() => void load()} /> : null}
            </div>
          </div>
        )
      ) : view === "festivals" ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-muted">Utsavs and holy occasions. Drafts stay private until you publish them on the Mandir Center.</p>
            {isLeader ? <Button onClick={() => setEditing("new")}>+ Create utsav</Button> : null}
          </div>
          {events === null ? null : events.length === 0 ? <EmptyState title="No utsavs yet." /> : (
            <div className="grid gap-4 md:grid-cols-2">
              {[...events].sort((a, b) => Date.parse(b.startsAt) - Date.parse(a.startsAt)).map((e) => {
                const chip = dateChip(e.startsAt);
                const ended = Date.parse(e.endsAt) <= now;
                const full = e.capacity !== null ? Math.min(100, Math.round((e.seatsTaken / e.capacity) * 100)) : null;
                return (
                  <Card key={e.id} className={`flex flex-col gap-3 ${e.status === "CANCELLED" || ended ? "opacity-70" : ""}`}>
                    <div className="flex items-start gap-4">
                      <div className="flex size-14 shrink-0 flex-col items-center justify-center rounded-[12px] border border-primary/25 bg-primary/10 text-primary-strong">
                        <span className="text-xl font-semibold leading-none">{chip.day}</span>
                        <span className="text-[11px] font-semibold uppercase">{chip.month}</span>
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-2">
                          <h2 className="text-lg font-semibold">{e.title}</h2>
                          {ended && e.status === "PUBLISHED" ? <Pill>Ended</Pill>
                            : <Pill tone={STATUS[e.status].tone}>{STATUS[e.status].label}</Pill>}
                        </div>
                        <p className="font-mono text-xs text-muted">{formatIst(e.startsAt)} – {formatIst(e.endsAt).split(", ").pop()}</p>
                      </div>
                    </div>
                    {e.description ? <p className="rounded-[10px] bg-surface-2 p-3 text-sm">{e.description}</p> : null}
                    <div>
                      <div className="mb-1 flex justify-between text-xs text-muted">
                        <span>{e.seatsTaken} registered{e.capacity !== null ? ` of ${e.capacity}` : ""}</span>
                        <span>{e.registrationOpen ? "Passes open" : "Passes closed"}</span>
                      </div>
                      {full !== null ? (
                        <div className="h-1.5 overflow-hidden rounded-full bg-surface-2">
                          <div className="h-full rounded-full bg-primary" style={{ width: `${full}%` }} />
                        </div>
                      ) : null}
                    </div>
                    {isLeader && e.status !== "CANCELLED" && !ended ? (
                      <div className="flex flex-wrap justify-end gap-2 border-t border-line pt-3">
                        {e.status === "DRAFT" ? <Button variant="ghost" className="text-danger" onClick={() => setDeleting(e)}>Delete</Button> : null}
                        <Button variant="secondary" onClick={() => setEditing(e)}>Edit</Button>
                        {e.status === "DRAFT" ? <Button onClick={() => void changeStatus(e, "publish")}>Publish</Button> : null}
                        {e.status === "PUBLISHED" ? <Button variant="danger" onClick={() => setCancelling(e)}>Cancel utsav</Button> : null}
                      </div>
                    ) : isLeader && e.status !== "PUBLISHED" ? (
                      <div className="flex flex-wrap justify-end gap-2 border-t border-line pt-3">
                        <Button variant="ghost" className="text-danger" onClick={() => setDeleting(e)}>Delete</Button>
                      </div>
                    ) : null}
                  </Card>
                );
              })}
            </div>
          )}
        </>
      ) : !isLeader ? null : forRoster.length === 0 || !current ? (
        <EmptyState title="No published utsavs yet." />
      ) : (
        <>
          <Card className="flex flex-wrap items-center justify-between gap-3 py-3">
            {picker}
            {Date.parse(current.endsAt) > now && current.status === "PUBLISHED"
              ? <Button variant="secondary" onClick={() => setIssuing(true)}>+ Issue pass</Button> : null}
          </Card>
          <PassList key={current.id} event={current} onChanged={() => void load()} />
        </>
      )}

      {isLeader && editing ? (
        <EventDialog event={editing === "new" ? null : editing} onClose={() => setEditing(null)}
                     onSaved={(t) => { setEditing(null); setNotice({ tone: "success", text: `“${t}” saved.` }); void load(); }} />
      ) : null}
      {issuing && current ? (
        <IssuePassDialog event={current} onClose={() => setIssuing(false)}
                         onIssued={(p) => { setIssuing(false); setNotice({ tone: "success",
                           text: `Pass ${formatPassCode(p.passCode)} issued to ${p.attendeeName} (${p.attendeeCount}).` }); void load(); }} />
      ) : null}
      <ConfirmDialog open={cancelling !== null} title="Cancel this utsav?" confirm="Cancel utsav"
                     onClose={() => setCancelling(null)}
                     onConfirm={() => { const e = cancelling; setCancelling(null); if (e) void changeStatus(e, "cancel"); }}>
        {cancelling ? <p>“{cancelling.title}” will be taken off the Mandir Center and its {cancelling.seatsTaken} registered
          {cancelling.seatsTaken === 1 ? " person" : " people"} can no longer use their passes.</p> : null}
      </ConfirmDialog>
      <ConfirmDialog open={deleting !== null} title="Delete this utsav?" confirm="Delete utsav"
                     onClose={() => setDeleting(null)}
                     onConfirm={() => { const e = deleting; setDeleting(null); if (e) void remove(e); }}>
        {deleting ? <p>“{deleting.title}” will be removed from your utsav list.
          {deleting.status === "CANCELLED" && deleting.seatsTaken > 0
            ? " Devotees who registered keep their cancelled pass in My Mandir, and the audit trail keeps a record." : ""}</p> : null}
      </ConfirmDialog>
    </div>
  );
}

function GateCard({ event, onCheckedIn }: { event: StaffEvent; onCheckedIn: () => void }) {
  const [code, setCode] = useState("");
  const [result, setResult] = useState<{ tone: "success" | "danger" | "warning"; title: string; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setResult(null);
    try {
      const r = await api.request<{ attendeeName: string; attendeeCount: number }>(`/events/${event.id}/check-in`, {
        method: "POST", json: { passCode: code },
      });
      setResult({ tone: "success", title: `Welcome, ${r.attendeeName}`,
                  text: `${r.attendeeCount} ${r.attendeeCount === 1 ? "person" : "people"} · ${event.title}` });
      setCode("");
      onCheckedIn();
    } catch (err) {
      if (err instanceof ApiError && err.code === "already_checked_in") {
        setResult({ tone: "warning", title: "Already used", text: "This pass was checked in earlier." });
      } else if (err instanceof ApiError && err.status === 404) {
        setResult({ tone: "danger", title: "Not valid", text: `No such pass for ${event.title}.` });
      } else {
        setResult({ tone: "danger", title: "Couldn't check in", text: describeError(err) });
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-semibold">Gate entry check-in</h2>
        <p className="text-sm text-muted">Enter the devotee&apos;s pass code for <strong>{event.title}</strong>.</p>
      </div>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <label htmlFor="pass-code" className="font-mono text-sm font-semibold">Pass code</label>
        <input id="pass-code" value={code} onChange={(e) => setCode(e.target.value)} autoComplete="off" autoFocus
               placeholder="ABCDE-23456"
               className="rounded-[10px] border-2 border-primary/60 bg-surface px-4 py-3 font-mono text-lg tracking-widest focus:border-primary focus:outline-none" />
        <Button type="submit" busy={busy} disabled={!code.trim()} className="py-3 text-base">✓ Verify &amp; check in</Button>
      </form>
      {result ? (
        <div role="status" className={`rounded-[12px] border p-4 ${
          result.tone === "success" ? "border-success/40 bg-success/10" : result.tone === "warning" ? "border-warning/40 bg-warning/10"
            : "border-danger/40 bg-danger/10"}`}>
          <p className={`font-semibold ${result.tone === "success" ? "text-success" : result.tone === "warning" ? "text-warning" : "text-danger"}`}>
            {result.title}
          </p>
          <p className="text-sm">{result.text}</p>
        </div>
      ) : null}
    </Card>
  );
}

function PassList({ event, compact, refresh = 0, onChanged }: {
  event: StaffEvent; compact?: boolean; refresh?: number; onChanged: () => void;
}) {
  const [passes, setPasses] = useState<EventPass[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<EventPass | null>(null);

  const load = useCallback(async () => {
    try {
      setPasses(await api.get<EventPass[]>(`/events/${event.id}/passes`));
    } catch (err) {
      setError(describeError(err));
    }
  }, [event.id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- load on open / utsav change
    void load();
  }, [load, event.seatsTaken, refresh]);

  async function cancel(p: EventPass) {
    try {
      await api.request(`/events/${event.id}/passes/${p.id}/cancel`, { method: "POST" });
      await load();
      onChanged();
    } catch (err) {
      setError(describeError(err));
    }
  }

  const active = passes?.filter((p) => p.status === "ACTIVE") ?? [];
  const people = active.reduce((n, p) => n + p.attendeeCount, 0);
  const inside = active.filter((p) => p.checkedInAt).reduce((n, p) => n + p.attendeeCount, 0);
  const shown = compact ? passes?.slice(0, 8) : passes;

  return (
    <div className="flex flex-col gap-4">
      {!compact ? (
        <div className="grid gap-3 sm:grid-cols-4">
          <StatCard label="Passes" tone="primary" value={active.length} />
          <StatCard label="People" tone="info" value={people} note={event.capacity !== null ? `of ${event.capacity}` : undefined} />
          <StatCard label="Checked in" tone="success" value={inside} note={people ? `${Math.round((inside / people) * 100)}%` : undefined} />
          <StatCard label="Cancelled" tone="neutral" value={(passes?.length ?? 0) - active.length} />
        </div>
      ) : null}
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <Card className="overflow-x-auto p-0">
        {compact ? <p className="border-b border-line px-4 py-3 text-sm font-semibold">Latest passes</p> : null}
        {passes?.length === 0 ? <p className="px-4 py-6 text-sm text-muted">No passes issued yet.</p> : (
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-2 text-xs uppercase tracking-wider text-muted">
              <tr><th className="px-4 py-2">Pass</th><th className="px-4 py-2">Name</th><th className="px-4 py-2">People</th>
                {!compact ? <th className="px-4 py-2">Contact</th> : null}<th className="px-4 py-2">Status</th><th /></tr>
            </thead>
            <tbody>
              {shown?.map((p) => (
                <tr key={p.id} className="border-t border-line">
                  <td className="px-4 py-2 font-mono">{formatPassCode(p.passCode)}</td>
                  <td className="px-4 py-2 font-medium">{p.attendeeName}</td>
                  <td className="px-4 py-2">{p.attendeeCount}</td>
                  {!compact ? <td className="px-4 py-2 text-muted">{p.phone ?? p.email ?? "Walk-in"}</td> : null}
                  <td className="px-4 py-2">
                    {p.status === "CANCELLED" ? <Pill>Cancelled</Pill>
                      : p.checkedInAt ? <Pill tone="success">In · {formatIst(p.checkedInAt).split(", ").pop()}</Pill>
                      : <Pill tone="primary">Registered</Pill>}
                  </td>
                  <td className="px-4 py-2 text-right">
                    {!compact && p.status === "ACTIVE" && !p.checkedInAt
                      ? <Button variant="ghost" className="text-danger" onClick={() => setCancelling(p)}>Cancel</Button> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      <ConfirmDialog open={cancelling !== null} title="Cancel this pass?" confirm="Cancel pass" onClose={() => setCancelling(null)}
                     onConfirm={() => { const p = cancelling; setCancelling(null); if (p) void cancel(p); }}>
        {cancelling ? <p>Pass {formatPassCode(cancelling.passCode)} for {cancelling.attendeeName} ({cancelling.attendeeCount}) will
          stop working at the gate and its places will be freed.</p> : null}
      </ConfirmDialog>
    </div>
  );
}

function EventDialog({ event, onClose, onSaved }: { event: StaffEvent | null; onClose: () => void; onSaved: (title: string) => void }) {
  const [f, setF] = useState({
    title: event?.title ?? "", description: event?.description ?? "",
    startsAt: event ? localInputFromIst(event.startsAt) : "", endsAt: event ? localInputFromIst(event.endsAt) : "",
    capacity: event?.capacity != null ? String(event.capacity) : "", registrationOpen: event?.registrationOpen ?? true,
  });
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setFields({});
    const start = istFromLocalInput(f.startsAt);
    const end = istFromLocalInput(f.endsAt);
    if (!start || !end) {
      setError("Choose a start and an end time.");
      return;
    }
    setBusy(true);
    try {
      await api.request(event ? `/events/${event.id}` : "/events", {
        method: event ? "PUT" : "POST",
        json: { title: f.title.trim(), description: f.description.trim() || null, startsAt: start, endsAt: end,
                capacity: f.capacity.trim() ? Number(f.capacity) : null, registrationOpen: f.registrationOpen },
      });
      onSaved(f.title.trim());
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={event ? `Edit ${event.title}` : "Create an utsav"}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField label="Title" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} error={fields.title} required />
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">Description and highlights (optional)</span>
          <textarea rows={3} maxLength={2000} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })}
                    className="rounded-[10px] border border-line bg-surface px-3 py-2" />
        </label>
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Starts (IST)" type="datetime-local" value={f.startsAt} onChange={(e) => setF({ ...f, startsAt: e.target.value })}
                     error={fields.startsAt} required />
          <TextField label="Ends (IST)" type="datetime-local" value={f.endsAt} onChange={(e) => setF({ ...f, endsAt: e.target.value })}
                     error={fields.endsAt} required />
        </div>
        <TextField label="Capacity (people, optional)" inputMode="numeric" value={f.capacity}
                   onChange={(e) => setF({ ...f, capacity: e.target.value.replace(/\D/g, "") })} error={fields.capacity} />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={f.registrationOpen} onChange={(e) => setF({ ...f, registrationOpen: e.target.checked })} />
          Devotees can register for passes on the Mandir Center
        </label>
        {!event ? <p className="text-xs text-muted">Saved as a draft. Publish it when you&apos;re ready.</p> : null}
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>{event ? "Save" : "Create draft"}</Button>
        </div>
      </form>
    </Dialog>
  );
}
