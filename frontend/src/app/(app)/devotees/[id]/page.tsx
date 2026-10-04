"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { DevoteeForm } from "@/components/DevoteeForm";
import { MaskedNote } from "@/components/MaskedNote";
import { useLanguage } from "@/components/LanguageProvider";
import { useMe } from "@/components/Session";
import { CounterBookingDialog, IssuePassDialog, RegisterSevakDialog } from "@/components/StaffDialogs";
import { EmptyState, Pill, StatCard, useView, ViewSwitcher } from "@/components/staff";
import { Alert, Button, Card, Dialog, SelectField, Spinner, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import type { StaffEvent } from "@/lib/events";
import { formatDate, formatDateTime } from "@/lib/format";
import { todayIst, type Puja } from "@/lib/pujas";
import type { Team } from "@/lib/sevak";
import {
  CONSENT_LABELS, DONATION_MODES, hasRole, moduleAccess, type Devotee, type DonationFund, type Role, type StaffModule,
} from "@/lib/types";

/** GET /devotees/{id}/activity (ADR 0023). A null section = not visible to this user; [] = none. */
interface Activity {
  donations: {
    net: string;
    count: number;
    items: { id: number; receivedOn: string; amount: string; mode: string; purpose: string | null; fundId: number | null;
             reversed: boolean; receiptNumber: string | null }[];
  } | null;
  pujaBookings: { id: number; bookingCode: string; pujaName: string; pujaDate: string; amount: string; status: string }[] | null;
  eventPasses: { id: number; eventTitle: string | null; startsAt: string | null; attendeeCount: number; status: string; checkedIn: boolean }[] | null;
  sevaOffers: { id: number; sevaAreas: string; status: string; createdAt: string }[] | null;
}

const VIEWS = ["overview", "giving", "pujas", "passes", "seva"] as const;
const inr = (v: string) => `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;

export default function DevoteePageWrapper() {
  return (
    <Suspense>
      <DevoteeDetail />
    </Suspense>
  );
}

function DevoteeDetail() {
  const me = useMe();
  const { t } = useLanguage();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const search = useSearchParams();
  const id = /^\d{1,18}$/.test(params.id) ? params.id : null;

  const [devotee, setDevotee] = useState<Devotee | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string | null>(search.get("created") ? t.common.success : null);
  const [confirmErase, setConfirmErase] = useState(false);
  const [erasing, setErasing] = useState(false);
  const [eraseError, setEraseError] = useState<string | null>(null);

  const canEdit = hasRole(me.role, "LEADER");
  const canErase = hasRole(me.role, "TRUST_ADMIN");
  const can = (min: Role, module: StaffModule) => hasRole(me.role, min) && moduleAccess(me.moduleLimits, module) === "FULL";
  const view = useView(VIEWS);
  const [activity, setActivity] = useState<Activity | null>(null);
  const [action, setAction] = useState<"donation" | "puja" | "pass" | "sevak" | null>(null);
  const [receiptFor, setReceiptFor] = useState<number | null>(null);
  const [options, setOptions] = useState<{ pujas: Puja[]; events: StaffEvent[]; teams: Team[]; funds: DonationFund[] }>(
    { pujas: [], events: [], teams: [], funds: [] });

  const loadActivity = useCallback(() => {
    if (!id) return;
    api.get<Activity>(`/devotees/${id}/activity`).then((a) => {
      setActivity(a);
      // Fund names for the giving tab, only for staff who see donations.
      if (a.donations) {
        api.get<DonationFund[]>("/donation-funds").then((funds) => setOptions((o) => ({ ...o, funds }))).catch(() => undefined);
      }
    }).catch(() => setActivity(null));
  }, [id]);
  const fundName = (fundId: number | null) => options.funds.find((f) => f.id === fundId)?.name ?? null;

  useEffect(() => {
    loadActivity();
  }, [loadActivity]);

  async function open(next: "donation" | "puja" | "pass" | "sevak") {
    // The pickers' lists load when an action opens, so the page itself stays one read.
    const get = <T,>(path: string) => api.get<T>(path).catch(() => [] as unknown as T);
    const [pujas, events, teams, funds] = await Promise.all([
      next === "puja" ? get<Puja[]>("/pujas") : Promise.resolve(options.pujas),
      next === "pass" ? get<StaffEvent[]>("/events") : Promise.resolve(options.events),
      next === "sevak" ? get<Team[]>("/seva-teams") : Promise.resolve(options.teams),
      next === "donation" ? get<DonationFund[]>("/donation-funds") : Promise.resolve(options.funds),
    ]);
    setOptions({ pujas, events, teams, funds });
    setAction(next);
  }

  const done = (text: string) => {
    setAction(null);
    setReceiptFor(null);
    setNotice(text);
    loadActivity();
  };

  // The "just created" flag is one-shot: drop it from the URL so a reload or a shared link
  // doesn't repeat the message.
  useEffect(() => {
    if (id && search.get("created")) {
      router.replace(`/devotees/${id}`, { scroll: false });
    }
  }, [id, search, router]);

  useEffect(() => {
    if (!id) {
      return;
    }
    let cancelled = false;
    api
      .get<Devotee>(`/devotees/${id}`)
      .then((d) => !cancelled && setDevotee(d))
      .catch((err) => !cancelled && setError(describeError(err, t.errors)));
    return () => {
      cancelled = true;
    };
  }, [id, t.errors]);

  async function erase() {
    if (!devotee) {
      return;
    }
    setErasing(true);
    setEraseError(null);
    try {
      await api.request<void>(`/devotees/${devotee.id}`, { method: "DELETE" });
      router.replace("/devotees");
    } catch (err) {
      setEraseError(describeError(err, t.errors));
      setErasing(false);
    }
  }

  if (!id) {
    return <Alert tone="danger">{t.errors.not_found ?? "That isn't a valid devotee link."}</Alert>;
  }
  if (error) {
    return (
      <div className="flex flex-col gap-4">
        <Alert tone="danger">{error}</Alert>
        <BackLink label={t.devotees.importPage.backLink} />
      </div>
    );
  }
  if (!devotee) {
    return (
      <p className="flex items-center gap-2 text-muted">
        <Spinner /> {t.common.loading}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <BackLink label={t.devotees.importPage.backLink} />
      <div className="flex flex-col gap-4 border-b border-line pb-5 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold sm:text-3xl">{devotee.fullName}</h1>
            <Pill tone="primary">Devotee</Pill>
            {activity?.sevaOffers?.some((o) => o.status === "APPROVED") ? <Pill tone="success">Sevak</Pill> : null}
          </div>
          <p className="mt-1 text-sm text-muted">Devotee ID <span className="font-mono font-semibold text-fg">#{devotee.id}</span>
            {devotee.city ? ` · ${devotee.city}${devotee.state ? `, ${devotee.state}` : ""}` : ""}</p>
        </div>
        {!editing ? (
          <div className="flex flex-wrap gap-2">
            {can("LEADER", "DONATIONS") ? <Button onClick={() => void open("donation")}>+ Record donation</Button> : null}
            {can("LEADER", "PUJAS") ? <Button variant="secondary" onClick={() => void open("puja")}>+ Book puja</Button> : null}
            {can("MEMBER", "EVENTS") ? <Button variant="secondary" onClick={() => void open("pass")}>+ Issue pass</Button> : null}
            {can("LEADER", "VOLUNTEERS") && !devotee.masked ? <Button variant="secondary" onClick={() => void open("sevak")}>+ Enrol as sevak</Button> : null}
            {canEdit && !devotee.masked ? <Button variant="secondary" onClick={() => setEditing(true)}>{t.common.edit}</Button> : null}
            {canErase ? <Button variant="danger" onClick={() => setConfirmErase(true)}>{t.devotees.detail.eraseButton}</Button> : null}
          </div>
        ) : null}
      </div>
      {notice ? <Alert tone="success">{notice}</Alert> : null}
      {devotee.masked ? <MaskedNote /> : null}

      {editing ? (
        <Card className="max-w-3xl">
          <DevoteeForm
            devotee={devotee}
            onSaved={(d) => {
              setDevotee(d);
              setEditing(false);
              setNotice(t.common.success);
            }}
            onCancel={() => setEditing(false)}
          />
        </Card>
      ) : (
        <>
          {activity ? (
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              {activity.donations ? (
                <StatCard label="Lifetime giving" tone="success" value={inr(activity.donations.net)}
                          note={`${activity.donations.count} donation${activity.donations.count === 1 ? "" : "s"} recorded`} />
              ) : null}
              {activity.pujaBookings ? (
                <StatCard label="Pujas & sankalpas" tone="primary" value={activity.pujaBookings.filter((b) => b.status !== "CANCELLED").length}
                          note="Performed and scheduled" />
              ) : null}
              {activity.eventPasses ? (
                <StatCard label="Festival darshan" tone="info" value={activity.eventPasses.filter((p) => p.status !== "CANCELLED").length}
                          note="Utsav passes" />
              ) : null}
              {activity.sevaOffers ? (
                <StatCard label="Sevak hub" tone="maroon"
                          value={activity.sevaOffers.some((o) => o.status === "APPROVED") ? "Sevak" : activity.sevaOffers.length ? "Offered" : "Devotee"}
                          note={activity.sevaOffers.length ? activity.sevaOffers[0]!.sevaAreas : "Not enrolled as a sevak"} />
              ) : null}
            </div>
          ) : null}

          <ViewSwitcher label="Devotee views" current={view} views={[
            { id: "overview", label: "Overview & profile" },
            ...(activity?.donations ? [{ id: "giving" as const, label: `Giving & donations (${activity.donations.count})` }] : []),
            ...(activity?.pujaBookings ? [{ id: "pujas" as const, label: `Pujas & sankalpas (${activity.pujaBookings.length})` }] : []),
            ...(activity?.eventPasses ? [{ id: "passes" as const, label: `Festival passes (${activity.eventPasses.length})` }] : []),
            ...(activity?.sevaOffers ? [{ id: "seva" as const, label: "Sevak hub" }] : []),
          ]} />

          {view === "overview" ? (
            <div className="grid gap-6 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <h2 className="mb-4 text-lg">{t.devotees.detail.contactDetails}</h2>
                <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
                  <Field label={t.devotees.colPhone} value={devotee.phone} mono />
                  <Field label={t.devotees.colEmail} value={devotee.email} />
                  <Field label={t.devotees.form.addressLabel} value={devotee.addressLine} hidden={devotee.masked} wide hiddenLabel={t.devotees.contactHiddenNote} />
                  <Field label={t.devotees.colCity} value={devotee.city} />
                  <Field label={t.devotees.colState} value={devotee.state} />
                  <Field label={t.devotees.colPincode} value={devotee.pincode} hidden={devotee.masked} mono hiddenLabel={t.devotees.contactHiddenNote} />
                  <Field label={t.devotees.colDob} value={formatDate(devotee.dateOfBirth)} hidden={devotee.masked} hiddenLabel={t.devotees.contactHiddenNote} />
                </dl>
              </Card>
              <Card>
                <h2 className="mb-4 text-lg">{t.devotees.detail.consentHeading}</h2>
                <dl className="grid gap-4">
                  <Field
                    label={t.devotees.colConsent}
                    value={devotee.consentSource ? t.consent[devotee.consentSource] ?? CONSENT_LABELS[devotee.consentSource] ?? devotee.consentSource : null}
                  />
                  <Field label={t.devotees.detail.consentRecordedAt} value={formatDateTime(devotee.consentGivenAt)} />
                  <Field label={t.devotees.detail.createdAt} value={formatDateTime(devotee.createdAt)} />
                  <Field label={t.devotees.detail.updatedAt} value={formatDateTime(devotee.updatedAt)} />
                </dl>
                <p className="mt-4 border-t border-line pt-3 text-xs text-muted">Kept under the Digital Personal Data Protection Act, 2023, with the consent recorded above.</p>
              </Card>
              {activity ? (
                <div className="grid gap-3 sm:grid-cols-3 lg:col-span-3">
                  {activity.donations ? (
                    <Latest title="Latest donation" empty="No donations yet">
                      {activity.donations.items[0] ? <><span className="font-mono text-lg font-semibold text-success">{inr(activity.donations.items[0].amount)}</span>
                        <span className="block text-xs text-muted">{fundName(activity.donations.items[0].fundId) ?? activity.donations.items[0].purpose ?? "General fund"} · {formatDate(activity.donations.items[0].receivedOn)}</span></> : null}
                    </Latest>
                  ) : null}
                  {activity.pujaBookings ? (
                    <Latest title="Latest puja booking" empty="No pujas scheduled">
                      {activity.pujaBookings[0] ? <><span className="font-semibold">{activity.pujaBookings[0].pujaName}</span>
                        <span className="block text-xs text-muted">{formatDate(activity.pujaBookings[0].pujaDate)} · {activity.pujaBookings[0].status.toLowerCase()}</span></> : null}
                    </Latest>
                  ) : null}
                  {activity.eventPasses ? (
                    <Latest title="Latest darshan pass" empty="No festival passes">
                      {activity.eventPasses[0] ? <><span className="font-semibold">{activity.eventPasses[0].eventTitle ?? "Utsav"}</span>
                        <span className="block text-xs text-muted">{activity.eventPasses[0].attendeeCount} people · {activity.eventPasses[0].checkedIn ? "checked in" : activity.eventPasses[0].status.toLowerCase()}</span></> : null}
                    </Latest>
                  ) : null}
                </div>
              ) : null}
            </div>
          ) : view === "giving" && activity?.donations ? (
            activity.donations.items.length === 0 ? <EmptyState title="No donations recorded yet." /> : (
              <Card className="overflow-x-auto p-0">
                <table className="w-full text-left text-sm">
                  <thead className="bg-surface-2 text-xs uppercase tracking-wider text-muted">
                    <tr><th className="px-4 py-2">Date</th><th className="px-4 py-2">Amount</th><th className="px-4 py-2">Mode</th>
                      <th className="px-4 py-2">Fund / purpose</th><th className="px-4 py-2">80G receipt</th></tr>
                  </thead>
                  <tbody>
                    {activity.donations.items.map((d) => (
                      <tr key={d.id} className={`border-t border-line ${d.reversed ? "opacity-60" : ""}`}>
                        <td className="px-4 py-2 font-mono text-xs">{d.receivedOn}</td>
                        <td className="px-4 py-2 font-mono font-semibold">{inr(d.amount)}{d.reversed ? <span className="ml-2"><Pill tone="warning">Reversed</Pill></span> : null}</td>
                        <td className="px-4 py-2 font-mono text-xs">{d.mode}</td>
                        <td className="px-4 py-2 text-muted">{fundName(d.fundId) ?? "General fund"}{d.purpose && d.purpose !== fundName(d.fundId) ? ` · ${d.purpose}` : ""}</td>
                        <td className="px-4 py-2">
                          {d.receiptNumber ? <span className="font-mono text-xs">{d.receiptNumber}</span>
                            : !d.reversed && can("LEADER", "DONATIONS") ? <Button variant="secondary" onClick={() => setReceiptFor(d.id)}>Issue 80G receipt</Button>
                            : <span className="text-muted">—</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>
            )
          ) : view === "pujas" && activity?.pujaBookings ? (
            <ItemList empty="No pujas or sankalpas booked yet." items={activity.pujaBookings.map((b) => ({
              key: b.id, title: b.pujaName, meta: `${formatDate(b.pujaDate)} · ${b.bookingCode}`, tag: b.status.toLowerCase(),
              right: Number(b.amount) > 0 ? inr(b.amount) : "No dakshina" }))} />
          ) : view === "passes" && activity?.eventPasses ? (
            <ItemList empty="No festival passes issued yet." items={activity.eventPasses.map((p) => ({
              key: p.id, title: p.eventTitle ?? "Utsav", meta: p.startsAt ? formatDateTime(p.startsAt) : "",
              tag: p.checkedIn ? "checked in" : p.status.toLowerCase(), right: `${p.attendeeCount} people` }))} />
          ) : view === "seva" && activity?.sevaOffers ? (
            <ItemList empty="Not enrolled as a sevak yet." items={activity.sevaOffers.map((o) => ({
              key: o.id, title: o.sevaAreas, meta: formatDate(o.createdAt), tag: o.status.toLowerCase(), right: "" }))} />
          ) : null}
        </>
      )}

      {action === "donation" ? (
        <RecordDonationDialog devotee={devotee} funds={options.funds} onClose={() => setAction(null)}
                              onDone={(amount) => done(`Donation of ${inr(amount)} recorded for ${devotee.fullName}.`)} />
      ) : null}
      {action === "puja" ? (
        <CounterBookingDialog catalog={options.pujas.filter((p) => p.active)} date={todayIst()} onClose={() => setAction(null)}
                              initial={devotee.masked ? { devoteeName: devotee.fullName }
                                : { devoteeName: devotee.fullName, phone: devotee.phone ?? "", email: devotee.email ?? "" }}
                              onBooked={(b) => done(`${b.pujaName} booked for ${b.pujaDate} (code ${b.bookingCode}).`)} />
      ) : null}
      {action === "pass" ? (
        <IssuePassDialog events={options.events.filter((e) => e.status === "PUBLISHED" && Date.parse(e.endsAt) > Date.parse(`${todayIst()}T00:00:00+05:30`))}
                         onClose={() => setAction(null)}
                         initial={devotee.masked ? { name: devotee.fullName }
                           : { name: devotee.fullName, phone: devotee.phone ?? "", email: devotee.email ?? "" }}
                         onIssued={(p, e) => done(`Pass ${p.passCode} issued for ${e.title}.`)} />
      ) : null}
      {action === "sevak" ? (
        <RegisterSevakDialog teams={options.teams} teamId={null} onClose={() => setAction(null)}
                             initial={{ fullName: devotee.fullName, phone: devotee.phone ?? "", email: devotee.email ?? "" }}
                             onSaved={(n) => done(`${n} enrolled as a sevak.`)} />
      ) : null}
      {receiptFor !== null ? (
        <ReceiptDialog donationId={receiptFor} devotee={devotee} onClose={() => setReceiptFor(null)}
                       onDone={(no) => done(`80G receipt ${no} issued.`)} />
      ) : null}

      <Dialog open={confirmErase} onClose={() => setConfirmErase(false)} title={t.devotees.detail.eraseConfirmTitle}>
        <p>
          {t.devotees.detail.eraseConfirmMessage}
        </p>
        <p className="text-sm text-muted">
          {t.common.permanentAction}
        </p>
        {eraseError ? <Alert tone="danger">{eraseError}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmErase(false)}>
            {t.common.cancel}
          </Button>
          <Button variant="danger" busy={erasing} onClick={erase}>
            {t.devotees.detail.eraseConfirmAction}
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

function BackLink({ label }: { label: string }) {
  return (
    <Link href="/devotees" className="text-sm text-primary-strong hover:underline">
      {label}
    </Link>
  );
}

function Field({
  label,
  value,
  hidden = false,
  mono = false,
  wide = false,
  hiddenLabel = "Hidden for your role",
}: {
  label: string;
  value: string | null | undefined;
  hidden?: boolean;
  mono?: boolean;
  wide?: boolean;
  hiddenLabel?: string;
}) {
  return (
    <div className={wide ? "sm:col-span-2" : ""}>
      <dt className="text-xs uppercase tracking-wide text-muted">{label}</dt>
      <dd className={`mt-0.5 ${mono && !hidden && value ? "font-mono text-sm" : ""}`}>
        {hidden ? (
          <span className="text-muted italic">{hiddenLabel}</span>
        ) : value ? (
          value
        ) : (
          <span className="text-muted">—</span>
        )}
      </dd>
    </div>
  );
}

function Latest({ title, empty, children }: { title: string; empty: string; children: React.ReactNode }) {
  return (
    <div className="rounded-[12px] border border-line bg-surface-2 p-4 text-sm">
      <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-muted">{title}</p>
      {children ?? <span className="text-muted">{empty}</span>}
    </div>
  );
}

function ItemList({ items, empty }: {
  items: { key: number; title: string; meta: string; tag: string; right: string }[]; empty: string;
}) {
  if (items.length === 0) return <EmptyState title={empty} />;
  return (
    <Card className="p-0">
      <ul className="divide-y divide-line">
        {items.map((i) => (
          <li key={i.key} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
            <span className="min-w-0"><span className="font-semibold">{i.title}</span>
              <span className="block text-xs text-muted">{i.meta}</span></span>
            <span className="flex shrink-0 items-center gap-3"><span className="font-mono text-xs">{i.right}</span><Pill>{i.tag}</Pill></span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function RecordDonationDialog({ devotee, funds, onClose, onDone }: {
  devotee: Devotee; funds: DonationFund[]; onClose: () => void; onDone: (amount: string) => void;
}) {
  const [f, setF] = useState({ amount: "", mode: "UPI", fundId: "", purpose: "", reference: "", receivedOn: todayIst() });
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
      await api.request("/donations", { method: "POST", json: {
        devoteeId: devotee.id, amount: f.amount.trim(), mode: f.mode, fundId: f.fundId ? Number(f.fundId) : null,
        purpose: f.purpose.trim() || null, reference: f.reference.trim() || null, receivedOn: f.receivedOn } });
      onDone(f.amount.trim());
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={`Record a donation · ${devotee.fullName}`}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Amount (₹)" inputMode="decimal" value={f.amount} onChange={set("amount")} error={fields.amount} />
          <SelectField label="Mode" value={f.mode} onChange={set("mode")} options={DONATION_MODES.map((m) => ({ value: m, label: m.replace("_", " ") }))} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <SelectField label="Fund" value={f.fundId} onChange={set("fundId")}
                       options={[{ value: "", label: "General fund" }, ...funds.filter((x) => x.active).map((x) => ({ value: String(x.id), label: x.name }))]} />
          <TextField label="Received on" type="date" value={f.receivedOn} onChange={set("receivedOn")} error={fields.receivedOn} />
        </div>
        <TextField label="Purpose (optional)" value={f.purpose} onChange={set("purpose")} />
        <TextField label="Reference (optional)" placeholder="UTR / cheque no." value={f.reference} onChange={set("reference")} />
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Record donation</Button>
        </div>
      </form>
    </Dialog>
  );
}

function ReceiptDialog({ donationId, devotee, onClose, onDone }: {
  donationId: number; devotee: Devotee; onClose: () => void; onDone: (number: string) => void;
}) {
  const [pan, setPan] = useState("");
  const [address, setAddress] = useState(devotee.masked ? ""
    : [devotee.addressLine, devotee.city, devotee.state, devotee.pincode].filter(Boolean).join(", "));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      const r = await api.request<{ number: string }>(`/donations/${donationId}/receipt`, { method: "POST",
        json: { donorPan: pan.trim().toUpperCase(), donorAddress: address.trim() } });
      onDone(r.number);
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title="Issue an 80G receipt">
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField label="Donor PAN" value={pan} onChange={(e) => setPan(e.target.value)} error={fields.donorPan}
                   hint="Stored encrypted; shown masked afterwards" />
        <TextField label="Donor address" value={address} onChange={(e) => setAddress(e.target.value)} error={fields.donorAddress} />
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Issue receipt</Button>
        </div>
      </form>
    </Dialog>
  );
}
