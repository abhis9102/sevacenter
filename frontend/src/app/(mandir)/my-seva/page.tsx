"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MandirPageTitle, useMandirText } from "@/components/MandirShell";
import { Alert, Badge, Button, Card, TextField } from "@/components/ui";
import { displayPhone, type Channel, type DevoteeProfile, type MySeva } from "@/lib/devotee";
import { ApiError, describeError } from "@/lib/errors";
import { DEVOTEE_CHANGED } from "@/lib/temple";
import type { ReceiptDetail } from "@/lib/types";

const changed = () => window.dispatchEvent(new Event(DEVOTEE_CHANGED));

/**
 * Devotee sign-in with a one-time code, then "my seva" (ADR 0018), now across every phone and email
 * the devotee has verified, with their own profile (ADR 0025). The session is an HttpOnly cookie
 * scoped to /api/v1/portal; this page never sees it. 401 here just means "not signed in".
 */
export default function MySevaPage() {
  const tx = useMandirText();
  const [seva, setSeva] = useState<MySeva | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(
    () => api.get<MySeva>("/portal/me", { allowUnauthorized: true }).then(setSeva, () => setSeva(null))
      .finally(() => {
        setLoading(false);
        changed();
      }),
    [],
  );

  useEffect(() => {
    api.get<MySeva>("/portal/me", { allowUnauthorized: true }).then(setSeva, () => setSeva(null))
      .finally(() => setLoading(false));
  }, []);

  async function signOut() {
    await api.request("/portal/logout", { method: "POST", allowUnauthorized: true }).catch(() => undefined);
    setSeva(null);
    changed();
  }

  const update = (next: MySeva) => {
    setSeva(next);
    changed();
  };

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="user" title={seva?.profile.fullName ? tx.my.namaste(seva.profile.fullName) : tx.my.title}
                       subtitle={seva ? tx.my.subtitleIn : tx.my.subtitleOut}
                       actions={seva ? <Button variant="secondary" onClick={() => void signOut()}>{tx.my.signOut}</Button> : null} />
      {loading ? null : seva ? (
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_22rem]">
          <div className="flex flex-col gap-4"><SevaView seva={seva} /></div>
          <aside className="flex flex-col gap-4">
            <ProfileCard profile={seva.profile} onSaved={update} />
            <ContactsCard seva={seva} onLinked={update} />
          </aside>
        </div>
      ) : (
        <div className="mx-auto w-full max-w-md"><Verifier mode="login" onDone={() => void load()} /></div>
      )}
    </div>
  );
}

/**
 * Proves a phone or email with a one-time code: to sign in, or (signed in) to link it to this
 * account. Linking a contact that already had its own account merges the two.
 */
