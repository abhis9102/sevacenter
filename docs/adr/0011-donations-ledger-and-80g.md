# ADR 0011: Donations — an append-only ledger, 80G receipts, PAN encryption

- Status: Accepted (2026-10-03)
- Context: M3. Money and the donor's PAN (Permanent Account Number). The main threats are amount
  tampering, rewriting history, gaps or duplicates in receipt numbers (a tax-compliance
  problem), and PAN exposure. India's DPDP Act still applies, and so does tax law: 80G records
  must be kept, so a donor can't simply be deleted.

## Decision

**Slices.** M3.1: an offline ledger that staff record by hand (cash, UPI, cheque, bank transfer),
which is the bulk of temple donations. M3.2: 80G receipts plus PAN. M3.3: Razorpay online
donations (webhook signature, idempotency).

**Money.** Amounts are `BIGINT` paise, `> 0`, with an upper bound. The API takes rupees as a decimal
string with at most two decimal places and converts exactly (`BigDecimal`, never `double`).
Currency is INR only.

**Append-only.** Donation rows are never updated or deleted by the app. The DB role has no
`UPDATE`/`DELETE` grant on the ledger, so even an application bug can't rewrite history. A
correction is a **reversal**: a new row that points at the original, made by a TRUST_ADMIN with a
reason. A donation can be reversed at most once (unique constraint).

**Who:**

| | MEMBER | LEADER | TRUST_ADMIN |
|---|---|---|---|
| Record a donation | ❌ | ✅ | ✅ |
| List / view (own tenant) | ❌ | ✅ | ✅ |
| Reverse a donation | ❌ | ❌ | ✅ |
| FY summary | ❌ | ✅ | ✅ |

Members get no access at all: donation history is financial information about named people.

**Donor link.** A donation optionally references a devotee. Erasing a devotee who has donations
anonymises the devotee instead of deleting them: personal fields are blanked and the row stays, so
the ledger and any receipts still line up (DPDP erasure versus statutory retention).

**Financial year.** The Indian FY runs from 1 April to 31 March, computed in `Asia/Kolkata`, not UTC
or the server's zone.

**Audit.** Every ledger write is attributed (`recorded_by`). A structured line goes to the `audit`
logger. A tamper-evident audit table follows in M3.2, alongside the receipts.

## Consequences

- The ledger has a separate grant set in the migration: `SELECT, INSERT` only.
- Tests prove the app role can't `UPDATE` or `DELETE` the ledger.
- The probe gains donation rows: role matrix, cross-tenant ids, amount tampering.
