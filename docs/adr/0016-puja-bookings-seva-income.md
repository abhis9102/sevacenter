# ADR 0016: Puja bookings, and why dakshina is not a donation

- Status: Accepted (2026-10-03)
- Context: MVP (MandirCenter operations). Devotees book pujas and sankalps (with gotra, nakshatra,
  family names and a date), often paying a fixed dakshina. **A dakshina for a service is a fee,
  not a gift.** It isn't eligible for a section 80G deduction. If puja payments entered the
  donation ledger, a trust could issue 80G receipts for them, which is a compliance problem.

## Decision

- **Catalog** (LEADER+): name, deity, description, dakshina (0 = free), active, order.
- **Booking** (public, on the trust's host, rate-limited):
  - details plus a date from today to a year ahead, and a phone or email;
  - a 10-character booking code;
  - the puja name and dakshina are **snapshotted** into the booking.
- **Payment**, for a dakshina above zero:
  - It uses the M3.3 flow: a server-side order, then the signature **and** a Razorpay API
    cross-check. `payment_intent.kind = PUJA`.
  - A verified PUJA payment confirms the **booking**. It never writes a `donation` row.
  - DB CHECKs: a PUJA intent never has a `donation_id`, and a paid booking can only be CONFIRMED
    with a `payment_ref`.
- **Free pujas** are confirmed at once.
- **Schedule:**
  - MEMBER+ (a priest) sees the day's confirmed bookings **without contacts** and marks them
    performed (only CONFIRMED ones).
  - LEADER+ also sees contacts and can cancel. Performed bookings can't be cancelled.
- **Not yet:** refunds on cancellation (done in the gateway dashboard for now), and a seva-income
  report (later, next to the donation summary).

## Consequences

- `PujaSettlement` is an interface owned by the payment code and implemented by the puja module.
  The ledger code doesn't depend on pujas, and payment verification is shared, not duplicated.
