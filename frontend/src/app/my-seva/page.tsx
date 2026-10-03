"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya } from "@/components/Diya";
import { Alert, Badge, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";

type Channel = "EMAIL" | "SMS";

interface MySeva {
  channel: Channel;
  contact: string;
  pujaBookings: { bookingCode: string; pujaName: string; pujaDate: string; amount: string; status: string }[];
  eventPasses: { passCode: string; eventTitle: string | null; startsAt: string | null; attendeeCount: number; status: string }[];
  sevakSignups: { sevaAreas: string; status: string; createdAt: string }[];
}

/**
 * Devotee sign-in with a one-time code, then "my seva" (ADR 0018). The session is an HttpOnly
 * cookie scoped to /api/v1/portal; this page never sees it. 401 here just means "not signed in".
 */
export default function MySevaPage() {
  const [seva, setSeva] = useState<MySeva | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(
    () => api.get<MySeva>("/portal/me", { allowUnauthorized: true }).then(setSeva, () => setSeva(null))
      .finally(() => setLoading(false)),
    [],
  );

  useEffect(() => {
    api.get<MySeva>("/portal/me", { allowUnauthorized: true }).then(setSeva, () => setSeva(null))
      .finally(() => setLoading(false));
  }, []);

  async function signOut() {
    await api.request("/portal/logout", { method: "POST", allowUnauthorized: true }).catch(() => undefined);
    setSeva(null);
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-4 py-10">
      <header className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Diya className="size-9" />
          <h1 className="text-xl font-semibold">My seva</h1>
        </div>
        {seva ? <Button variant="secondary" onClick={() => void signOut()}>Sign out</Button> : null}
      </header>
      {loading ? null : seva ? <SevaView seva={seva} /> : <SignIn onSignedIn={load} />}
    </main>
  );
}

function SignIn({ onSignedIn }: { onSignedIn: () => Promise<void> }) {
  const [channels, setChannels] = useState<{ email: boolean; sms: boolean } | null>(null);
  const [channel, setChannel] = useState<Channel>("SMS");
  const [contact, setContact] = useState("");
  const [code, setCode] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});

  useEffect(() => {
    api.get<{ email: boolean; sms: boolean }>("/public/devotee-login").then((c) => {
      setChannels(c);
      if (!c.sms && c.email) setChannel("EMAIL");
    }).catch(() => setChannels({ email: false, sms: false }));
  }, []);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await action();
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  const sendCode = (e: React.FormEvent) => {
    e.preventDefault();
    void run(async () => {
      await api.request("/public/devotee-login/code", { method: "POST", json: { channel, contact } });
      setSent(true);
    });
  };

  const verify = (e: React.FormEvent) => {
    e.preventDefault();
    void run(async () => {
      await api.request("/public/devotee-login/verify", { method: "POST", json: { channel, contact, code } });
      await api.refreshCsrf();
      await onSignedIn();
    });
  };

  if (channels === null) return null;
  if (!channels.email && !channels.sms) {
    return <Card><p>Signing in isn&apos;t available at this temple yet.</p></Card>;
  }

  return (
    <Card>
      {!sent ? (
        <form onSubmit={sendCode} className="flex flex-col gap-3" noValidate>
          <p className="text-sm text-muted">
            See your puja bookings, event passes and seva. We&apos;ll send a 6-digit code to confirm it&apos;s you.
          </p>
          {channels.email && channels.sms ? (
            <div className="flex gap-2" role="group" aria-label="Send the code by">
              <Button variant={channel === "SMS" ? "primary" : "secondary"} onClick={() => setChannel("SMS")}>Mobile</Button>
              <Button variant={channel === "EMAIL" ? "primary" : "secondary"} onClick={() => setChannel("EMAIL")}>Email</Button>
            </div>
          ) : null}
          {channel === "SMS" ? (
            <TextField label="Mobile number" inputMode="tel" autoComplete="tel" value={contact}
                       onChange={(e) => setContact(e.target.value)} error={fields.contact} />
          ) : (
            <TextField label="Email" type="email" autoComplete="email" value={contact}
                       onChange={(e) => setContact(e.target.value)} error={fields.contact} />
          )}
          <p className="text-xs text-muted">Use the same mobile or email you gave when booking.</p>
          {error ? <Alert tone="danger">{error}</Alert> : null}
          <div><Button type="submit" busy={busy}>Send code</Button></div>
        </form>
      ) : (
        <form onSubmit={verify} className="flex flex-col gap-3" noValidate>
          <p className="text-sm">We sent a code to <strong>{contact}</strong>. It expires in 10 minutes.</p>
          <TextField label="6-digit code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code}
                     onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} />
          <p className="text-xs text-muted">Nobody from the temple will ever ask you for this code.</p>
          {error ? <Alert tone="danger">{error}</Alert> : null}
          <div className="flex gap-2">
            <Button type="submit" busy={busy}>Sign in</Button>
            <Button variant="secondary" onClick={() => { setSent(false); setCode(""); setError(null); }}>Change</Button>
          </div>
        </form>
      )}
    </Card>
  );
}

function SevaView({ seva }: { seva: MySeva }) {
  const empty = seva.pujaBookings.length + seva.eventPasses.length + seva.sevakSignups.length === 0;
  return (
    <>
      <p className="text-sm text-muted">Signed in as {seva.contact}</p>
      {empty ? (
        <Card><p>Nothing here yet for {seva.contact}. Bookings made with this {seva.channel === "SMS" ? "mobile number" : "email"} will show up here.</p></Card>
      ) : null}
      {seva.pujaBookings.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">Puja bookings</h2>
          <ul className="flex flex-col gap-2">
            {seva.pujaBookings.map((b) => (
              <li key={b.bookingCode} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{b.pujaName} · {b.pujaDate} · ₹{b.amount}</span>
                <span className="flex items-center gap-2"><code>{b.bookingCode}</code><Badge>{b.status}</Badge></span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
      {seva.eventPasses.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">Event passes</h2>
          <ul className="flex flex-col gap-2">
            {seva.eventPasses.map((p) => (
              <li key={p.passCode} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{p.eventTitle ?? "Event"}{p.startsAt ? ` · ${new Date(p.startsAt).toLocaleString("en-IN")}` : ""} · {p.attendeeCount} people</span>
                <span className="flex items-center gap-2"><code>{p.passCode}</code><Badge>{p.status}</Badge></span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
      {seva.sevakSignups.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">Seva offered</h2>
          <ul className="flex flex-col gap-2">
            {seva.sevakSignups.map((s) => (
              <li key={s.createdAt} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{s.sevaAreas}</span><Badge>{s.status}</Badge>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
    </>
  );
}
