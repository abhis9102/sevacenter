"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MandirPageTitle, useDevotee, useMandirText } from "@/components/MandirShell";
import { prefill } from "@/lib/devotee";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatIst, formatPassCode, type PassIssued, type PublicEvent } from "@/lib/events";

/**
 * Public events on the trust's own host (ADR 0014). Devotees register (no sign-in) and get a pass
 * code to show at the gate. Places are checked by the server under a lock.
 */
export default function UpcomingPage() {
  const tx = useMandirText();
  const [events, setEvents] = useState<PublicEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [registering, setRegistering] = useState<PublicEvent | null>(null);
  const [issued, setIssued] = useState<PassIssued | null>(null);

  useEffect(() => {
    api.get<PublicEvent[]>("/public/events").then(setEvents).catch((err) => setError(describeError(err)));
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="utsav" title={tx.utsav.title} subtitle={tx.utsav.subtitle} />
      {error ? <Alert tone="danger">{error}</Alert> : null}

      {issued ? (
        <Card className="mx-auto flex w-full max-w-md flex-col items-center gap-2 border-2 border-dashed border-primary/40 bg-gradient-to-br from-primary/10 via-surface to-haldi/10 text-center">
          <p className="text-xs font-semibold uppercase tracking-wider text-primary-strong">{tx.utsav.passLabel}</p>
          <h2 className="text-lg font-medium">{tx.utsav.registered(issued.name)}</h2>
          <p className="text-sm">{issued.eventTitle} · {formatIst(issued.startsAt)} · {tx.common.people(issued.count)}</p>
          <p className="font-mono text-3xl tracking-widest text-primary-strong" aria-label="Pass code">{formatPassCode(issued.passCode)}</p>
          <p className="text-xs text-muted">{tx.utsav.showCode}</p>
          <div><Button variant="secondary" onClick={() => setIssued(null)}>{tx.utsav.backToList}</Button></div>
        </Card>
      ) : registering ? (
        <RegisterForm event={registering} onCancel={() => setRegistering(null)}
                      onDone={(p) => { setRegistering(null); setIssued(p); }} />
      ) : (
        <div className="flex flex-col gap-4">
          {events?.length === 0 ? <Card><p className="text-muted">{tx.utsav.none}</p></Card> : null}
          {events?.map((e) => {
            const d = new Date(e.startsAt);
            const day = d.toLocaleDateString("en-IN", { day: "numeric", timeZone: "Asia/Kolkata" });
            const month = d.toLocaleDateString("en-IN", { month: "short", timeZone: "Asia/Kolkata" });
            return (
              <Card key={e.id} className="flex gap-4">
                <div className="flex size-16 shrink-0 flex-col items-center justify-center rounded-[12px] bg-gradient-to-br from-primary to-kumkum text-white shadow-xs">
                  <span className="text-2xl font-semibold leading-none">{day}</span>
                  <span className="text-xs font-semibold uppercase">{month}</span>
                </div>
                <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                  <h2 className="text-lg font-semibold">{e.title}</h2>
                  <p className="text-sm text-muted">{formatIst(e.startsAt)}</p>
                  {e.description ? <p className="text-sm">{e.description}</p> : null}
                  <div className="flex flex-wrap items-center gap-3 pt-1">
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      e.placesLeft === 0 ? "bg-kumkum/12 text-kumkum" : "bg-success/12 text-success"}`}>
                      {e.placesLeft === null ? tx.utsav.openToAll : e.placesLeft === 0 ? tx.utsav.full : tx.utsav.placesLeft(e.placesLeft)}
                    </span>
                    {e.registrationOpen && e.placesLeft !== 0 ? (
                      <Button onClick={() => setRegistering(e)}>{tx.utsav.getPass}</Button>
                    ) : (
                      <span className="text-xs text-muted">{tx.utsav.closed}</span>
                    )}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

function RegisterForm({ event, onCancel, onDone }: { event: PublicEvent; onCancel: () => void; onDone: (p: PassIssued) => void }) {
  const tx = useMandirText();
  const pre = prefill(useDevotee());
  const [name, setName] = useState(pre.name);
  const [count, setCount] = useState("1");
  const [phone, setPhone] = useState(pre.phone);
  const [email, setEmail] = useState(pre.email);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      onDone(await api.request<PassIssued>(`/public/events/${event.id}/register`, {
        method: "POST",
        json: { name: name.trim(), count: Number(count), phone: phone.trim() || null, email: email.trim() || null },
      }));
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(err instanceof ApiError && err.code === "event_full" ? tx.utsav.notEnough : describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="mx-auto flex w-full max-w-xl flex-col gap-3">
      <h2 className="text-lg font-semibold">{tx.utsav.register(event.title)}</h2>
      <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
        <TextField label={tx.common.yourName} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} error={fields.name} />
        <TextField label={tx.utsav.count} inputMode="numeric" value={count}
                   onChange={(e) => setCount(e.target.value.replace(/\D/g, "").slice(0, 2))} error={fields.count} />
        <TextField label={tx.common.mobile} inputMode="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)}
                   error={fields.phone} />
        <TextField label={tx.common.emailIfNoMobile} type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
                   error={fields.email} />
        <p className="text-xs text-muted">{tx.utsav.usedOnly}</p>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex gap-2">
          <Button type="submit" busy={busy}>{tx.utsav.submit}</Button>
          <Button type="button" variant="secondary" onClick={onCancel}>{tx.common.back}</Button>
        </div>
      </form>
    </Card>
  );
}
