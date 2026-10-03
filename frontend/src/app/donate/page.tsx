"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya } from "@/components/Diya";
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

const PRESETS = ["101", "501", "1100", "2100"];

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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<Confirmed | null>(null);

  useEffect(() => {
    api.get<Info>("/public/donations/info").then(setInfo).catch((err) => setLoadError(describeError(err)));
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

  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col gap-6 px-4 py-10">
      <header className="flex items-center gap-3">
        <Diya className="size-9" />
        <div>
          <p className="text-xs uppercase tracking-wide text-muted">Donate to</p>
          <h1 className="text-xl font-semibold">{info?.trustName ?? "…"}</h1>
        </div>
      </header>

      {loadError ? <Alert tone="danger">{loadError}</Alert> : null}
      {info && !info.onlineDonations ? (
        <Alert tone="info">This trust doesn&apos;t accept online donations yet. Please contact the temple office.</Alert>
      ) : null}

      {done ? (
        <Card className="flex flex-col gap-3">
          <h2 className="text-lg font-medium">Thank you, {done.donorName}</h2>
          <p>
            Your donation of <strong>₹{done.amount}</strong> was received on {done.receivedOn}.
          </p>
          <p className="text-sm text-muted">
            This is a payment confirmation, not an 80G tax receipt. For an 80G receipt, contact the temple office with
            your PAN; they&apos;ll issue it from their records.
          </p>
        </Card>
      ) : info?.onlineDonations ? (
        <Card className="flex flex-col gap-4">
          <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
            <fieldset className="flex flex-col gap-2">
              <legend className="text-sm font-medium">Amount (₹)</legend>
              <div className="flex flex-wrap gap-2">
                {PRESETS.map((p) => (
                  <Button key={p} type="button" variant={amount === p ? "primary" : "secondary"} onClick={() => setAmount(p)}>
                    ₹{p}
                  </Button>
                ))}
              </div>
              <TextField label="Other amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </fieldset>
            <TextField label="Your name" autoComplete="name" required value={name} onChange={(e) => setName(e.target.value)} />
            <TextField label="Purpose (optional)" placeholder="e.g. Annadanam" value={purpose}
                       onChange={(e) => setPurpose(e.target.value)} />
            <TextField label="Mobile number (optional)" inputMode="tel" autoComplete="tel" value={phone}
                       onChange={(e) => setPhone(e.target.value)} />
            <TextField label="Email (optional)" type="email" autoComplete="email" value={email}
                       onChange={(e) => setEmail(e.target.value)}
                       hint="Leave a mobile or email to see this donation and its receipt later in My seva." />
            {error ? <Alert tone="danger">{error}</Alert> : null}
            <Button type="submit" busy={busy}>
              Donate ₹{validAmount(amount) ? amount.trim() : "…"}
            </Button>
            <p className="text-xs text-muted">
              Payments are processed by Razorpay into the trust&apos;s own account. Card and UPI details never reach
              this site.
            </p>
          </form>
        </Card>
      ) : null}
    </main>
  );
}
