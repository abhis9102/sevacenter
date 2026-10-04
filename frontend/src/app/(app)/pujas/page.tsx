"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { CounterBookingDialog } from "@/components/StaffDialogs";
import { ConfirmDialog, EmptyState, Pill, StaffTitle, useView, ViewSwitcher } from "@/components/staff";
import { Alert, Button, Card, Dialog, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { isFree, todayIst, type Priest, type Puja, type PujaBooking } from "@/lib/pujas";
import { hasRole } from "@/lib/types";

const STATUS = {
  AWAITING_PAYMENT: { tone: "warning", label: "Awaiting payment" },
  CONFIRMED: { tone: "primary", label: "Confirmed" },
  PERFORMED: { tone: "success", label: "Performed" },
  CANCELLED: { tone: "neutral", label: "Cancelled" },
} as const;
const VIEWS = ["roster", "catalog", "priests"] as const;
const rupees = (r: string) => `₹${Number(r).toLocaleString("en-IN")}`;

/**
 * Pujas & sankalp (ADR 0016, 0026, 0027): the priest's roster for a day (no contacts for members),
 * the temple's priests, and for leaders the catalog, counter bookings and who performs each sankalp. Dakshina is seva income, never an 80G donation.
 */
export default function PujasPage() {
  const me = useMe();
  const isLeader = hasRole(me.role, "LEADER");
  const view = useView(VIEWS);
  const [date, setDate] = useState(todayIst());
  const [bookings, setBookings] = useState<PujaBooking[] | null>(null);
  const [catalog, setCatalog] = useState<Puja[] | null>(null);
  const [priests, setPriests] = useState<Priest[] | null>(null);
  const [editingPriest, setEditingPriest] = useState<Priest | "new" | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [editing, setEditing] = useState<Puja | "new" | null>(null);
  const [booking, setBooking] = useState(false);
  const [cancelling, setCancelling] = useState<PujaBooking | null>(null);

  const load = useCallback(async () => {
    try {
      const [b, c, p] = await Promise.all([
        api.get<PujaBooking[]>(`/puja-bookings?date=${encodeURIComponent(date)}`),
        api.get<Puja[]>("/pujas"),
        api.get<Priest[]>("/priests"),
      ]);
      setBookings(b);
      setCatalog(c);
      setPriests(p);
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }, [date]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- load on date change
    void load();
  }, [load]);

  async function act(b: PujaBooking, action: "performed" | "cancel") {
    setNotice(null);
    try {
      await api.request(`/puja-bookings/${b.id}/${action}`, { method: "POST" });
      setNotice({ tone: "success", text: action === "performed" ? `${b.pujaName} for ${b.devoteeName} marked performed.`
        : `${b.pujaName} for ${b.devoteeName} cancelled.` });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }

  async function assign(b: PujaBooking, priestId: string) {
    setNotice(null);
    try {
      await api.request(`/puja-bookings/${b.id}/priest`, { method: "POST", json: { priestId: priestId ? Number(priestId) : null } });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }

  async function setPriestActive(p: Priest, active: boolean) {
    setNotice(null);
    try {
      await api.request(`/priests/${p.id}`, { method: "PUT", json: { name: p.name, phone: p.phone, specialties: p.specialties, active } });
      setNotice({ tone: "success", text: active ? `${p.name} is available again.` : `${p.name} is no longer assigned new sankalps.` });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }

  async function setActive(p: Puja, active: boolean) {
    setNotice(null);
    try {
      await api.request(`/pujas/${p.id}`, { method: "PUT", json: { name: p.name, deity: p.deity, description: p.description,
        dakshina: p.dakshina, active, displayOrder: p.displayOrder } });
      setNotice({ tone: "success", text: active ? `${p.name} is back on the Mandir Center.` : `${p.name} is hidden from the Mandir Center.` });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }

  const open = bookings?.filter((b) => b.status !== "CANCELLED") ?? [];
  const done = open.filter((b) => b.status === "PERFORMED").length;

  return (
    <div className="flex flex-col gap-6">
      <StaffTitle
        title="Pujas & Sacred Sankalp"
        pill="Vedic Rituals"
        description="The priest's sankalp roster with each devotee's gotra and family, and the temple's puja catalog."
        views={<ViewSwitcher label="Pujas views" current={view} views={[
          { id: "roster", label: "Priest Sankalp Roster" }, { id: "catalog", label: "Puja Catalog & Fees" },
          { id: "priests", label: "Priests & Pujaris" }]} />}
      />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}

      {view === "roster" ? (
        <>
          <Card className="flex flex-wrap items-center justify-between gap-3 py-3">
            <div className="flex flex-wrap items-center gap-2">
              <label htmlFor="puja-date" className="text-sm font-semibold">Puja date</label>
              <input id="puja-date" type="date" value={date} onChange={(e) => setDate(e.target.value)}
                     className="rounded-[10px] border border-line bg-surface px-3 py-1.5 text-sm" />
              {date !== todayIst() ? (
                <button type="button" onClick={() => setDate(todayIst())} className="text-sm font-medium text-primary-strong hover:underline">Today</button>
              ) : null}
            </div>
            <div className="flex items-center gap-3">
              <span className="text-sm text-muted">{open.length} sankalp{open.length === 1 ? "" : "s"} · {done} performed</span>
              {isLeader ? <Button onClick={() => setBooking(true)}>+ Book for a devotee</Button> : null}
            </div>
          </Card>

          {bookings === null ? null : bookings.length === 0 ? (
            <EmptyState title="No pujas booked for this date.">
              Devotees book on the Mandir Center; walk-ins are booked here with &ldquo;Book for a devotee&rdquo;.
            </EmptyState>
          ) : (
            <div className="grid gap-3">
              {bookings.map((b) => (
                <Card key={b.id} className={`flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between ${b.status === "CANCELLED" ? "opacity-60" : ""}`}>
                  <div className="flex min-w-0 flex-1 flex-col gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-lg font-semibold">{b.pujaName}</h2>
                      <span className="rounded bg-surface-2 px-2 py-0.5 font-mono text-xs text-muted">{b.bookingCode}</span>
                      <Pill tone={STATUS[b.status].tone}>{STATUS[b.status].label}</Pill>
                      {b.counter ? <Pill tone="info">Counter</Pill> : null}
                    </div>
                    <dl className="grid gap-x-6 gap-y-2 rounded-[10px] border border-line/70 bg-surface-2 p-3 text-sm sm:grid-cols-2">
                      <div><dt className="text-xs font-semibold text-muted">Yajman (devotee)</dt><dd className="font-semibold">{b.devoteeName}</dd></div>
                      <div><dt className="text-xs font-semibold text-muted">Gotra / Nakshatra / Rashi</dt>
                        <dd>{b.gotra || "—"} / {b.nakshatra || "—"} / {b.rashi || "—"}</dd></div>
                      {b.familyNames ? <div className="sm:col-span-2"><dt className="text-xs font-semibold text-muted">Family members for the sankalp</dt>
                        <dd className="font-medium text-primary-strong">{b.familyNames}</dd></div> : null}
                      <div><dt className="text-xs font-semibold text-muted">Dakshina</dt>
                        <dd className="font-mono">{isFree(b.amount) ? "None" : rupees(b.amount)}{b.counterMode ? ` · ${b.counterMode.replace("_", " ").toLowerCase()} at counter` : ""}</dd></div>
                      {isLeader && (b.phone || b.email) ? <div><dt className="text-xs font-semibold text-muted">Contact</dt><dd>{b.phone ?? b.email}</dd></div> : null}
                      <div>
                        <dt className="text-xs font-semibold text-muted">Priest</dt>
                        {isLeader && (b.status === "CONFIRMED" || b.status === "AWAITING_PAYMENT") && priests ? (
                          <dd>
                            <select aria-label={`Priest for ${b.devoteeName}`} value={b.priestId ?? ""}
                                    onChange={(e) => void assign(b, e.target.value)}
                                    className="mt-0.5 rounded-[8px] border border-line bg-surface px-2 py-1 text-sm">
                              <option value="">Not assigned</option>
                              {priests.filter((p) => p.active || p.id === b.priestId).map((p) => (
                                <option key={p.id} value={p.id}>{p.name}</option>
                              ))}
                            </select>
                          </dd>
                        ) : <dd className="font-medium">{b.priestName ?? "Not assigned"}</dd>}
                      </div>
                    </dl>
                  </div>
                  <div className="flex shrink-0 gap-2 sm:flex-col">
                    {b.status === "CONFIRMED" ? <Button onClick={() => void act(b, "performed")}>Mark performed</Button> : null}
                    {isLeader && (b.status === "CONFIRMED" || b.status === "AWAITING_PAYMENT") ? (
                      <Button variant="secondary" onClick={() => setCancelling(b)}>Cancel</Button>
                    ) : null}
                  </div>
                </Card>
              ))}
            </div>
          )}
        </>
      ) : view === "priests" ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-muted">The temple&apos;s priests and pujaris. Assign one to each sankalp in the roster.</p>
            {isLeader ? <Button onClick={() => setEditingPriest("new")}>+ Add priest</Button> : null}
          </div>
          {priests === null ? null : priests.length === 0 ? (
            <EmptyState title="No priests listed yet.">Add your pujaris to assign them to sankalps.</EmptyState>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {priests.map((p) => (
                <Card key={p.id} className={`flex flex-col gap-3 ${p.active ? "" : "opacity-70"}`}>
                  <div className="flex items-start gap-3">
                    <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary/25 to-gold/25 font-display text-lg font-semibold text-primary-strong">
                      {p.name.replace(/^(Pt\.|Pandit|Shri)\s+/i, "").charAt(0).toUpperCase()}
                    </span>
                    <div className="min-w-0">
                      <h2 className="text-lg font-semibold">{p.name}</h2>
                      {p.phone ? <p className="text-sm text-muted">{p.phone}</p> : null}
                    </div>
                  </div>
                  {p.specialties ? <p className="rounded-[10px] bg-surface-2 p-2.5 text-sm"><span className="font-semibold">Performs: </span>{p.specialties}</p> : null}
                  <p className="text-xs text-muted">
                    {(bookings ?? []).filter((b) => b.priestId === p.id && b.status !== "CANCELLED").length} sankalp(s) on {date}
                  </p>
                  <div className="flex items-center justify-between border-t border-line pt-3">
                    <Pill tone={p.active ? "success" : "neutral"}>{p.active ? "Available" : "Inactive"}</Pill>
                    {isLeader ? (
                      <div className="flex gap-2">
                        <Button variant="secondary" onClick={() => setEditingPriest(p)}>Edit</Button>
                        <Button variant="secondary" onClick={() => void setPriestActive(p, !p.active)}>{p.active ? "Deactivate" : "Activate"}</Button>
                      </div>
                    ) : null}
                  </div>
                </Card>
              ))}
            </div>
          )}
        </>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-muted">Pujas offered to devotees on the Mandir Center. Hidden ones stay in past bookings.</p>
            {isLeader ? <Button onClick={() => setEditing("new")}>+ Add new puja</Button> : null}
          </div>
          {catalog === null ? null : catalog.length === 0 ? (
            <EmptyState title="No pujas in the catalog yet." />
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {catalog.map((p) => (
                <Card key={p.id} className={`flex flex-col justify-between gap-3 ${p.active ? "" : "opacity-70"}`}>
                  <div className="flex flex-col gap-2">
                    <div className="flex items-start justify-between gap-2">
                      {p.deity ? <Pill tone="primary">{p.deity}</Pill> : <span />}
                      <span className="font-mono text-base font-semibold">{isFree(p.dakshina) ? "No dakshina" : rupees(p.dakshina)}</span>
                    </div>
                    <h2 className="text-lg font-semibold">{p.name}</h2>
                    {p.description ? <p className="text-sm text-muted">{p.description}</p> : null}
                  </div>
                  <div className="flex items-center justify-between border-t border-line pt-3">
                    <Pill tone={p.active ? "success" : "neutral"}>{p.active ? "Active" : "Hidden"}</Pill>
                    {isLeader ? (
                      <div className="flex gap-2">
                        <Button variant="secondary" onClick={() => setEditing(p)}>Edit</Button>
                        <Button variant="secondary" onClick={() => void setActive(p, !p.active)}>{p.active ? "Hide" : "Show"}</Button>
                      </div>
                    ) : null}
                  </div>
                </Card>
              ))}
            </div>
          )}
        </>
      )}

      {isLeader && editing ? (
        <PujaDialog puja={editing === "new" ? null : editing} onClose={() => setEditing(null)}
                    onSaved={(name) => { setEditing(null); setNotice({ tone: "success", text: `${name} saved.` }); void load(); }} />
      ) : null}
      {isLeader && editingPriest ? (
        <PriestDialog priest={editingPriest === "new" ? null : editingPriest} onClose={() => setEditingPriest(null)}
                      onSaved={(name) => { setEditingPriest(null); setNotice({ tone: "success", text: `${name} saved.` }); void load(); }} />
      ) : null}
      {isLeader && booking && catalog ? (
        <CounterBookingDialog catalog={catalog.filter((p) => p.active)} date={date} onClose={() => setBooking(false)}
                              onBooked={(b) => { setBooking(false); setDate(b.pujaDate);
                                setNotice({ tone: "success", text: `${b.pujaName} booked for ${b.devoteeName} on ${b.pujaDate} (code ${b.bookingCode}).` });
                                void load(); }} />
      ) : null}
      <ConfirmDialog open={cancelling !== null} title="Cancel this puja?" confirm="Cancel puja"
                     onClose={() => setCancelling(null)}
                     onConfirm={() => { const b = cancelling; setCancelling(null); if (b) void act(b, "cancel"); }}>
        {cancelling ? <p>{cancelling.pujaName} for {cancelling.devoteeName} on {cancelling.pujaDate} will be cancelled. A paid dakshina
          isn&apos;t refunded automatically.</p> : null}
      </ConfirmDialog>
    </div>
  );
}

function PujaDialog({ puja, onClose, onSaved }: { puja: Puja | null; onClose: () => void; onSaved: (name: string) => void }) {
  const [f, setF] = useState({ name: puja?.name ?? "", deity: puja?.deity ?? "", description: puja?.description ?? "",
                               dakshina: puja?.dakshina ?? "0" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await api.request(puja ? `/pujas/${puja.id}` : "/pujas", {
        method: puja ? "PUT" : "POST",
        json: { name: f.name.trim(), deity: f.deity.trim() || null, description: f.description.trim() || null,
                dakshina: f.dakshina.trim(), active: puja?.active ?? true, displayOrder: puja?.displayOrder ?? 0 },
      });
      onSaved(f.name.trim());
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={puja ? `Edit ${puja.name}` : "Add a puja"}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField label="Name" value={f.name} onChange={set("name")} error={fields.name} />
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Deity (optional)" value={f.deity} onChange={set("deity")} />
          <TextField label="Dakshina (₹, 0 for none)" inputMode="decimal" value={f.dakshina} onChange={set("dakshina")} error={fields.dakshina} />
        </div>
        <TextField label="Description (optional)" value={f.description} onChange={set("description")} />
        <p className="text-xs text-muted">Dakshina is a fee for a service, so it isn&apos;t an 80G donation.</p>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Save</Button>
        </div>
      </form>
    </Dialog>
  );
}

function PriestDialog({ priest, onClose, onSaved }: { priest: Priest | null; onClose: () => void; onSaved: (name: string) => void }) {
  const [f, setF] = useState({ name: priest?.name ?? "", phone: priest?.phone ?? "", specialties: priest?.specialties ?? "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await api.request(priest ? `/priests/${priest.id}` : "/priests", {
        method: priest ? "PUT" : "POST",
        json: { name: f.name.trim(), phone: f.phone.trim() || null, specialties: f.specialties.trim() || null, active: priest?.active ?? true },
      });
      onSaved(f.name.trim());
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={priest ? `Edit ${priest.name}` : "Add a priest"}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField label="Name" placeholder="Pt. Shridhar Joshi" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })}
                   error={fields.name} />
        <TextField label="Mobile (optional)" inputMode="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })}
                   error={fields.phone} hint="Seen by leaders only" />
        <TextField label="Pujas they perform (optional)" placeholder="Rudrabhishek, Navagraha homa, Satyanarayan katha"
                   value={f.specialties} onChange={(e) => setF({ ...f, specialties: e.target.value })} error={fields.specialties} />
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Save</Button>
        </div>
      </form>
    </Dialog>
  );
}
