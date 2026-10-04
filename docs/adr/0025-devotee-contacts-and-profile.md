# ADR 0025: One devotee, several verified contacts, and their own profile

- Status: Accepted (2026-10-04)
- Context: "My seva" (ADR 0018/0019) showed what was made with the one phone or email a devotee
  signed in with. People use both: a puja booked by phone and a donation made by email belonged to
  the same person but showed up in two separate, half-empty accounts. Devotees also had nowhere to
  keep their name, gotra or family for the next sankalp, and phone login couldn't be tried locally.

## Decision

- **A devotee account holds up to 6 verified contacts** (`devotee_contact`, forced RLS). Each is
  linked only by entering the code sent **to that contact**: `POST /api/v1/portal/contacts`, with a
  devotee session and CSRF. Signing in with any of them opens the same account, and "my seva" shows
  records made with any of them (exact matches, one entry per record).
- **Contacts on a staff devotee record are not trusted.** Staff may mistype a number; trusting it
  would show one person's bookings to another. A contact counts only once its owner verifies it.
- **Linking a contact that already had its own account merges the two.** The devotee has just
  proved they own both. Contacts move over; the profile keeps the devotee's own entries and fills
  gaps from the other account; the other account points to this one (`merged_into`, kept for the
  audit trail) and its open sessions are revoked.
- **No SQL backfill.** Under FORCE RLS a migration role without BYPASSRLS would copy nothing, and
  silently. Accounts created before this register their own contact the next time they're used.
- **The devotee's own profile** (`PUT /api/v1/portal/profile`): name, gotra, nakshatra, rashi,
  date of birth, family, address. All optional, validated, audited. It pre-fills the sankalp and
  the donation, event and seva forms; it is separate from the staff devotee record. PAN is not
  collected here; 80G receipts stay with the temple office.
- **Signed in = auto-linked.** The public forms start with the devotee's verified phone and email,
  so what they do next lands in their account.
- **Local SMS stand-in.** Under the `local` profile only, and only while no SMS provider is
  configured, SMS codes go to the local Mailpit inbox (`sms-91…@sms.local`) so phone login can be
  tried end to end.

## Consequences

- Removing a linked contact isn't offered yet (no DELETE grant); a devotee who loses a number keeps
  it linked until support for unlinking is added.
- Code-sending limits are per contact (3 per 15 minutes, 10 a day), so linking reuses the login
  limits rather than adding a new path around them.
