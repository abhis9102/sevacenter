"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya } from "@/components/Diya";
import { Alert, Badge, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import type { ReceiptDetail } from "@/lib/types";

type Channel = "EMAIL" | "SMS";

interface MySeva {
  channel: Channel;
  contact: string;
  donations: {
    id: number; receivedOn: string; amount: string; mode: string; purpose: string | null; reversed: boolean;
    receiptNumber: string | null; receiptValid: boolean;
  }[];
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
  const [receipt, setReceipt] = useState<ReceiptDetail | null>(null);
  const [receiptError, setReceiptError] = useState<string | null>(null);
  const empty = seva.donations.length + seva.pujaBookings.length + seva.eventPasses.length + seva.sevakSignups.length === 0;

  async function openReceipt(donationId: number) {
    setReceiptError(null);
    try {
      setReceipt(await api.get<ReceiptDetail>(`/portal/donations/${donationId}/receipt`, { allowUnauthorized: true }));
    } catch (err) {
      setReceiptError(describeError(err));
    }
  }

  if (receipt) {
    return <ReceiptCopy receipt={receipt} onClose={() => setReceipt(null)} />;
  }

  return (
    <>
      <p className="text-sm text-muted">Signed in as {seva.contact}</p>
      {empty ? (
        <Card><p>Nothing here yet for {seva.contact}. Bookings made with this {seva.channel === "SMS" ? "mobile number" : "email"} will show up here.</p></Card>
      ) : null}
      {seva.donations.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">Donations &amp; receipts</h2>
          {receiptError ? <Alert tone="danger">{receiptError}</Alert> : null}
          <ul className="flex flex-col gap-2">
            {seva.donations.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{d.receivedOn} · ₹{d.amount}{d.purpose ? ` · ${d.purpose}` : ""}</span>
                <span className="flex items-center gap-2">
                  {d.reversed ? <Badge tone="warning">Reversed</Badge> : null}
                  {d.receiptNumber && d.receiptValid ? (
                    <Button variant="secondary" onClick={() => void openReceipt(d.id)}>Receipt {d.receiptNumber}</Button>
                  ) : d.receiptNumber ? <Badge tone="danger">Receipt cancelled</Badge> : <Badge>No 80G receipt yet</Badge>}
                </span>
              </li>
            ))}
          </ul>
        </Card>
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

/** A copy of the 80G receipt for the devotee's records. The PAN is masked; the trust holds the original. */
function ReceiptCopy({ receipt, onClose }: { receipt: ReceiptDetail; onClose: () => void }) {
  return (
    <Card className="print:border-none">
      <div className="flex flex-col gap-3 text-sm">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold">{receipt.trustLegalName}</h2>
            <p className="text-muted">{receipt.trustAddress}</p>
            <p className="text-muted">PAN {receipt.trustPan} · 80G {receipt.trustRegistration80g}</p>
          </div>
          <Button variant="secondary" onClick={onClose} className="print:hidden">Back</Button>
        </div>
        <p className="font-semibold">Receipt {receipt.number} · issued {receipt.issuedOn}</p>
        {receipt.cancelled ? <Alert tone="danger">This receipt was cancelled.</Alert> : null}
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt className="text-muted">Received from</dt><dd>{receipt.donorName}</dd>
          <dt className="text-muted">Address</dt><dd>{receipt.donorAddress}</dd>
          <dt className="text-muted">Donor PAN</dt><dd>{receipt.donorPan}</dd>
          <dt className="text-muted">Amount</dt><dd>₹{receipt.amount}</dd>
          <dt className="text-muted">Mode</dt><dd>{receipt.mode}</dd>
          <dt className="text-muted">Received on</dt><dd>{receipt.receivedOn}</dd>
        </dl>
        <p className="text-xs text-muted">
          Copy for your records, with your PAN masked. For the original receipt, please contact the temple office.
        </p>
        <div className="print:hidden"><Button onClick={() => window.print()}>Print</Button></div>
      </div>
    </Card>
  );
}
