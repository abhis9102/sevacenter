# 0038 — Households, and how a devotee's records come together in My Mandir

- **Status:** Accepted (2026-10-15)
- **Deciders:** AppSec (Abhi); built by Claude
- **Relates to:** ADR 0010 (devotee records), ADR 0018/0019/0025 (devotee login, contacts), ADR 0037 (groups)

## Context

Temples know families: one address, one gotra for sankalp, a head of the family, children who
later become devotees themselves. Devotee records were individuals with no link between them.
Devotees, signed in to MandirCenter, should see their whole relationship with the temple.

## Decision

**The joining key is a verified contact.** A devotee signs in with a one-time code to a phone or
email; they can prove several. Everything staff record about them (donations, puja bookings,
passes, sevak signups, devotee records) belongs to them when its phone or email exactly matches one
of their verified contacts. Groups link to the signed-in account itself.

```
verified contacts ──match──► staff devotee record(s) ──► household (family)
        │                                                     │
        └──► group memberships, passes, pujas, giving ────────┴──► My Mandir
```

1. **Households are run by the temple office (staff, Devotees module).** A household has a name,
   family gotra, shared address and notes; members are devotee records with a relation (one head,
   at most one household per devotee; both enforced by the database). LEADER+ build and change
   them; MEMBER staff see names, not the address (same masking as devotee records). Family giving
   for the financial year is shown only to staff who can see donations.
2. **Devotees see, but don't edit, their household** in My Mandir: the family name, gotra and each
   member's **name and relation only**, never another member's phone, email or address. Otherwise
   anyone could attach themselves to a family and read its details.
3. **Erasure** (DPDP) removes the devotee from their household: deleted records by cascade,
   anonymised ones (kept for the ledger) explicitly.
4. **How people join**, in one place:

   | | Who adds | How |
   |---|---|---|
   | Public, open group | the devotee | MandirCenter → Groups → Join |
   | Public, approval group | devotee asks, leader approves | Ask to join → SevaCenter → approve |
   | Private group | staff | by the phone/email the devotee signs in with |
   | Household | temple office | on the devotee's page in SevaCenter |
   | Utsav pass / puja | devotee or the counter | as before; shown in My Mandir by contact |

## Consequences

- My Mandir shows passes, pujas, giving, sevak signups, **groups and family** in one place.
- A devotee whose records use a phone/email they never verified won't see them until they add
  that contact in My Mandir (one code).
- Tests: `HouseholdTest` (structure rules, masking, family giving net of reversals, the devotee's
  read-only view with no contacts, erasure, cross-trust); mutation-checked: dropping the erasure
  step fails it.
