"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, PageHeader, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatDate } from "@/lib/format";
import { hasRole } from "@/lib/types";

interface Settings {
  keyId: string;
  live: boolean;
  updatedAt: string;
}

/**
 * Connect the trust's own Razorpay account (ADR 0013). The key secret is write-only: it is
 * checked against Razorpay, stored encrypted, and never shown again.
 */
export default function PaymentsPage() {
  const me = useMe();
  const isAdmin = hasRole(me.role, "TRUST_ADMIN");
  const [settings, setSettings] = useState<Settings | null | undefined>(undefined);
  const [keyId, setKeyId] = useState("");
  const [keySecret, setKeySecret] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Readonly<Record<string, string>>>({});

  const load = useCallback(async () => {
    try {
      setSettings(await api.get<Settings>("/payment-settings"));
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setSettings(null);
      } else {
        setNotice({ tone: "danger", text: describeError(err) });
      }
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load
    if (isAdmin) void load();
  }, [isAdmin, load]);

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice(null);
    setFieldErrors({});
    try {
      setSettings(await api.request<Settings>("/payment-settings", { method: "PUT", json: { keyId, keySecret } }));
      setKeySecret("");
      setNotice({ tone: "success", text: "Razorpay connected. Online donations are now open." });
    } catch (err) {
      if (err instanceof ApiError) setFieldErrors(err.fields);
      setNotice({ tone: "danger", text: describeError(err) });
    } finally {
      setBusy(false);
    }
  }

  async function onReconcile() {
    setBusy(true);
    setNotice(null);
    try {
      const r = await api.request<{ settled: number }>("/payment-settings/reconcile", { method: "POST" });
      setNotice({
        tone: "success",
        text: r.settled === 0 ? "No pending payments to recover." : `Recovered ${r.settled} payment(s) into the ledger.`,
      });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    } finally {
      setBusy(false);
    }
  }

  if (!isAdmin) {
    return <Alert tone="warning">Only trust admins can manage payment settings.</Alert>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Payments" description="Online donations go straight into this trust's own Razorpay account." />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}

      <Card className="flex max-w-2xl flex-col gap-4">
        {settings ? (
          <p className="text-sm">
            Connected: <span className="font-mono">{settings.keyId}</span>{" "}
            {settings.live ? "(live)" : "(test mode: no real money)"} · updated {formatDate(settings.updatedAt)}
          </p>
        ) : settings === null ? (
          <p className="text-sm text-muted">Not connected yet. Devotees can&apos;t donate online until you connect.</p>
        ) : null}
        <form onSubmit={onSave} className="flex flex-col gap-3">
          <TextField label="Razorpay key id" placeholder="rzp_test_… or rzp_live_…" value={keyId}
                     onChange={(e) => setKeyId(e.target.value)} error={fieldErrors.keyId} autoComplete="off" />
          <TextField label="Razorpay key secret" type="password" value={keySecret}
                     onChange={(e) => setKeySecret(e.target.value)} error={fieldErrors.keySecret} autoComplete="off" />
          <p className="text-xs text-muted">
            From Razorpay Dashboard → Account &amp; Settings → API keys. The secret is checked with Razorpay, stored
            encrypted, and never shown again.
          </p>
          <div>
            <Button type="submit" busy={busy} disabled={!keyId.trim() || !keySecret.trim()}>
              {settings ? "Replace keys" : "Connect Razorpay"}
            </Button>
          </div>
        </form>
      </Card>

      {settings ? (
        <Card className="flex max-w-2xl flex-col gap-3">
          <h2 className="font-medium">Donate page</h2>
          <p className="text-sm">
            Share <a className="font-mono text-primary-strong underline" href="/donate">this trust&apos;s /donate page</a>{" "}
            with devotees.
          </p>
          <h2 className="pt-2 font-medium">Recover pending payments</h2>
          <p className="text-sm text-muted">
            If a devotee paid but closed the browser before returning, the payment is safe with Razorpay. This asks
            Razorpay about unfinished payments and records the captured ones.
          </p>
          <div>
            <Button variant="secondary" busy={busy} onClick={() => void onReconcile()}>
              Recover pending payments
            </Button>
          </div>
        </Card>
      ) : null}
    </div>
  );
}
