# ADR 0017: The public temple page

- Status: Accepted (2026-10-03)
- Context: MVP (MandirCenter operations). Each trust's own host (`<slug>.sevacenter.app`) needs a
  public front door. It shows timings, an announcement and contact details, and links to the
  services the temple actually offers. Until now `/` sent everyone to the staff app.

## Decision

- **`/` on a trust host** is now the public temple page. Staff use the "Staff sign in" link and
  land on `/devotees` after login, as before.
- **Content** (`temple_profile`, one row per trust, forced RLS): deity, address, helpline (E.164),
  timings and announcement. All fields are optional, and **empty until the temple fills them in**.
  There's no placeholder data, because the public must never see made-up timings or phone numbers.
  LEADER+ edits it.
- **Service links come from real state**:
  - Donate: only when a payment account is connected.
  - Events: only when a published upcoming event exists.
  - Pujas: only when an active puja exists.
  - Seva: always.
- All text is rendered as text (no HTML).
