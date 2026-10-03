# ADR 0013: Online donations with Razorpay, into each trust's own account

- Status: Accepted (2026-10-03)
- Context: M3.3. Devotees pay online by UPI or card. For an 80G receipt the trust has to be the
  recipient of the money, and a platform that collects money on behalf of others needs a
  payment-aggregator licence. The main threats: forged "payment succeeded" calls, amount
  tampering, replays and double counting, a leaked gateway secret, payments lost when the browser
  closes, and spam orders.

## Decision

**Each trust uses its own Razorpay account.** A TRUST_ADMIN enters the key id and secret once,
under Settings → Payments.
- The secret is checked against Razorpay before it's saved.
- It's stored AES-256-GCM encrypted, with the tenant id as associated data, under its own key
  (`SEVACENTER_SECRETS_KEY`, separate from the PAN key).
- It's never returned by any API. The key id is public, because Checkout needs it.

**Flow** (on the trust's own host, no login needed):
1. `POST /api/v1/public/donations/orders`: the **server** creates the Razorpay order from the
   requested amount (₹1 to ₹10,00,000). It records a `payment_intent` (order id, amount, donor
   name, status CREATED). Rate-limited per client IP.
2. The browser opens Razorpay Checkout.
3. `POST /api/v1/public/donations/confirm` with `{orderId, paymentId, signature}`:
   - verify `HMAC-SHA256(orderId|paymentId, secret)` in constant time;
   - **fetch the payment from Razorpay's API** and require `captured`, the same `order_id`, the
     intent's exact amount and INR;
   - then, with the intent row locked, insert the ledger donation and mark the intent PAID.
     This step is **idempotent**: a second confirm returns the same donation. `UNIQUE
     (razorpay_payment_id)` is the backstop.

   The amount always comes from the intent and never from the confirm request.
4. **Reconciliation** (TRUST_ADMIN): for intents still CREATED, fetch the order's payments from
   Razorpay and confirm any captured ones. This recovers payments made in a browser that closed
   before step 3, without needing a public webhook yet. A webhook comes with M6 (public URL).

**Ledger attribution.** `donation.channel` is `STAFF` or `ONLINE`. A CHECK enforces that a STAFF
entry has `recorded_by` and an ONLINE entry has `payment_ref`. The staff column stays strictly
attributed: no NOT NULL is dropped.

**Receipts.** An online donation is a normal ledger entry. Staff issue its 80G receipt with the
donor's PAN and address (M3.2 rules). The donor gets a payment confirmation, which is not presented
as an 80G receipt.

**Content Security Policy.** The donate page alone allows Razorpay's checkout script and frames
(`checkout.razorpay.com`, `api.razorpay.com`). The admin app's policy is unchanged.

## Consequences

- Tests use a fake gateway client and never touch the network. One opt-in live test uses real
  test keys.
- A trust without payment settings can't take online donations (`409 payments_not_configured`).
