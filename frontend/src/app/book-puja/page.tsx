"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya } from "@/components/Diya";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatPassCode } from "@/lib/events";
import { isFree, todayIst, type Booked, type Puja } from "@/lib/pujas";
import { loadCheckout } from "@/lib/razorpay";

/**
 * Public puja booking on the trust's own host (ADR 0016). A paid puja opens Razorpay Checkout; the
 * server confirms the booking only after verifying the payment with Razorpay.
 */
export default function BookPujaPage() {
  const [pujas, setPujas] = useState<Puja[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [chosen, setChosen] = useState<Puja | null>(null);
  const [done, setDone] = useState<{ code: string; puja: string; date: string } | null>(null);

  useEffect(() => {
    api.get<Puja[]>("/public/pujas").then(setPujas).catch((err) => setError(describeError(err)));
  }, []);

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-4 py-10">
      <header className="flex items-center gap-3">
        <Diya className="size-9" />
        <h1 className="text-xl font-semibold">Book a puja</h1>
      </header>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {done ? (
        <Card className="flex flex-col gap-2">
          <h2 className="text-lg font-medium">Your {done.puja} is booked for {done.date}</h2>
          <p className="text-sm text-muted">Booking code:</p>
          <p className="font-mono text-2xl tracking-widest">{formatPassCode(done.code)}</p>
          <p className="text-xs text-muted">Puja dakshina is a seva fee, not an 80G-eligible donation.</p>
        </Card>
      ) : chosen ? (
        <BookingForm puja={chosen} onBack={() => setChosen(null)} onDone={setDone} />
      ) : (
        <div className="flex flex-col gap-3">
          {pujas?.length === 0 ? <Card><p className="text-muted">No pujas are open for booking right now.</p></Card> : null}
          {pujas?.map((p) => (
            <Card key={p.id} className="flex flex-col gap-1">
              <h2 className="font-medium">{p.name}</h2>
              {p.deity ? <p className="text-sm text-muted">{p.deity}</p> : null}
              {p.description ? <p className="text-sm">{p.description}</p> : null}
              <p className="text-sm">{isFree(p.dakshina) ? "No dakshina" : `Dakshina ₹${p.dakshina}`}</p>
              <div><Button onClick={() => setChosen(p)}>Book</Button></div>
            </Card>
          ))}
        </div>
      )}
    </main>
  );
}

function BookingForm({ puja, onBack, onDone }: {
  puja: Puja; onBack: () => void; onDone: (d: { code: string; puja: string; date: string }) => void;
}) {
  const [f, setF] = useState({ devoteeName: "", gotra: "", nakshatra: "", rashi: "", familyNames: "", pujaDate: todayIst(),
                                phone: "", email: "" });
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
      const booked = await api.request<Booked>(`/public/pujas/${puja.id}/book`, {
        method: "POST",
        json: Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v.trim() || null])),
      });
      if (!booked.orderId || !booked.keyId) {
        onDone({ code: booked.bookingCode, puja: booked.pujaName, date: booked.pujaDate });
        return;
      }
      const Razorpay = await loadCheckout();
      new Razorpay({
        key: booked.keyId,
        order_id: booked.orderId,
        amount: booked.amountPaise,
        currency: "INR",
        name: booked.pujaName,
        description: `Puja on ${booked.pujaDate}`,
        prefill: { name: f.devoteeName.trim() },
        handler: (paid) => {
          api.request<{ bookingCode: string }>("/public/donations/confirm", {
            method: "POST",
            json: { orderId: paid.razorpay_order_id, paymentId: paid.razorpay_payment_id, signature: paid.razorpay_signature },
          }).then((r) => onDone({ code: r.bookingCode, puja: booked.pujaName, date: booked.pujaDate }))
            .catch((err) => setError(`${describeError(err)} If money was taken, the temple's records will pick it up; keep reference ${paid.razorpay_payment_id}.`))
            .finally(() => setBusy(false));
        },
        modal: { ondismiss: () => setBusy(false) },
      }).open();
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
      setBusy(false);
    }
  }

  return (
    <Card className="flex flex-col gap-3">
      <h2 className="font-medium">{puja.name}{isFree(puja.dakshina) ? "" : ` · ₹${puja.dakshina}`}</h2>
      <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
        <TextField label="Name for the sankalp" value={f.devoteeName} onChange={set("devoteeName")} error={fields.devoteeName} />
        <TextField label="Gotra (optional)" value={f.gotra} onChange={set("gotra")} />
        <TextField label="Nakshatra (optional)" value={f.nakshatra} onChange={set("nakshatra")} />
        <TextField label="Rashi (optional)" value={f.rashi} onChange={set("rashi")} />
        <TextField label="Family members (optional)" value={f.familyNames} onChange={set("familyNames")} />
        <TextField label="Date" type="date" value={f.pujaDate} onChange={set("pujaDate")} error={fields.pujaDate} />
        <TextField label="Mobile number" inputMode="tel" value={f.phone} onChange={set("phone")} error={fields.phone} />
        <TextField label="Email (if no mobile)" type="email" value={f.email} onChange={set("email")} error={fields.email} />
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex gap-2">
          <Button type="submit" busy={busy}>{isFree(puja.dakshina) ? "Book" : `Pay ₹${puja.dakshina} & book`}</Button>
          <Button type="button" variant="secondary" onClick={onBack}>Back</Button>
        </div>
      </form>
    </Card>
  );
}