function Verifier({ mode, onDone, onCancel }: {
  mode: "login" | "link"; onDone: (seva?: MySeva) => void; onCancel?: () => void;
}) {
  const tx = useMandirText();
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
      setError(err instanceof ApiError && err.code === "too_many_contacts"
        ? tx.my.tooMany : describeError(err));
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
      if (mode === "login") {
        await api.request("/public/devotee-login/verify", { method: "POST", json: { channel, contact, code } });
        await api.refreshCsrf();
        onDone();
      } else {
        onDone(await api.request<MySeva>("/portal/contacts", { method: "POST", json: { channel, contact, code } }));
      }
    });
  };

  if (channels === null) return null;
  if (!channels.email && !channels.sms) {
    return <Card><p>{tx.my.notAvailable}</p></Card>;
  }

  const body = !sent ? (
    <form onSubmit={sendCode} className="flex flex-col gap-3" noValidate>
      <p className="text-sm text-muted">
        {mode === "login" ? tx.my.loginIntro : tx.my.linkIntro}
      </p>
      {channels.email && channels.sms ? (
        <div className="flex gap-2" role="group" aria-label={tx.my.sendBy}>
          <Button type="button" variant={channel === "SMS" ? "primary" : "secondary"} onClick={() => setChannel("SMS")}>{tx.my.mobileTab}</Button>
          <Button type="button" variant={channel === "EMAIL" ? "primary" : "secondary"} onClick={() => setChannel("EMAIL")}>{tx.my.emailTab}</Button>
        </div>
      ) : null}
      {channel === "SMS" ? (
        <TextField label={tx.common.mobile} inputMode="tel" autoComplete="tel" value={contact}
                   onChange={(e) => setContact(e.target.value)} error={fields.contact} />
      ) : (
        <TextField label={tx.my.emailLabel} type="email" autoComplete="email" value={contact}
                   onChange={(e) => setContact(e.target.value)} error={fields.contact} />
      )}
      {mode === "login" ? <p className="text-xs text-muted">{tx.my.useSame}</p> : null}
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <div className="flex gap-2">
        <Button type="submit" busy={busy}>{tx.my.sendCode}</Button>
        {onCancel ? <Button type="button" variant="secondary" onClick={onCancel}>{tx.common.cancel}</Button> : null}
      </div>
    </form>
  ) : (
    <form onSubmit={verify} className="flex flex-col gap-3" noValidate>
      <p className="text-sm">{tx.my.sentTo(contact)}</p>
      <TextField label={tx.my.codeLabel} inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code}
                 onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} />
      <p className="text-xs text-muted">{tx.my.neverAsk}</p>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <div className="flex gap-2">
        <Button type="submit" busy={busy}>{mode === "login" ? tx.my.signIn : tx.my.linkIt}</Button>
        <Button type="button" variant="secondary" onClick={() => { setSent(false); setCode(""); setError(null); }}>{tx.my.change}</Button>
      </div>
    </form>
  );
  return mode === "login" ? <Card>{body}</Card> : body;
}

function ContactsCard({ seva, onLinked }: { seva: MySeva; onLinked: (s: MySeva) => void }) {
  const tx = useMandirText();
  const [adding, setAdding] = useState(false);
  return (
    <Card className="flex flex-col gap-3">
      <div>
        <h2 className="text-base font-semibold">{tx.my.contactsTitle}</h2>
        <p className="text-xs text-muted">{tx.my.contactsNote}</p>
      </div>
      <ul className="flex flex-col gap-1.5 text-sm">
        {seva.contacts.map((c) => (
          <li key={c.channel + c.contact} className="flex items-center justify-between gap-2">
            <span className="truncate">{c.channel === "SMS" ? displayPhone(c.contact) : c.contact}</span>
            <Badge tone="success">{tx.my.verified}</Badge>
          </li>
        ))}
      </ul>
      {adding ? (
        <Verifier mode="link" onCancel={() => setAdding(false)}
                  onDone={(s) => { if (s) onLinked(s); setAdding(false); }} />
      ) : seva.contacts.length < 6 ? (
        <div><Button variant="secondary" onClick={() => setAdding(true)}>{tx.my.addContact}</Button></div>
      ) : null}
    </Card>
  );
}

const PROFILE_FIELDS: Array<[keyof DevoteeProfile, string?]> = [
  ["fullName"], ["gotra"], ["nakshatra"], ["rashi"], ["dateOfBirth", "date"], ["familyNames"],
  ["addressLine"], ["city"], ["state"], ["pincode"],
];

function ProfileCard({ profile, onSaved }: { profile: DevoteeProfile; onSaved: (s: MySeva) => void }) {
  const tx = useMandirText();
  const [editing, setEditing] = useState(false);
  const [f, setF] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});

  function start() {
    setF(Object.fromEntries(PROFILE_FIELDS.map(([k]) => [k, profile[k] ?? ""])));
    setError(null);
    setFields({});
    setEditing(true);
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      onSaved(await api.request<MySeva>("/portal/profile", {
        method: "PUT", json: Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v.trim() || null])),
      }));
      setEditing(false);
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  const filled = PROFILE_FIELDS.filter(([k]) => profile[k]);
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="text-base font-semibold">{tx.my.profileTitle}</h2>
          <p className="text-xs text-muted">{tx.my.profileNote}</p>
        </div>
        {!editing ? <Button variant="secondary" onClick={start}>{filled.length ? tx.my.edit : tx.my.add}</Button> : null}
      </div>
      {editing ? (
        <form onSubmit={save} className="flex flex-col gap-3" noValidate>
          {PROFILE_FIELDS.map(([k, type]) => (
            <TextField key={k} label={tx.my.fields[k]} type={type ?? "text"} value={f[k] ?? ""} error={fields[k]}
                       onChange={(e) => setF({ ...f, [k]: e.target.value })} />
          ))}
          {error ? <Alert tone="danger">{error}</Alert> : null}
          <div className="flex gap-2">
            <Button type="submit" busy={busy}>{tx.my.save}</Button>
            <Button type="button" variant="secondary" onClick={() => setEditing(false)}>{tx.common.cancel}</Button>
          </div>
        </form>
      ) : filled.length ? (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
          {filled.map(([k]) => (
            <div key={k} className="contents"><dt className="text-muted">{tx.my.fields[k]}</dt><dd>{profile[k]}</dd></div>
          ))}
        </dl>
      ) : (
        <p className="text-sm text-muted">{tx.my.profileEmpty}</p>
      )}
    </Card>
  );
}

