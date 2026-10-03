# ADR 0010: Devotee records — PII scope, tiered access, consent and erasure

- Status: Accepted (2026-10-03)
- Context: M2. Devotee records are the first personal data about people who are **not** users of
  the system. That brings in India's DPDP Act: purpose limitation, consent, data minimisation, and
  the right to erasure.

## Decision

**Fields.** Full name (required); phone, email, address (line, city, state, pincode) and date of
birth, all optional; a consent record. No PAN: it arrives with 80G donations (M3), encrypted and
scoped to the donation.

**Tiered access** (over TRUST_ADMIN > LEADER > MEMBER):

| | MEMBER | LEADER | TRUST_ADMIN |
|---|---|---|---|
| List / search / view | ✅ **masked** | ✅ full | ✅ full |
| Create / edit | ❌ | ✅ | ✅ |
| Erase | ❌ | ❌ | ✅ |
| CSV export (M2 slice 2) | ❌ | ❌ | ✅ |

- **Masked** = name, city and state in full. Phone shows its last 4 digits, email its first letter
  and domain. Address line, pincode and date of birth are left out. Masking happens on the
  server: the full values never reach a MEMBER's browser.
- Members search by **name only**. Searching by phone or email would let a member confirm a full
  phone number that the masked view hides.
- Bulk export is admin-only. A CSV of every devotee is the highest-impact exfiltration path.

**Consent.** Creating a devotee requires a consent source (in person, phone, online form,
written). The server records when consent was given and which staff member recorded it; the
client can't set either. Edits don't change the consent record.

**Erasure.** A TRUST_ADMIN can erase a devotee, which is a hard delete. M3 has to revisit this:
80G receipts have to be kept for years under tax law, so erasing a donor will then mean
anonymising the devotee and keeping the receipt.

**Audit.** `created_by` / `updated_by` on every row. A full audit log (Repudiation, threat model)
is planned with M3, where money makes it mandatory.

**Search safety.** User input is escaped before it goes into `LIKE`, so `%` or `_` can't turn into
full-table wildcard scans. Page size is capped at 100, and sorting is fixed (no client-chosen
sort property).

## Consequences

- Every devotee endpoint declares `@PreAuthorize`, and masking is decided from the role
  re-read on each request (`StaffSessionFilter`), so a demotion takes effect immediately.
- The `devotee` table is under forced RLS; `everyTenantScopedTableHasForcedRlsAndAPolicy` fails the
  build otherwise.
- `authz_probe.py` gains a row for every devotee endpoint: role matrix, cross-tenant ids and
  masking.
