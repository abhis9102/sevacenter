"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MandirPageTitle } from "@/components/MandirShell";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { loadCheckout, validAmount, type CheckoutSuccess } from "@/lib/razorpay";

interface Info {
  trustName: string | null;
  onlineDonations: boolean;
}

interface CreatedOrder {
  orderId: string;
  keyId: string;
  amountPaise: number;
}

interface Confirmed {
  donationId: number;
  amount: string;
  donorName: string;
  receivedOn: string;
}

const PRESETS = ["251", "501", "1100", "2100", "5100", "11000"];

/**
 * Public donation page on the trust's own host (ADR 0013). No sign-in. The server creates the
 * order and verifies the payment with Razorpay before recording anything.
 */
export default function DonatePage() {
  const [info, setInfo] = useState<Info | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [amount, setAmount] = useState("501");
  const [name, setName] = useState("");
  const [purpose, setPurpose] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [funds, setFunds] = useState<{ id: number; name: string }[]>([]);
  const [fundId, setFundId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<Confirmed | null>(null);

  useEffect(() => {
    api.get<Info>("/public/donations/info").then(setInfo).catch((err) => setLoadError(describeError(err)));
    api.get<{ id: number; name: string }[]>("/public/donation-funds").then(setFunds).catch(() => setFunds([]));
  }, []);

  async function onConfirm(paid: CheckoutSuccess) {
    try {
      const result = await api.request<Confirmed>("/public/donations/confirm", {
        method: "POST",
        json: { orderId: paid.razorpay_order_id, paymentId: paid.razorpay_payment_id, signature: paid.razorpay_signature },
      });
      setDone(result);
    } catch (err) {
      setError(
        `${describeError(err)} If money was taken, it is safe: the trust's records will pick it up. ` +
          `Please keep your payment reference ${paid.razorpay_payment_id}.`,
      );
    } finally {
      setBusy(false);
    }
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!validAmount(amount)) {
      setError("Enter an amount between ₹1 and ₹10,00,000 (up to 2 decimals).");
      return;
    }
    if (!name.trim()) {
      setError("Please enter your name.");
      return;
    }
    setBusy(true);
    try {
      const order = await api.request<CreatedOrder>("/public/donations/orders", {
        method: "POST",
        json: {
          amount: amount.trim(), donorName: name.trim(), purpose: purpose.trim() || undefined,
          phone: phone.trim() || undefined, email: email.trim() || undefined,
          fundId: fundId ? Number(fundId) : undefined,
        },
      });
      const Razorpay = await loadCheckout();
      new Razorpay({
        key: order.keyId,
        order_id: order.orderId,
        amount: order.amountPaise,
        currency: "INR",
        name: info?.trustName ?? "Donation",
        description: purpose.trim() || "Donation",
        prefill: { name: name.trim() },
        handler: (paid) => void onConfirm(paid),
        modal: { ondismiss: () => setBusy(false) },
      }).open();
    } catch (err) {
      setError(err instanceof Error && err.message === "checkout_unavailable"
        ? "The payment window couldn't load. Check your connection and try again."
        : describeError(err));
      setBusy(false);
    }
  }

  const chip = (active: boolean) => `rounded-[12px] border px-3 py-2.5 text-sm font-semibold transition-colors ${
    active ? "border-primary bg-primary text-white shadow-xs" : "border-line bg-surface hover:border-primary/50"
  }`;

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="donate" title="Seva & daan" subtitle={info?.trustName ? `Donate to ${info.trustName}` : undefined} />

      {loadError ? <Alert tone="danger">{loadError}</Alert> : null}
      {info && !info.onlineDonations ? (
        <Alert tone="info">This trust doesn&apos;t accept online donations yet. Please contact the temple office.</Alert>
      ) : null}

      {done ? (
        <Card className="flex flex-col gap-3 border-primary/30 bg-gradient-to-br from-primary/10 to-surface">
          <h2 className="text-lg font-medium">Thank you, {done.donorName} 🙏</h2>
          <p>
            Your donation of <strong>₹{done.amount}</strong> was received on {done.receivedOn}.
          </p>
          <p className="text-sm text-muted">
            This is a payment confirmation, not an 80G tax receipt. For an 80G receipt, contact the temple office with
            your PAN; they&apos;ll issue it from their records.
          </p>
        </Card>
      ) : info?.onlineDonations ? (
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <Card className="flex flex-col gap-4">
          <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-2 text-sm font-semibold">Choose an amount</legend>
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
                {PRESETS.map((p) => (
                  <button key={p} type="button" aria-pressed={amount === p} className={chip(amount === p)} onClick={() => setAmount(p)}>
                    ₹{Number(p).toLocaleString("en-IN")}
                  </button>
                ))}
              </div>
              <TextField label="Or enter an amount (₹)" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </fieldset>
            {funds.length > 0 ? (
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-2 text-sm font-semibold">Give to</legend>
                <div className="grid gap-2 sm:grid-cols-2">
                  {[{ id: "", name: "Where it's needed most" }, ...funds.map((f) => ({ id: String(f.id), name: f.name }))].map((f) => (
                    <button key={f.id || "general"} type="button" aria-pressed={fundId === f.id} onClick={() => setFundId(f.id)}
                            className={`${chip(fundId === f.id)} text-left`}>
                      {f.name}
                    </button>
                  ))}
                </div>
              </fieldset>
            ) : null}
            <TextField label="Your name" autoComplete="name" required value={name} onChange={(e) => setName(e.target.value)} />
            <TextField label="Purpose (optional)" placeholder="e.g. In memory of my grandmother" value={purpose}
                       onChange={(e) => setPurpose(e.target.value)} />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label="Mobile number (optional)" inputMode="tel" autoComplete="tel" value={phone}
                         onChange={(e) => setPhone(e.target.value)} />
              <TextField label="Email (optional)" type="email" autoComplete="email" value={email}
                         onChange={(e) => setEmail(e.target.value)} />
            </div>
            <p className="-mt-2 text-xs text-muted">Leave a mobile or email to see this donation and its receipt later in My Mandir.</p>
            {error ? <Alert tone="danger">{error}</Alert> : null}
            <Button type="submit" busy={busy}>
              Donate ₹{validAmount(amount) ? Number(amount.trim()).toLocaleString("en-IN") : "…"}
            </Button>
          </form>
        </Card>
        <aside className="flex flex-col gap-3 text-sm">
          <Card className="flex flex-col gap-2 bg-gradient-to-br from-haldi/15 to-surface">
            <h2 className="text-base font-semibold">Where your daan goes</h2>
            <p className="text-muted">
              Every rupee goes into {info.trustName ?? "the trust"}&apos;s own account and its books, earmarked to the
              fund you choose.
            </p>
          </Card>
          <Card className="flex flex-col gap-2">
            <h2 className="text-base font-semibold">80G tax receipt</h2>
            <p className="text-muted">Ask the temple office with your PAN; they issue it from the trust&apos;s records.</p>
          </Card>
          <Card className="flex flex-col gap-2">
            <h2 className="text-base font-semibold">Safe payment</h2>
            <p className="text-muted">Processed by Razorpay. Card and UPI details never reach this site.</p>
          </Card>
        </aside>
        </div>
      ) : null}
    </div>
  );
}