function SevaView({ seva }: { seva: MySeva }) {
  const tx = useMandirText();
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
      {empty ? (
        <Card><p>{tx.my.nothing}</p></Card>
      ) : null}
      {seva.donations.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">{tx.my.donations}</h2>
          {receiptError ? <Alert tone="danger">{receiptError}</Alert> : null}
          <ul className="flex flex-col gap-2">
            {seva.donations.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{d.receivedOn} · ₹{d.amount}{d.purpose ? ` · ${d.purpose}` : ""}</span>
                <span className="flex items-center gap-2">
                  {d.reversed ? <Badge tone="warning">{tx.my.reversed}</Badge> : null}
                  {d.receiptNumber && d.receiptValid ? (
                    <Button variant="secondary" onClick={() => void openReceipt(d.id)}>{tx.my.receipt(d.receiptNumber)}</Button>
                  ) : d.receiptNumber ? <Badge tone="danger">{tx.my.receiptCancelled}</Badge> : <Badge>{tx.my.noReceipt}</Badge>}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
      {seva.pujaBookings.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">{tx.my.bookings}</h2>
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
          <h2 className="mb-3 font-semibold">{tx.my.passes}</h2>
          <ul className="flex flex-col gap-2">
            {seva.eventPasses.map((p) => (
              <li key={p.passCode} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{p.eventTitle ?? tx.my.event}{p.startsAt ? ` · ${new Date(p.startsAt).toLocaleString("en-IN")}` : ""} · {tx.common.people(p.attendeeCount)}</span>
                <span className="flex items-center gap-2"><code>{p.passCode}</code><Badge>{p.status}</Badge></span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
      {seva.sevakSignups.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">{tx.my.seva}</h2>
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
  const tx = useMandirText();
  return (
    <Card className="print:border-none">
      <div className="flex flex-col gap-3 text-sm">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold">{receipt.trustLegalName}</h2>
            <p className="text-muted">{receipt.trustAddress}</p>
            <p className="text-muted">PAN {receipt.trustPan} · 80G {receipt.trustRegistration80g}</p>
          </div>
          <Button variant="secondary" onClick={onClose} className="print:hidden">{tx.common.back}</Button>
        </div>
        <p className="font-semibold">{tx.my.receiptIssued(receipt.number, receipt.issuedOn)}</p>
        {receipt.cancelled ? <Alert tone="danger">{tx.my.receiptWasCancelled}</Alert> : null}
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt className="text-muted">{tx.my.receivedFrom}</dt><dd>{receipt.donorName}</dd>
          <dt className="text-muted">{tx.my.address}</dt><dd>{receipt.donorAddress}</dd>
          <dt className="text-muted">{tx.my.donorPan}</dt><dd>{receipt.donorPan}</dd>
          <dt className="text-muted">{tx.my.amount}</dt><dd>₹{receipt.amount}</dd>
          <dt className="text-muted">{tx.my.mode}</dt><dd>{receipt.mode}</dd>
          <dt className="text-muted">{tx.my.receivedOn}</dt><dd>{receipt.receivedOn}</dd>
        </dl>
        <p className="text-xs text-muted">{tx.my.copyNote}</p>
        <div className="print:hidden"><Button onClick={() => window.print()}>{tx.my.print}</Button></div>
      </div>
    </Card>
  );
}
