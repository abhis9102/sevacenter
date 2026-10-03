"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya } from "@/components/Diya";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatIst, formatPassCode, type PassIssued, type PublicEvent } from "@/lib/events";

/**
 * Public events on the trust's own host (ADR 0014). Devotees register (no sign-in) and get a pass
 * code to show at the gate. Places are checked by the server under a lock.
 */
export default function UpcomingPage() {
  const [events, setEvents] = useState<PublicEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [registering, setRegistering] = useState<PublicEvent | null>(null);
  const [issued, setIssued] = useState<PassIssued | null>(null);

  useEffect(() => {
    api.get<PublicEvent[]>("/public/events").then(setEvents).catch((err) => setError(describeError(err)));
  }, []);

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-4 py-10">
      <header className="flex items-center gap-3">
        <Diya className="size-9" />
        <h1 className="text-xl font-semibold">Upcoming events</h1>
      </header>
      {error ? <Alert tone="danger">{error}</Alert> : null}

      {issued ? (
        <Card className="flex flex-col gap-2">
          <h2 className="text-lg font-medium">You&apos;re registered, {issued.name}</h2>
          <p>{issued.eventTitle} · {formatIst(issued.startsAt)} · {issued.count} {issued.count === 1 ? "person" : "people"}</p>
          <p className="text-sm text-muted">Show this pass code at the gate:</p>
          <p className="font-mono text-3xl tracking-widest" aria-label="Pass code">{formatPassCode(issued.passCode)}</p>
          <p className="text-xs text-muted">Keep it private: anyone with the code can use the pass. Take a screenshot.</p>
          <div><Button variant="secondary" onClick={() => setIssued(null)}>Back to events</Button></div>
        </Card>
      ) : registering ? (
        <RegisterForm event={registering} onCancel={() => setRegistering(null)}
                      onDone={(p) => { setRegistering(null); setIssued(p); }} />
      ) : (
        <div className="flex flex-col gap-4">
          {events?.length === 0 ? <Card><p className="text-muted">No upcoming events right now.</p></Card> : null}
          {events?.map((e) => (
            <Card key={e.id} className="flex flex-col gap-2">
              <h2 className="font-medium">{e.title}</h2>
              <p className="text-sm text-muted">{formatIst(e.startsAt)}</p>
              {e.description ? <p className="text-sm">{e.description}</p> : null}
              <p className="text-sm">
                {e.placesLeft === null ? "Open to all" : e.placesLeft === 0 ? "Full" : `${e.placesLeft} places left`}
              </p>
              {e.registrationOpen && e.placesLeft !== 0 ? (
                <div><Button onClick={() => setRegistering(e)}>Register</Button></div>
              ) : (
                <p className="text-xs text-muted">Registration closed.</p>
              )}
            </Card>
          ))}
        </div>
      )}
    </main>
  );
}

function RegisterForm({ event, onCancel, onDone }: { event: PublicEvent; onCancel: () => void; onDone: (p: PassIssued) => void }) {
  const [name, setName] = useState("");
  const [count, setCount] = useState("1");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
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
      setError(err instanceof ApiError && err.code === "event_full" ? "Sorry, there aren't enough places left." : describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="flex flex-col gap-3">
      <h2 className="font-medium">Register · {event.title}</h2>
      <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
        <TextField label="Your name" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} error={fields.name} />
        <TextField label="Number of people (1–10)" inputMode="numeric" value={count}
                   onChange={(e) => setCount(e.target.value.replace(/\D/g, "").slice(0, 2))} error={fields.count} />
        <TextField label="Mobile number" inputMode="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)}
                   error={fields.phone} />
        <TextField label="Email (if no mobile)" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
                   error={fields.email} />
        <p className="text-xs text-muted">Your contact details are used by the temple only for this event.</p>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex gap-2">
          <Button type="submit" busy={busy}>Get my pass</Button>
          <Button type="button" variant="secondary" onClick={onCancel}>Back</Button>
        </div>
      </form>
    </Card>
  );
}
