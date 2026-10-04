"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MandirPageTitle, useDevotee, useMandirText } from "@/components/MandirShell";
import { prefill } from "@/lib/devotee";
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
  const tx = useMandirText();
  const [info, setInfo] = useState<Info | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [amount, setAmount] = useState("501");
  // null = not typed in yet: a signed-in devotee's own details fill it (ADR 0025).
  const pre = prefill(useDevotee());
  const [nameIn, setName] = useState<string | null>(null);
  const [purpose, setPurpose] = useState("");
  const [phoneIn, setPhone] = useState<string | null>(null);
  const [emailIn, setEmail] = useState<string | null>(null);
  const name = nameIn ?? pre.name, phone = phoneIn ?? pre.phone, email = emailIn ?? pre.email;
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
        `${describeError(err)} ${tx.donate.takenNote(paid.razorpay_payment_id)}`,
      );
    } finally {
      setBusy(false);
    }
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!validAmount(amount)) {
      setError(tx.donate.amountError);
      return;
    }
    if (!name.trim()) {
      setError(tx.donate.nameError);
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
        ? tx.donate.checkoutError
        : describeError(err));
      setBusy(false);
    }
  }

  const chip = (active: boolean) => `rounded-[12px] border px-3 py-2.5 text-sm font-semibold transition-colors ${
    active ? "border-primary bg-primary text-white shadow-xs" : "border-line bg-surface hover:border-primary/50"
  }`;

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="donate" title={tx.donate.title} subtitle={info?.trustName ? tx.donate.to(info.trustName) : undefined} />

      {loadError ? <Alert tone="danger">{loadError}</Alert> : null}
      {info && !info.onlineDonations ? (
        <Alert tone="info">{tx.donate.notAccepting}</Alert>
      ) : null}

      {done ? (
        <Card className="flex flex-col gap-3 border-primary/30 bg-gradient-to-br from-primary/10 to-surface">
          <h2 className="text-lg font-medium">{tx.donate.thanks(done.donorName)}</h2>
          <p>{tx.donate.received(done.amount, done.receivedOn)}</p>
          <p className="text-sm text-muted">{tx.donate.notReceipt}</p>
        </Card>
      ) : info?.onlineDonations ? (
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <Card className="flex flex-col gap-4">
          <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-2 text-sm font-semibold">{tx.donate.chooseAmount}</legend>
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
                {PRESETS.map((p) => (
                  <button key={p} type="button" aria-pressed={amount === p} className={chip(amount === p)} onClick={() => setAmount(p)}>
                    ₹{Number(p).toLocaleString("en-IN")}
                  </button>
                ))}
              </div>
              <TextField label={tx.donate.otherAmount} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </fieldset>
            {funds.length > 0 ? (
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-2 text-sm font-semibold">{tx.donate.giveTo}</legend>
                <div className="grid gap-2 sm:grid-cols-2">
                  {[{ id: "", name: tx.donate.neededMost }, ...funds.map((f) => ({ id: String(f.id), name: f.name }))].map((f) => (
                    <button key={f.id || "general"} type="button" aria-pressed={fundId === f.id} onClick={() => setFundId(f.id)}
                            className={`${chip(fundId === f.id)} text-left`}>
                      {f.name}
                    </button>
                  ))}
                </div>
              </fieldset>
            ) : null}
            <TextField label={tx.common.yourName} autoComplete="name" required value={name} onChange={(e) => setName(e.target.value)} />
            <TextField label={tx.donate.purpose} placeholder={tx.donate.purposeHint} value={purpose}
                       onChange={(e) => setPurpose(e.target.value)} />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label={tx.common.mobileOptional} inputMode="tel" autoComplete="tel" value={phone}
                         onChange={(e) => setPhone(e.target.value)} />
              <TextField label={tx.common.emailOptional} type="email" autoComplete="email" value={email}
                         onChange={(e) => setEmail(e.target.value)} />
            </div>
            <p className="-mt-2 text-xs text-muted">{tx.donate.seeLater}</p>
            {error ? <Alert tone="danger">{error}</Alert> : null}
            <Button type="submit" busy={busy}>
              {tx.donate.submit(validAmount(amount) ? Number(amount.trim()).toLocaleString("en-IN") : "…")}
            </Button>
          </form>
        </Card>
        <aside className="flex flex-col gap-3 text-sm">
          <Card className="flex flex-col gap-2 bg-gradient-to-br from-haldi/15 to-surface">
            <h2 className="text-base font-semibold">{tx.donate.whereTitle}</h2>
            <p className="text-muted">{tx.donate.whereBody(info.trustName ?? "the trust")}</p>
          </Card>
          <Card className="flex flex-col gap-2">
            <h2 className="text-base font-semibold">{tx.donate.receiptTitle}</h2>
            <p className="text-muted">{tx.donate.receiptBody}</p>
          </Card>
          <Card className="flex flex-col gap-2">
            <h2 className="text-base font-semibold">{tx.donate.safeTitle}</h2>
            <p className="text-muted">{tx.donate.safeBody}</p>
          </Card>
        </aside>
        </div>
      ) : null}
    </div>
  );
}
