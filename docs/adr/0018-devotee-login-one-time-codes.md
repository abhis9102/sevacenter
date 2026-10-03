# ADR 0018: Devotee login with one-time codes (email and SMS)

- Status: Accepted (2026-10-04)
- Context: MVP (MandirCenter operations). Devotees book pujas, register for event passes and
  offer seva on the trust's host, giving a mobile number or an email each time. They need to see
  their own bookings and passes again ("my seva") without the trust creating accounts for them,
  and without passwords, which devotees forget and reuse.

## Decision

- **A one-time code to a contact the devotee owns.** It's 6 digits from `SecureRandom`, sent by
  SMS (MSG91, DLT-registered template) or email (SMTP: Mailpit locally, Amazon SES in M6). Both
  channels are offered from the start; a channel is offered only when it's configured
  (`GET /api/v1/public/devotee-login`).
- **Nothing to enumerate.** `POST /public/devotee-login/code` returns the same `202 {"sent":true}`
  for every well-formed contact. An account (`devotee_account`) is created **only** when a code
  sent to that contact is verified, so "registration" is proof of ownership by construction.
- **Codes are never stored, returned or logged.** `devotee_otp` keeps HMAC-SHA256 values of the
  contact and the code under `SEVACENTER_OTP_KEY` (its own key). A plain hash of a 6-digit code
  would fall to a million-try loop; with a server-held key, a leaked table is useless on its own.
  No profile, including local, puts the code in a response; locally it lands in Mailpit.
- **Guessing is bounded.**
  - A code expires in 10 minutes and works once.
  - A new code replaces the previous one.
  - 5 wrong guesses burn the code. The counter is updated under a row lock and commits even on
    failure.
  - Every failure gives the same `400 invalid_code`.
- **Sending is bounded, because SMS costs money and can flood a stranger's phone.**
  - Per contact: 3 codes per 15 minutes and 10 per day, counted in the database so the limits
    hold across instances.
  - Per IP: 20 per 15 minutes.
  - Per trust per day: 500 SMS and 2,000 emails.
  - SMS goes only to Indian mobile numbers, so there's no international premium-rate pumping.
  - A provider failure returns 503 and rolls back the code row.
- **A separate devotee session.**
  - The cookie `SC_DEVOTEE` holds a 256-bit random token: HttpOnly, Secure, SameSite=Lax,
    `Path=/api/v1/portal`, 7 days.
  - Only the token's SHA-256 is stored (`devotee_session`, forced RLS), so a session works only
    on its own trust's host.
  - Logout revokes the session server-side.
  - It's deliberately **not** a Spring Security login, so it can never satisfy a staff
    endpoint, and a staff session opens no portal endpoint. The path scope means the browser
    doesn't even send it to the staff API.
  - CSRF applies as everywhere else.
- **"My seva" matches only the exact verified contact.** It shows puja bookings (not unpaid
  attempts), event passes and sevak signups whose phone or email equals the contact the devotee
  proved they own. There's no fuzzy matching, and nothing from staff-only records (devotee
  register, donations ledger, PAN).

## Consequences

- **SMS needs the trust platform's MSG91 setup:** the DLT entity and sender id, an approved OTP
  template with an `##otp##` variable, then `MSG91_AUTH_KEY` and `MSG91_OTP_TEMPLATE_ID` in the
  environment. Until then, only email is offered.
- **Sending happens inside the request transaction**, so a slow provider holds a connection for up
  to 10 seconds. A queue can come later if volume needs it.
- **The 20-per-IP limit is in memory per instance**, like the login throttle (shared store in M6).
  The per-contact and per-trust limits already live in the database.
- **Online donations carry no contact yet**, so they don't appear in "my seva". Adding an optional
  contact to the donate form is a follow-up.
