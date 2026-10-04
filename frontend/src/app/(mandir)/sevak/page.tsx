"use client";

import { useState } from "react";

import { api } from "@/components/apiClient";
import { MandirPageTitle, useDevotee, useMandirText } from "@/components/MandirShell";
import { prefill } from "@/lib/devotee";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";

/** Public volunteer signup on the trust's own host (ADR 0015). No sign-in. */
export default function SevakPage() {
  const tx = useMandirText();
  // null = untouched: a signed-in devotee's own details fill it (ADR 0025).
  const pre = prefill(useDevotee());
  const [formIn, setForm] = useState<Record<"fullName" | "phone" | "email" | "sevaAreas" | "availability" | "notes", string> | null>(null);
  const form = formIn ?? { fullName: pre.name, phone: pre.phone, email: pre.email, sevaAreas: "", availability: "", notes: "" };
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      const r = await api.request<{ name: string }>("/public/sevak", {
        method: "POST",
        json: Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v.trim() || null])),
      });
      setDone(r.name);
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  function toggleArea(area: string) {
    const list = form.sevaAreas.split(",").map((x) => x.trim()).filter(Boolean);
    const next = list.includes(area) ? list.filter((x) => x !== area) : [...list, area];
    setForm({ ...form, sevaAreas: next.join(", ") });
  }

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="sevak" title={tx.sevak.title} subtitle={tx.sevak.subtitle} />
      {done ? (
        <Card className="mx-auto w-full max-w-md border-primary/30 bg-gradient-to-br from-primary/10 to-surface text-center">
          <p className="text-lg font-medium">{tx.sevak.thanks(done)}</p>
          <p className="text-sm text-muted">{tx.sevak.callYou}</p>
        </Card>
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-[18rem_1fr]">
          <Card className="flex flex-col gap-3 bg-gradient-to-br from-haldi/15 to-surface">
            <h2 className="text-base font-semibold">{tx.sevak.ways}</h2>
            <p className="text-sm text-muted">{tx.sevak.tap}</p>
            <div className="flex flex-wrap gap-2">
              {tx.sevak.areas.map((area) => {
                const on = form.sevaAreas.split(",").map((x) => x.trim()).includes(area);
                return (
                  <button key={area} type="button" aria-pressed={on} onClick={() => toggleArea(area)}
                          className={`rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ${
                            on ? "border-primary bg-primary text-white" : "border-line bg-surface hover:border-primary/50"}`}>
                    {area}
                  </button>
                );
              })}
            </div>
          </Card>
        <Card>
          <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-2" noValidate>
            <TextField label={tx.common.yourName} autoComplete="name" value={form.fullName} onChange={set("fullName")} error={fields.fullName} />
            <TextField label={tx.common.mobile} inputMode="tel" autoComplete="tel" value={form.phone} onChange={set("phone")} error={fields.phone} />
            <TextField label={tx.common.emailIfNoMobile} type="email" autoComplete="email" value={form.email} onChange={set("email")} error={fields.email} />
            <TextField label={tx.sevak.availability} placeholder={tx.sevak.availabilityHint} value={form.availability}
                       onChange={set("availability")} />
            <div className="sm:col-span-2">
              <TextField label={tx.sevak.help} placeholder={tx.sevak.helpHint}
                         value={form.sevaAreas} onChange={set("sevaAreas")} error={fields.sevaAreas} />
            </div>
            <div className="sm:col-span-2">
              <TextField label={tx.sevak.notes} value={form.notes} onChange={set("notes")} />
            </div>
            <div className="flex flex-col gap-3 sm:col-span-2">
              <p className="text-xs text-muted">{tx.sevak.privacy}</p>
              {error ? <Alert tone="danger">{error}</Alert> : null}
              <div><Button type="submit" busy={busy}>{tx.sevak.submit}</Button></div>
            </div>
          </form>
        </Card>
        </div>
      )}
    </div>
  );
}
