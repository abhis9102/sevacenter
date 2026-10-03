"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Badge, Button, Card, Dialog, PageHeader, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { isFree, todayIst, type Puja, type PujaBooking } from "@/lib/pujas";
import { hasRole } from "@/lib/types";

const TONE = { AWAITING_PAYMENT: "warning", CONFIRMED: "primary", PERFORMED: "success", CANCELLED: "neutral" } as const;

/**
 * Pujas (ADR 0016): the day's schedule for the priest (no contacts) and, for leaders, the catalog
 * and contacts. Puja dakshina is seva income, kept apart from 80G donations.
 */
export default function PujasPage() {
  const me = useMe();
  const isLeader = hasRole(me.role, "LEADER");
  const [date, setDate] = useState(todayIst());
  const [bookings, setBookings] = useState<PujaBooking[] | null>(null);
  const [catalog, setCatalog] = useState<Puja[] | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    try {
      const [b, c] = await Promise.all([
        api.get<PujaBooking[]>(`/puja-bookings?date=${encodeURIComponent(date)}`),
        api.get<Puja[]>("/pujas"),
      ]);
      setBookings(b);
      setCatalog(c);
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
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Pujas" description="The day's sankalps and the puja catalog. Devotees book on the public /book-puja page."
                  actions={isLeader ? <Button onClick={() => setAdding(true)}>Add puja</Button> : null} />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}

      <Card className="flex flex-col gap-3">
        <div className="flex items-end gap-3">
          <TextField label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </div>
        {bookings?.length === 0 ? <p className="text-muted">No bookings for this day.</p> : null}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <tbody>
              {bookings?.map((b) => (
                <tr key={b.id} className="border-t border-line align-top">
                  <td className="py-2 pr-3">
                    <p className="font-medium">{b.pujaName}</p>
                    <p className="text-xs text-muted font-mono">{b.bookingCode}</p>
                  </td>
                  <td className="py-2 pr-3">
                    <p>{b.devoteeName}</p>
                    <p className="text-xs text-muted">
                      {[b.gotra && `Gotra ${b.gotra}`, b.nakshatra && `Nakshatra ${b.nakshatra}`, b.rashi && `Rashi ${b.rashi}`]
                        .filter(Boolean).join(" · ")}
                    </p>
                    {b.familyNames ? <p className="text-xs">Family: {b.familyNames}</p> : null}
                    {isLeader && (b.phone || b.email) ? <p className="text-xs text-muted">{b.phone ?? b.email}</p> : null}
                  </td>
                  <td className="py-2 pr-3"><Badge tone={TONE[b.status]}>{b.status.toLowerCase().replace("_", " ")}</Badge></td>
                  <td className="py-2 text-right whitespace-nowrap">
                    {b.status === "CONFIRMED" ? <Button onClick={() => void act(b, "performed")}>Mark performed</Button> : null}
                    {isLeader && (b.status === "CONFIRMED" || b.status === "AWAITING_PAYMENT") ? (
                      <Button variant="ghost" className="text-danger" onClick={() => void act(b, "cancel")}>Cancel</Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card className="flex flex-col gap-2">
        <h2 className="font-medium">Catalog</h2>
        {catalog?.map((p) => (
          <p key={p.id} className="text-sm">
            <span className="font-medium">{p.name}</span>
            {p.deity ? <span className="text-muted"> · {p.deity}</span> : null}
            <span> · {isFree(p.dakshina) ? "free" : `₹${p.dakshina}`}</span>
            {!p.active ? <span className="text-muted"> · hidden</span> : null}
          </p>
        ))}
      </Card>

      {isLeader ? <AddPujaDialog open={adding} onClose={() => setAdding(false)}
                                 onSaved={() => { setAdding(false); void load(); }} /> : null}
    </div>
  );
}

function AddPujaDialog({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState("");
  const [deity, setDeity] = useState("");
  const [description, setDescription] = useState("");
  const [dakshina, setDakshina] = useState("0");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await api.request("/pujas", {
        method: "POST",
        json: { name: name.trim(), deity: deity.trim() || null, description: description.trim() || null,
                dakshina: dakshina.trim(), active: true, displayOrder: 0 },
      });
      setName(""); setDeity(""); setDescription(""); setDakshina("0");
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title="Add puja">
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} error={fields.name} />
        <TextField label="Deity (optional)" value={deity} onChange={(e) => setDeity(e.target.value)} />
        <TextField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        <TextField label="Dakshina (₹, 0 for free)" inputMode="decimal" value={dakshina}
                   onChange={(e) => setDakshina(e.target.value)} error={fields.dakshina} />
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
