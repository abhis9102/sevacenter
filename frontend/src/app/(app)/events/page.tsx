"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Badge, Button, Card, Dialog, PageHeader, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatIst, formatPassCode, istFromLocalInput, type EventPass, type StaffEvent } from "@/lib/events";
import { hasRole } from "@/lib/types";

const STATUS_TONE = { DRAFT: "neutral", PUBLISHED: "success", CANCELLED: "danger" } as const;

/**
 * Events (ADR 0014). Everyone on staff sees events and can check passes in at the gate (name and
 * head count only). Leaders create, publish and cancel events and see registrations with contacts.
 */
export default function EventsPage() {
  const me = useMe();
  const isLeader = hasRole(me.role, "LEADER");
  const [events, setEvents] = useState<StaffEvent[] | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [creating, setCreating] = useState(false);
  const [passesFor, setPassesFor] = useState<StaffEvent | null>(null);
  const [gateFor, setGateFor] = useState<StaffEvent | null>(null);

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

  async function changeStatus(e: StaffEvent, action: "publish" | "cancel") {
    setNotice(null);
    try {
      await api.request(`/events/${e.id}/${action}`, { method: "POST" });
      setNotice({ tone: "success", text: action === "publish" ? `“${e.title}” is now public.` : `“${e.title}” was cancelled.` });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
    await load();
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Events"
        description="Festivals, darshan slots and satsangs. Devotees register on the temple's public events page."
        actions={isLeader ? <Button onClick={() => setCreating(true)}>New event</Button> : null}
      />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}

      {events?.length === 0 ? <Card><p className="text-muted">No events yet.</p></Card> : null}
      <div className="grid gap-4 md:grid-cols-2">
        {events?.map((e) => (
          <Card key={e.id} className="flex flex-col gap-2">
            <div className="flex items-start justify-between gap-2">
              <h2 className="font-medium">{e.title}</h2>
              <Badge tone={STATUS_TONE[e.status]}>{e.status.toLowerCase()}</Badge>
            </div>
            <p className="text-sm text-muted">{formatIst(e.startsAt)} – {formatIst(e.endsAt)}</p>
            <p className="text-sm">
              {e.seatsTaken} registered{e.capacity !== null ? ` of ${e.capacity}` : ""}
              {!e.registrationOpen ? " · registration closed" : ""}
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
              {e.status === "PUBLISHED" ? (
                <Button variant="secondary" onClick={() => setGateFor(e)}>Gate check-in</Button>
              ) : null}
              {isLeader && e.status === "DRAFT" ? (
                <Button onClick={() => void changeStatus(e, "publish")}>Publish</Button>
              ) : null}
              {isLeader ? <Button variant="secondary" onClick={() => setPassesFor(e)}>Registrations</Button> : null}
              {isLeader && e.status !== "CANCELLED" ? (
                <Button variant="ghost" className="text-danger" onClick={() => void changeStatus(e, "cancel")}>Cancel event</Button>
              ) : null}
            </div>
          </Card>
        ))}
      </div>

      {isLeader ? (
        <CreateEventDialog open={creating} onClose={() => setCreating(false)}
                           onCreated={() => { setCreating(false); void load(); }} />
      ) : null}
      {passesFor ? <PassesDialog event={passesFor} onClose={() => setPassesFor(null)} /> : null}
      {gateFor ? <GateDialog event={gateFor} onClose={() => { setGateFor(null); void load(); }} /> : null}
    </div>
  );
}

function CreateEventDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [capacity, setCapacity] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setFields({});
    const start = istFromLocalInput(startsAt);
    const end = istFromLocalInput(endsAt);
    if (!start || !end) {
      setError("Choose a start and an end time.");
      return;
    }
    setBusy(true);
    try {
      await api.request("/events", {
        method: "POST",
        json: {
          title: title.trim(),
          description: description.trim() || null,
          startsAt: start,
          endsAt: end,
          capacity: capacity.trim() ? Number(capacity) : null,
          registrationOpen: true,
        },
      });
      setTitle(""); setDescription(""); setStartsAt(""); setEndsAt(""); setCapacity("");
      onCreated();
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title="New event">
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} error={fields.title} required />
        <TextField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        <TextField label="Starts (IST)" type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)}
                   error={fields.startsAt} required />
        <TextField label="Ends (IST)" type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)}
                   error={fields.endsAt} required />
        <TextField label="Capacity (people, optional)" inputMode="numeric" value={capacity}
                   onChange={(e) => setCapacity(e.target.value.replace(/\D/g, ""))} error={fields.capacity} />
        <p className="text-xs text-muted">Saved as a draft. Publish it when you&apos;re ready for registrations.</p>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Create draft</Button>
        </div>
      </form>
    </Dialog>
  );
}

function PassesDialog({ event, onClose }: { event: StaffEvent; onClose: () => void }) {
  const [passes, setPasses] = useState<EventPass[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPasses(await api.get<EventPass[]>(`/events/${event.id}/passes`));
    } catch (err) {
      setError(describeError(err));
    }
  }, [event.id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- load on open
    void load();
  }, [load]);

  async function cancel(p: EventPass) {
    try {
      await api.request(`/events/${event.id}/passes/${p.id}/cancel`, { method: "POST" });
      await load();
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <Dialog open onClose={onClose} title={`Registrations · ${event.title}`}>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <div className="max-h-[60vh] overflow-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase text-muted">
            <tr><th className="py-2">Pass</th><th>Name</th><th>People</th><th>Contact</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {passes?.map((p) => (
              <tr key={p.id} className="border-t border-line">
                <td className="py-2 font-mono">{formatPassCode(p.passCode)}</td>
                <td>{p.attendeeName}</td>
                <td>{p.attendeeCount}</td>
                <td className="text-muted">{p.phone ?? p.email}</td>
                <td>{p.status === "CANCELLED" ? "cancelled" : p.checkedInAt ? `in · ${formatIst(p.checkedInAt)}` : "registered"}</td>
                <td>
                  {p.status === "ACTIVE" && !p.checkedInAt ? (
                    <Button variant="ghost" className="text-danger" onClick={() => void cancel(p)}>Cancel</Button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {passes?.length === 0 ? <p className="py-4 text-muted">No registrations yet.</p> : null}
      </div>
    </Dialog>
  );
}

function GateDialog({ event, onClose }: { event: StaffEvent; onClose: () => void }) {
  const [code, setCode] = useState("");
  const [result, setResult] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setResult(null);
    try {
      const r = await api.request<{ attendeeName: string; attendeeCount: number }>(`/events/${event.id}/check-in`, {
        method: "POST",
        json: { passCode: code },
      });
      setResult({ tone: "success", text: `✓ ${r.attendeeName}: ${r.attendeeCount} ${r.attendeeCount === 1 ? "person" : "people"}. Welcome!` });
      setCode("");
    } catch (err) {
      if (err instanceof ApiError && err.code === "already_checked_in") {
        setResult({ tone: "warning", text: "Already used: this pass was checked in earlier." });
      } else if (err instanceof ApiError && err.status === 404) {
        setResult({ tone: "danger", text: "No such pass for this event." });
      } else {
        setResult({ tone: "danger", text: describeError(err) });
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={`Gate check-in · ${event.title}`}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField label="Pass code" value={code} onChange={(e) => setCode(e.target.value)} autoComplete="off"
                   placeholder="ABCDE-23456" autoFocus />
        {result ? <Alert tone={result.tone}>{result.text}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Close</Button>
          <Button type="submit" busy={busy} disabled={!code.trim()}>Check in</Button>
        </div>
      </form>
    </Dialog>
  );
}
