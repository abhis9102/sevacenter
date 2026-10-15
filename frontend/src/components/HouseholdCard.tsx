"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Alert, Button, Card, Dialog, SelectField, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";

type Relation = "HEAD" | "SPOUSE" | "CHILD" | "PARENT" | "SIBLING" | "GRANDPARENT" | "GRANDCHILD" | "OTHER";

interface Household {
  id: number;
  name: string;
  gotra: string | null;
  addressLine: string | null;
  city: string | null;
  state: string | null;
  pincode: string | null;
  notes: string | null;
  members: { memberId: number; devoteeId: number; fullName: string; relation: Relation }[];
  givingThisYear: string | null;
  masked: boolean;
}

export const RELATIONS: { value: Relation; label: string }[] = [
  { value: "HEAD", label: "Head of family" }, { value: "SPOUSE", label: "Spouse" }, { value: "CHILD", label: "Child" },
  { value: "PARENT", label: "Parent" }, { value: "SIBLING", label: "Sibling" }, { value: "GRANDPARENT", label: "Grandparent" },
  { value: "GRANDCHILD", label: "Grandchild" }, { value: "OTHER", label: "Other" },
];
const inr = (v: string) => `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
const relationLabel = (r: Relation) => RELATIONS.find((x) => x.value === r)?.label ?? r;

interface Seed {
  id: number;
  fullName: string;
  addressLine: string | null;
  city: string | null;
  state: string | null;
  pincode: string | null;
}

/** A devotee's household (ADR 0038). LEADER+ build it; everyone with Devotees access sees it (masked for members). */
export function HouseholdCard({ devotee, canEdit }: { devotee: Seed; canEdit: boolean }) {
  const [household, setHousehold] = useState<Household | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<"create" | "edit" | "add" | null>(null);

  const load = useCallback(async () => {
    try {
      setHousehold(await api.get<Household>(`/devotees/${devotee.id}/household`));
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) setHousehold(null);
      else setError(describeError(err));
    }
  }, [devotee.id]);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load
    void load();
  }, [load]);

  async function run(fn: () => Promise<unknown>) {
    setError(null);
    try {
      await fn();
      await load();
    } catch (err) {
      setError(describeError(err));
    }
  }

  if (household === undefined) return null;
  const address = household ? [household.addressLine, household.city, household.state, household.pincode].filter(Boolean).join(", ") : "";

  return (
    <Card className="flex flex-col gap-3 lg:col-span-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="text-lg">Household</h2>
          <p className="text-sm text-muted">The family as one unit: shared address and gotra, everyone&apos;s place in it.</p>
        </div>
        {canEdit ? (
          household ? (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setDialog("add")}>Add family member</Button>
              <Button variant="secondary" onClick={() => setDialog("edit")}>Edit</Button>
            </div>
          ) : <Button variant="secondary" onClick={() => setDialog("create")}>Create household</Button>
        ) : null}
      </div>
      {error ? <Alert tone="danger">{error}</Alert> : null}

      {household === null ? <p className="text-sm text-muted">Not part of a household yet.</p> : (
        <>
          <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
            <div><dt className="text-muted">Family</dt><dd className="font-semibold">{household.name}</dd></div>
            <div><dt className="text-muted">Gotra</dt><dd>{household.gotra ?? "-"}</dd></div>
            {household.givingThisYear !== null ? (
              <div><dt className="text-muted">Family giving this year</dt><dd className="font-semibold">{inr(household.givingThisYear)}</dd></div>
            ) : null}
            {!household.masked ? <div className="sm:col-span-3"><dt className="text-muted">Address</dt><dd>{address || "-"}</dd></div> : null}
          </dl>
          <ul className="divide-y divide-line rounded-[10px] border border-line">
            {household.members.map((m) => (
              <li key={m.memberId} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                <span>
                  {m.devoteeId === devotee.id ? <span className="font-semibold">{m.fullName}</span>
                    : <Link href={`/devotees/${m.devoteeId}`} className="font-semibold text-primary-strong">{m.fullName}</Link>}
                  <span className="ml-2 text-muted">{relationLabel(m.relation)}</span>
                </span>
                {canEdit ? (
                  <span className="flex items-center gap-2">
                    <select aria-label={`Relation of ${m.fullName}`} value={m.relation}
                            className="rounded-[8px] border border-line bg-surface px-2 py-1 text-xs"
                            onChange={(e) => void run(() => api.request(`/households/${household.id}/members/${m.memberId}`,
                              { method: "PUT", json: { relation: e.target.value } }))}>
                      {RELATIONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                    </select>
                    <Button variant="secondary" onClick={() => void run(() => api.request(
                      `/households/${household.id}/members/${m.memberId}`, { method: "DELETE" }))}>Remove</Button>
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
          {household.notes && !household.masked ? <p className="whitespace-pre-line text-xs text-muted">{household.notes}</p> : null}
        </>
      )}

      {dialog === "create" || dialog === "edit" ? (
        <HouseholdForm devotee={devotee} household={dialog === "edit" ? household ?? null : null}
                       onClose={() => setDialog(null)} onSaved={() => { setDialog(null); void load(); }}
                       onDeleted={() => { setDialog(null); void load(); }} />
      ) : null}
      {dialog === "add" && household ? (
        <AddMemberDialog householdId={household.id} onClose={() => setDialog(null)} onAdded={() => { setDialog(null); void load(); }} />
      ) : null}
    </Card>
  );
}

function HouseholdForm({ devotee, household, onClose, onSaved, onDeleted }: {
  devotee: Seed; household: Household | null; onClose: () => void; onSaved: () => void; onDeleted: () => void;
}) {
  const surname = devotee.fullName.trim().split(/\s+/).slice(-1)[0] ?? "";
  const [f, setF] = useState({
    name: household?.name ?? (surname ? `${surname} family` : ""), gotra: household?.gotra ?? "",
    addressLine: household?.addressLine ?? devotee.addressLine ?? "", city: household?.city ?? devotee.city ?? "",
    state: household?.state ?? devotee.state ?? "", pincode: household?.pincode ?? devotee.pincode ?? "",
    notes: household?.notes ?? "",
  });
  const [relation, setRelation] = useState<Relation>("HEAD");
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value });

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFields({});
    const body = Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v.trim() || null]));
    try {
      if (household) await api.request(`/households/${household.id}`, { method: "PUT", json: body });
      else await api.request("/households", { method: "POST", json: { household: body, devoteeId: devotee.id, relation } });
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={household ? "Edit household" : "Create household"}>
      <form method="post" onSubmit={save} className="flex flex-col gap-3">
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Family name" value={f.name} maxLength={120} onChange={set("name")} error={fields.name} required />
          <TextField label="Family gotra" value={f.gotra} maxLength={60} onChange={set("gotra")} />
        </div>
        {!household ? (
          <SelectField label={`${devotee.fullName} is the`} value={relation} options={RELATIONS}
                       onChange={(e) => setRelation(e.target.value as Relation)} />
        ) : null}
        <TextField label="Address" value={f.addressLine} maxLength={200} onChange={set("addressLine")} />
        <div className="grid gap-3 sm:grid-cols-3">
          <TextField label="City" value={f.city} maxLength={80} onChange={set("city")} />
          <TextField label="State" value={f.state} maxLength={80} onChange={set("state")} />
          <TextField label="Pincode" value={f.pincode} maxLength={6} onChange={set("pincode")} error={fields.pincode} />
        </div>
        <TextField label="Notes (optional)" value={f.notes} maxLength={500} onChange={set("notes")} />
        <div className="flex justify-between gap-2">
          <Button type="submit" busy={busy}>Save</Button>
          {household ? (
            <Button type="button" variant="secondary" onClick={() => {
              if (!window.confirm("Delete this household? The devotee records stay; only the family link goes.")) return;
              void api.request(`/households/${household.id}`, { method: "DELETE" }).then(onDeleted).catch((err) => setError(describeError(err)));
            }}>Delete household</Button>
          ) : null}
        </div>
      </form>
    </Dialog>
  );
}

function AddMemberDialog({ householdId, onClose, onAdded }: { householdId: number; onClose: () => void; onAdded: () => void }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<{ id: number; fullName: string; city: string | null }[]>([]);
  const [relation, setRelation] = useState<Relation>("SPOUSE");
  const [error, setError] = useState<string | null>(null);

  async function search(e: React.FormEvent) {
    e.preventDefault();
    try {
      const page = await api.get<{ items: { id: number; fullName: string; city: string | null }[] }>(
        `/devotees?q=${encodeURIComponent(q.trim())}&page=0&size=10`);
      setResults(page.items);
    } catch (err) {
      setError(describeError(err));
    }
  }

  async function add(devoteeId: number) {
    try {
      await api.request(`/households/${householdId}/members`, { method: "POST", json: { devoteeId, relation } });
      onAdded();
    } catch (err) {
      setError(err instanceof ApiError && err.status === 409
        ? "That devotee is already in another household. Remove them there first." : describeError(err));
    }
  }

  return (
    <Dialog open onClose={onClose} title="Add family member">
      <div className="flex flex-col gap-3">
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <SelectField label="Relation" value={relation} options={RELATIONS} onChange={(e) => setRelation(e.target.value as Relation)} />
        <form method="post" onSubmit={search} className="flex items-end gap-2">
          <div className="flex-1"><TextField label="Find devotee by name" value={q} maxLength={120} onChange={(e) => setQ(e.target.value)} /></div>
          <Button type="submit" variant="secondary" disabled={!q.trim()}>Search</Button>
        </form>
        <ul className="divide-y divide-line">
          {results.map((d) => (
            <li key={d.id} className="flex items-center justify-between gap-2 py-2 text-sm">
              <span>{d.fullName} <span className="text-muted">{d.city}</span></span>
              <Button onClick={() => void add(d.id)}>Add</Button>
            </li>
          ))}
        </ul>
        <p className="text-xs text-muted">Not in the list? Add them as a devotee first, then add them here.</p>
      </div>
    </Dialog>
  );
}
