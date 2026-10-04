"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MandirPageTitle, useDevotee, useMandirText } from "@/components/MandirShell";
import { prefill } from "@/lib/devotee";
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
  const tx = useMandirText();
  const [pujas, setPujas] = useState<Puja[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [chosen, setChosen] = useState<Puja | null>(null);
  const [done, setDone] = useState<{ code: string; puja: string; date: string } | null>(null);

  useEffect(() => {
    api.get<Puja[]>("/public/pujas").then(setPujas).catch((err) => setError(describeError(err)));
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="puja" title={tx.puja.title} subtitle={tx.puja.subtitle} />
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {done ? (
        <Card className="mx-auto flex w-full max-w-md flex-col items-center gap-2 border-primary/30 bg-gradient-to-br from-primary/10 via-surface to-haldi/10 text-center">
          <p className="text-xs font-semibold uppercase tracking-wider text-primary-strong">{tx.puja.bookedLabel}</p>
          <h2 className="text-lg font-medium">{tx.puja.booked(done.puja, done.date)}</h2>
          <p className="text-sm text-muted">{tx.puja.code}</p>
          <p className="font-mono text-3xl tracking-widest text-primary-strong">{formatPassCode(done.code)}</p>
          <p className="text-xs text-muted">{tx.puja.notDonation}</p>
        </Card>
      ) : chosen ? (
        <BookingForm puja={chosen} onBack={() => setChosen(null)} onDone={setDone} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {pujas?.length === 0 ? <Card><p className="text-muted">{tx.puja.none}</p></Card> : null}
          {pujas?.map((p) => (
            <Card key={p.id} className="flex flex-col gap-2 transition-colors hover:border-primary/40">
              <div className="flex items-start justify-between gap-3">
                <h2 className="text-lg font-semibold">{p.name}</h2>
                <span className={`shrink-0 rounded-full px-2.5 py-0.5 font-mono text-xs font-semibold ${
                  isFree(p.dakshina) ? "bg-success/12 text-success" : "bg-primary/12 text-primary-strong"}`}>
                  {isFree(p.dakshina) ? tx.puja.noDakshina : `₹${Number(p.dakshina).toLocaleString("en-IN")}`}
                </span>
              </div>
              {p.deity ? <p className="text-xs font-semibold uppercase tracking-wide text-maroon">{p.deity}</p> : null}
              {p.description ? <p className="flex-1 text-sm text-muted">{p.description}</p> : <span className="flex-1" />}
              <div className="pt-1"><Button onClick={() => setChosen(p)}>{tx.puja.book}</Button></div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function BookingForm({ puja, onBack, onDone }: {
  puja: Puja; onBack: () => void; onDone: (d: { code: string; puja: string; date: string }) => void;
}) {
  const tx = useMandirText();
  const devotee = useDevotee();
  const [f, setF] = useState(() => {
    const pre = prefill(devotee), p = devotee?.profile;
    return { devoteeName: pre.name, gotra: p?.gotra ?? "", nakshatra: p?.nakshatra ?? "", rashi: p?.rashi ?? "",
             familyNames: p?.familyNames ?? "", pujaDate: todayIst(), phone: pre.phone, email: pre.email };
  });
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
            .catch((err) => setError(`${describeError(err)} ${tx.puja.takenNote(paid.razorpay_payment_id)}`))
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
    <Card className="mx-auto flex w-full max-w-2xl flex-col gap-4">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-maroon">{tx.puja.details}</p>
        <h2 className="text-lg font-semibold">{puja.name}{isFree(puja.dakshina) ? "" : ` · ₹${puja.dakshina}`}</h2>
      </div>
      <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-2" noValidate>
        <TextField label={tx.puja.nameForSankalp} value={f.devoteeName} onChange={set("devoteeName")} error={fields.devoteeName} />
        <TextField label={tx.puja.gotra} value={f.gotra} onChange={set("gotra")} />
        <TextField label={tx.puja.nakshatra} value={f.nakshatra} onChange={set("nakshatra")} />
        <TextField label={tx.puja.rashi} value={f.rashi} onChange={set("rashi")} />
        <TextField label={tx.puja.family} value={f.familyNames} onChange={set("familyNames")} />
        <TextField label={tx.puja.date} type="date" value={f.pujaDate} onChange={set("pujaDate")} error={fields.pujaDate} />
        <TextField label={tx.common.mobile} inputMode="tel" value={f.phone} onChange={set("phone")} error={fields.phone} />
        <TextField label={tx.common.emailIfNoMobile} type="email" value={f.email} onChange={set("email")} error={fields.email} />
        <div className="flex flex-col gap-3 sm:col-span-2">
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex gap-2">
          <Button type="submit" busy={busy}>{isFree(puja.dakshina) ? tx.puja.submitFree : tx.puja.submitPaid(puja.dakshina)}</Button>
          <Button type="button" variant="secondary" onClick={onBack}>{tx.common.back}</Button>
        </div>
        </div>
      </form>
    </Card>
  );
}
