# ADR 0019: Donation history and receipts in "my seva"

- Status: Accepted (2026-10-04)
- Context: MVP. Devotees asked to see their past donations and 80G receipts after signing in
  (ADR 0018). Online donations recorded no contact, so they couldn't be found. Staff-recorded
  donations are linked to a devotee record that carries a phone or email.

## Decision

- **Online donors may leave a mobile number or email.** It's optional, validated like every other
  contact, and stored on `payment_intent` (V18). Donating never requires it.
- **"My seva" lists a verified contact's own donations**, matched exactly. There are two ways in:
  - An online donation whose checkout contact equals the verified contact.
  - A staff-recorded donation linked to a devotee whose phone or email equals it.

  Reversals aren't listed as entries; the donation they reverse is marked "Reversed".
- **Receipts are a copy with the donor PAN masked** (`GET /api/v1/portal/donations/{id}/receipt`).
  It works only for a donation in that contact's own history. Anyone else's gets a 404, and with no
  devotee session the answer is 401. Responses are `no-store`. The full PAN stays a LEADER+ view:
  a family's shared phone, or a mistyped one on a devotee record, must never expose someone's full
  PAN.

## Consequences

- **Family members who share a phone see each other's donations and receipts.** The PAN stays
  masked, but the donor's name and address show. That's the same exact-contact rule as bookings and
  passes (ADR 0018), and families in practice share one number.
- **Donors who left no contact, or whose devotee record has none, see nothing.** The trust can add
  the contact to the devotee record.
- **The original receipt, with the full PAN, still comes from the temple office.**
