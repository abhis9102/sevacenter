# ADR 0024: The MandirCenter temple site: darshan hours, aartis, panchang

- Status: Accepted (2026-10-04)
- Context: The public temple pages (ADR 0017) worked but were plain: a free-text timings line and
  links to separate forms. A prototype showed the experience temples expect: the vibrant devotional
  palette, a live "open for darshan" strip, the daily aarti timetable, today's panchang and tabs for
  each service. It also made up data (a default helpline and five default aartis) whenever the
  temple hadn't entered any, and staff had to type the tithi in by hand every day.

## Decision

- **Structured darshan hours** on `temple_profile` (V22): a morning and an evening session, each an
  open/close pair. The database and the API both reject impossible hours (half a pair, closing
  before opening, overlapping sessions). The free-text `timings` stays as a short note.
- **Today's status override** (`PUT /api/v1/temple/status`, LEADER+): OPEN or CLOSED with a note.
  It's stored with the IST date it was set for and **applies on that date only**, so a "closed
  for grahan" can never get stuck. It's audited like any other change to the temple page.
- **The aarti timetable** is its own table, `temple_aarti`, with forced RLS. There are at most 12
  rows, returned in time order, and the whole timetable is replaced on save. The app role gets
  SELECT, INSERT and DELETE only.
- **The panchang is calculated, never typed in.** `frontend/src/lib/panchang.ts` computes tithi,
  paksha, nakshatra and lunar month (including adhik) from Meeus' Sun and Moon algorithms with the
  Lahiri ayanamsa. Each comes with its end time. Tithi and nakshatra change at the same instant
  everywhere, so no temple location is needed. The temple chooses **amanta or purnimanta**
  (`calendar`) to name the month. The tests check it against Meeus' worked examples, the 2026
  eclipses, Diwali, Pitru Paksha and 2026's Adhik Jyeshtha.
- **Nothing is shown that the temple didn't enter.** No hours means no status strip, no aartis
  means no timetable, and the panchang is shown because it's a calculation, not a claim the
  temple made.
- **One frame for every public page.** A route group `(mandir)` shares the header (temple name,
  devotee sign-in), the darshan strip, tabs for the services the temple actually offers, and the
  footer. URLs are unchanged. The MandirCenter palette is scoped to `.mandir`, so the staff app
  keeps its restrained one.

## Consequences

- The panchang's end times are good to a few minutes, so the UI shows minutes only, and the footnote
  says it's calculated (Lahiri). Sunrise-based conventions (the tithi "of the day") and
  location-dependent items (rahu kaal, sunrise) would need the temple's coordinates. That's a
  possible follow-up.
- The public payload grows by at most 12 short rows. Everything in it is already public.
