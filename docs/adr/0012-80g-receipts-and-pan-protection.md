# ADR 0012: 80G receipts and PAN protection

- Status: Accepted (2026-10-03)
- Context: M3.2. Donors claim tax deductions under section 80G, so a trust issues each donor a
  receipt that names the trust (with its PAN and 80G registration) and the donor (with their PAN).
  The donor PAN is the most sensitive field we'll hold: it's a national identifier, it's used
  for fraud, and DPDP applies. Receipt numbers must be unique and sequential within each trust's
  financial year.

## Decision

**Trust 80G profile** (a tenant setting, TRUST_ADMIN only): legal name, address, trust PAN, 80G
registration number and its validity dates. No receipt can be issued without a complete profile.

**Donor PAN, encrypted in the application** (not only "encryption at rest"):
- Stored as AES-256-GCM ciphertext with a random 96-bit nonce per value. The tenant id and the
  column name are passed as associated data, so a ciphertext copied into another tenant's row or
  another column fails to decrypt.
- The key comes from the environment (`SEVACENTER_PAN_KEY`, 32 bytes, base64). In M6 it moves to
  AWS KMS (envelope encryption) with rotation. The app refuses to start without the key. It never
  logs the key or any PAN.
- Alongside the ciphertext we store only the **last 4 characters** (for display, `XXXXXX1234X`
  style) and an **HMAC-SHA256** under a separate key (a blind index), so "same donor PAN" can be
  matched without decrypting.
- PAN format is validated before encryption (`AAAAA9999A`, with the 4th letter a valid
  holder-type code).
- Full PAN is shown only on the receipt itself and only to LEADER+. API lists show the masked
  form.

**Receipts** — append-only, like the ledger:
- One receipt per donation, never for a reversal. Issuing it snapshots the trust profile and the
  donor details into the receipt row, so later edits don't change an issued receipt.
- Number: `<FY>/<sequence>` (for example `2026-27/000123`). It comes from a per-tenant, per-FY
  counter row taken with `SELECT ... FOR UPDATE` in the same transaction as the insert, so the
  numbers have no gaps or duplicates even under concurrency. `UNIQUE (tenant_id, fy, sequence)`
  is the database backstop.
- Reversing a donation that has a receipt marks the receipt cancelled, through a separate
  cancellation row, never an update. The number is never reused.
- The app role gets `SELECT, INSERT` on receipt tables, plus `UPDATE` on the counter row only.

**Output**: JSON first (the frontend renders a printable receipt). Server-side PDF only if trusts
need it, with a library that SCA can track.

## Consequences

- A lost key means the stored PANs are unreadable. Hence KMS (and backups of the key material)
  in M6, and this ADR's own threat entries.
- Tests run with a throwaway key. A test proves that a ciphertext moved to another tenant's row
  fails to decrypt.
