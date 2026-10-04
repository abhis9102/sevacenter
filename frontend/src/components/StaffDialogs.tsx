"use client";

import { useState } from "react";

import { api } from "@/components/apiClient";
import { Alert, Button, Dialog, SelectField, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatIst, type EventPass, type StaffEvent } from "@/lib/events";
import { isFree, todayIst, type Puja, type PujaBooking } from "@/lib/pujas";
import type { Team } from "@/lib/sevak";

/**
 * Staff actions for a devotee standing in front of them (ADR 0026, 0028): book a puja at the
 * counter, issue a darshan pass, register them as a sevak. Used by the module screens and, pre-filled,
 * by the devotee's own page.
 */

const MODES = [
  { value: "CASH", label: "Cash" }, { value: "UPI", label: "UPI" }, { value: "CARD", label: "Card" },
  { value: "CHEQUE", label: "Cheque" }, { value: "BANK_TRANSFER", label: "Bank transfer" },
];
const rupees = (r: string) => `₹${Number(r).toLocaleString("en-IN")}`;

export function CounterBookingDialog({ catalog, date, initial, onClose, onBooked }: {
  catalog: Puja[]; date: string; initial?: Partial<Record<"devoteeName" | "phone" | "email", string>>;
  onClose: () => void; onBooked: (b: PujaBooking) => void;
}) {
  const [f, setF] = useState({ pujaId: String(catalog[0]?.id ?? ""), pujaDate: date < todayIst() ? todayIst() : date,
    devoteeName: initial?.devoteeName ?? "", gotra: "", nakshatra: "", rashi: "", familyNames: "", phone: initial?.phone ?? "",
    email: initial?.email ?? "", mode: "CASH", reference: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  const puja = catalog.find((p) => String(p.id) === f.pujaId);
  const paid = puja ? !isFree(puja.dakshina) : false;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      const trimmed = Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v.trim() || null]));
      onBooked(await api.request<PujaBooking>("/puja-bookings", { method: "POST", json: {
        ...trimmed, pujaId: Number(f.pujaId), mode: paid ? f.mode : null, reference: paid ? trimmed.reference : null } }));
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title="Book a puja for a devotee">
      {catalog.length === 0 ? <p className="text-sm">Add a puja to the catalog first.</p> : (
        <form onSubmit={onSubmit} className="flex flex-col gap-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <SelectField label="Puja" value={f.pujaId} onChange={set("pujaId")}
                         options={catalog.map((p) => ({ value: String(p.id), label: `${p.name} · ${isFree(p.dakshina) ? "no dakshina" : rupees(p.dakshina)}` }))} />
            <TextField label="Date" type="date" value={f.pujaDate} onChange={set("pujaDate")} error={fields.pujaDate} />
          </div>
          <TextField label="Name for the sankalp" value={f.devoteeName} onChange={set("devoteeName")} error={fields.devoteeName} />
          <div className="grid gap-3 sm:grid-cols-3">
            <TextField label="Gotra" value={f.gotra} onChange={set("gotra")} />
            <TextField label="Nakshatra" value={f.nakshatra} onChange={set("nakshatra")} />
            <TextField label="Rashi" value={f.rashi} onChange={set("rashi")} />
          </div>
          <TextField label="Family members (optional)" value={f.familyNames} onChange={set("familyNames")} />
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label="Mobile (optional)" inputMode="tel" value={f.phone} onChange={set("phone")} error={fields.phone}
                       hint="Lets them see it in My Mandir" />
            <TextField label="Email (optional)" type="email" value={f.email} onChange={set("email")} error={fields.email} />
          </div>
          {paid ? (
            <div className="grid gap-3 rounded-[10px] border border-line bg-surface-2 p-3 sm:grid-cols-2">
              <SelectField label={`Dakshina ${puja ? rupees(puja.dakshina) : ""} paid by`} value={f.mode} onChange={set("mode")}
                           options={MODES} error={fields.mode} />
              <TextField label="Reference (optional)" placeholder="UTR / cheque no." value={f.reference} onChange={set("reference")} />
            </div>
          ) : null}
          {error ? <Alert tone="danger">{error}</Alert> : null}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
            <Button type="submit" busy={busy}>Book puja</Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}


export function IssuePassDialog({ event, events, initial, onClose, onIssued }: {
  /** One utsav, or a list to pick from (e.g. from a devotee's page). */
  event?: StaffEvent; events?: StaffEvent[]; initial?: Partial<Record<"name" | "phone" | "email", string>>;
  onClose: () => void; onIssued: (p: EventPass, e: StaffEvent) => void;
}) {
  const options = event ? [event] : events ?? [];
  const [eventId, setEventId] = useState(String(options[0]?.id ?? ""));
  const chosen = options.find((e) => String(e.id) === eventId) ?? null;
  const [f, setF] = useState({ name: initial?.name ?? "", count: "1", phone: initial?.phone ?? "", email: initial?.email ?? "" });
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    if (!chosen) {
      setBusy(false);
      return;
    }
    try {
      onIssued(await api.request<EventPass>(`/events/${chosen.id}/passes`, { method: "POST", json: {
        name: f.name.trim(), count: Number(f.count), phone: f.phone.trim() || null, email: f.email.trim() || null } }), chosen);
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(err instanceof ApiError && err.code === "event_full" ? "Not enough places left." : describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={event ? `Issue a pass · ${event.title}` : "Issue a darshan pass"}>
      {options.length === 0 ? <p className="text-sm">No utsav is taking passes right now.</p> : (
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        {!event ? (
          <SelectField label="Utsav" value={eventId} onChange={(e) => setEventId(e.target.value)}
                       options={options.map((e) => ({ value: String(e.id), label: `${e.title} · ${formatIst(e.startsAt)}` }))} />
        ) : null}
        <div className="grid gap-3 sm:grid-cols-[1fr_8rem]">
          <TextField label="Name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={fields.name} />
          <SelectField label="People" value={f.count} onChange={(e) => setF({ ...f, count: e.target.value })}
                       options={Array.from({ length: 10 }, (_, i) => ({ value: String(i + 1), label: String(i + 1) }))} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Mobile (optional)" inputMode="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })}
                     error={fields.phone} />
          <TextField label="Email (optional)" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })}
                     error={fields.email} />
        </div>
        <p className="text-xs text-muted">A mobile or email lets them see the pass in My Mandir. Places still count against capacity.</p>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Issue pass</Button>
        </div>
      </form>
      )}
    </Dialog>
  );
}

