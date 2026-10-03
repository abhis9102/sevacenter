"use client";

import { useState } from "react";

import { api } from "@/components/apiClient";
import { Diya } from "@/components/Diya";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";

/** Public volunteer signup on the trust's own host (ADR 0015). No sign-in. */
export default function SevakPage() {
  const [form, setForm] = useState({ fullName: "", phone: "", email: "", sevaAreas: "", availability: "", notes: "" });
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

  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col gap-6 px-4 py-10">
      <header className="flex items-center gap-3">
        <Diya className="size-9" />
        <h1 className="text-xl font-semibold">Offer seva</h1>
      </header>
      {done ? (
        <Card><p>Thank you, {done}. The temple team will contact you.</p></Card>
      ) : (
        <Card>
          <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
            <TextField label="Your name" autoComplete="name" value={form.fullName} onChange={set("fullName")} error={fields.fullName} />
            <TextField label="Mobile number" inputMode="tel" autoComplete="tel" value={form.phone} onChange={set("phone")} error={fields.phone} />
            <TextField label="Email (if no mobile)" type="email" autoComplete="email" value={form.email} onChange={set("email")} error={fields.email} />
            <TextField label="How would you like to help?" placeholder="e.g. Annadanam kitchen, festival crowd management"
                       value={form.sevaAreas} onChange={set("sevaAreas")} error={fields.sevaAreas} />
            <TextField label="When are you available? (optional)" placeholder="e.g. weekends" value={form.availability}
                       onChange={set("availability")} />
            <TextField label="Anything else? (optional)" value={form.notes} onChange={set("notes")} />
            <p className="text-xs text-muted">Your details are seen only by the temple&apos;s organisers.</p>
            {error ? <Alert tone="danger">{error}</Alert> : null}
            <div><Button type="submit" busy={busy}>Send</Button></div>
          </form>
        </Card>
      )}
    </main>
  );
}
