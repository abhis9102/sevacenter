# 0039 — Devotees manage their own household in My Mandir

- **Status:** Accepted (2026-10-15)
- **Deciders:** AppSec (Abhi); built by Claude
- **Relates to:** ADR 0038 (households, verified contacts), ADR 0010 (devotee records, consent)

## Context

ADR 0038 let the temple office build households and devotees only view theirs. Devotees expect
to manage their own family (add their children, bring in their spouse) without a trip to the
office, while India's DPDP Act requires each adult's own consent for their data, and verifiable
parental consent for a child's.

## Decision

1. **Start a family:** a signed-in devotee in no household starts one and becomes its head. If no
   devotee record matches their verified contacts, one is created from their account (consent
   source ONLINE_FORM, recorded by their account).
2. **Managers** are the head and spouse. They:
   - **add children** directly (relation child or grandchild): the parent's act is the parental
     consent for the minor's record;
   - **edit children's records** (name, date of birth): a child is related as one and under 18;
   - **invite adults** by phone or email (any relation but head), at most 10 a day per family,
     each valid 7 days; email invites get a notification;
   - **remove** any member but the head.
3. **Adults join only by accepting** while signed in with the **exact** invited phone or email
   (their own consent). Nobody else can accept it, and nobody already in a household can.
4. **Anyone can leave.** Nobody sees another adult's contact details; children's dates of birth are
   shown only to managers.
5. **Provenance:** devotee records and households may now be created by a devotee's account instead
   of a staff member; the database requires one or the other. Every action is audited (no staff
   actor; the detail names the devotee account).

## Consequences

- Families form without the office; the office still sees and can correct everything (ADR 0038).
- SMS invites have no notification yet (no generic SMS sender): the invitee sees the invitation when
  they sign in.
- Tests: `FamilyTest` (the full flow; adults never edited as children; head not removable by
  others; leaving; one household per devotee; managers only). Mutation-checked: accepting without
  the invited contact fails it.