export function RegisterSevakDialog({ teams, teamId, initial, onClose, onSaved }: {
  teams: Team[]; teamId: number | null; initial?: Partial<Record<"fullName" | "phone" | "email", string>>;
  onClose: () => void; onSaved: (name: string) => void;
}) {
  const [f, setF] = useState({ fullName: initial?.fullName ?? "", phone: initial?.phone ?? "", email: initial?.email ?? "", sevaAreas: teams.find((t) => t.id === teamId)?.name ?? "",
    availability: "", notes: "", teamId: teamId ? String(teamId) : "", duty: "", approved: "true" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await api.request("/sevaks", { method: "POST", json: {
        fullName: f.fullName.trim(), phone: f.phone.trim() || null, email: f.email.trim() || null,
        sevaAreas: f.sevaAreas.trim(), availability: f.availability.trim() || null, notes: f.notes.trim() || null,
        approved: f.approved === "true", teamId: f.teamId ? Number(f.teamId) : null, duty: f.duty.trim() || null } });
      onSaved(f.fullName.trim());
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title="Register a volunteer">
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Full name" placeholder="Ramesh Sharma" value={f.fullName} onChange={set("fullName")} error={fields.fullName} />
          <TextField label="Mobile" inputMode="tel" placeholder="98765 43210" value={f.phone} onChange={set("phone")} error={fields.phone} />
        </div>
        <TextField label="Email (if no mobile)" type="email" value={f.email} onChange={set("email")} error={fields.email} />
        <TextField label="Seva they offer" placeholder="Kitchen, crowd management" value={f.sevaAreas} onChange={set("sevaAreas")}
                   error={fields.sevaAreas} />
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Availability (optional)" placeholder="Weekends & festivals" value={f.availability} onChange={set("availability")} />
          <SelectField label="Status" value={f.approved} onChange={set("approved")}
                       options={[{ value: "true", label: "Approved / ready" }, { value: "false", label: "Pending review" }]} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <SelectField label="Assign team (optional)" value={f.teamId} onChange={set("teamId")}
                       options={[{ value: "", label: "— Unassigned —" }, ...teams.map((t) => ({ value: String(t.id), label: t.name }))]} />
          <TextField label="Duty / shift (optional)" placeholder="Navratri morning kitchen" value={f.duty} onChange={set("duty")} />
        </div>
        <TextField label="Skills / notes (optional)" value={f.notes} onChange={set("notes")} />
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Register volunteer</Button>
        </div>
      </form>
    </Dialog>
  );
}
