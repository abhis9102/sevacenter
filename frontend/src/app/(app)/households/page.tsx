"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { RELATIONS } from "@/components/HouseholdCard";
import { Alert, Button, Card, Dialog, PageHeader, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";

interface Summary {
  id: number;
  name: string;
  gotra: string | null;
  head: string | null;
  members: number;
}

interface Detail {
  id: number;
  name: string;
  gotra: string | null;
  addressLine: string | null;
  city: string | null;
  members: { memberId: number; devoteeId: number; fullName: string; relation: string }[];
  givingThisYear: string | null;
  masked: boolean;
}

const relation = (r: string) => RELATIONS.find((x) => x.value === r)?.label ?? r;
const inr = (v: string) => `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;

/** People → Households (ADR 0038, 0039): every family, searchable; open one to see its members. */
export default function HouseholdsPage() {
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<Summary[] | null>(null);
  const [open, setOpen] = useState<Detail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback((query: string) => api.get<Summary[]>(`/households?q=${encodeURIComponent(query)}`)
    .then(setRows).catch((e) => setError(describeError(e))), []);
  useEffect(() => {
    void load("");
  }, [load]);

  async function show(id: number) {
    try {
      setOpen(await api.get<Detail>(`/households/${id}`));
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Households" description="Families: who belongs together, their gotra and the family's giving. Devotees can also build their family themselves in My Mandir."
                  actions={<Link href="/devotees" className="text-sm font-medium text-primary-strong">Create from a devotee →</Link>} />
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <form method="post" className="flex max-w-xl items-end gap-2" onSubmit={(e) => { e.preventDefault(); void load(q.trim()); }}>
        <div className="flex-1"><TextField label="Search families" placeholder="e.g. Kulkarni" value={q} maxLength={120} onChange={(e) => setQ(e.target.value)} /></div>
        <Button type="submit" variant="secondary">Search</Button>
      </form>
      {rows?.length === 0 ? (
        <Card className="text-sm text-muted">No households yet. Open a devotee and choose <span className="font-semibold">Create household</span>, or let devotees add their family in My Mandir.</Card>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {rows?.map((h) => (
          <button key={h.id} type="button" onClick={() => void show(h.id)}
                  className="flex flex-col gap-1 rounded-[14px] border border-line bg-surface p-4 text-left transition-colors hover:border-primary/50">
            <span className="font-semibold">{h.name}</span>
            <span className="text-xs text-muted">{h.head ? `Head: ${h.head}` : "No head set"}{h.gotra ? ` · ${h.gotra}` : ""}</span>
            <span className="text-sm">{h.members} member{h.members === 1 ? "" : "s"}</span>
          </button>
        ))}
      </div>

      <Dialog open={open !== null} onClose={() => setOpen(null)} title={open?.name ?? ""}>
        {open ? (
          <div className="flex flex-col gap-3 text-sm">
            <p className="text-muted">{[open.gotra && `Gotra: ${open.gotra}`, !open.masked && [open.addressLine, open.city].filter(Boolean).join(", ")]
              .filter(Boolean).join(" · ") || "-"}</p>
            {open.givingThisYear !== null ? <p>Family giving this year: <span className="font-semibold">{inr(open.givingThisYear)}</span></p> : null}
            <ul className="divide-y divide-line rounded-[10px] border border-line">
              {open.members.map((m) => (
                <li key={m.memberId} className="flex items-center justify-between gap-2 px-3 py-2">
                  <Link href={`/devotees/${m.devoteeId}`} className="font-semibold text-primary-strong">{m.fullName}</Link>
                  <span className="text-muted">{relation(m.relation)}</span>
                </li>
              ))}
            </ul>
            <p className="text-xs text-muted">To change members, open any of them: the Household card is on their page.</p>
          </div>
        ) : null}
      </Dialog>
    </div>
  );
}
