# ADR 0022: Earmarked donation funds

- Status: Accepted (2026-10-04)
- Context: Trusts account for money by purpose, for example the Annadanam fund, the building fund
  and the general fund. They need per-fund totals, and online donors want to choose where their gift
  goes. A free-text "category" was proposed; totals would split on every typo and casing difference.

## Decision

- **A managed list per trust (`donation_fund`, V21).** Names are unique ignoring case and repeated
  spaces. TRUST_ADMIN creates, renames and turns funds off; LEADER+ (who see donations) list them.
  Funds are **never deleted**, only deactivated, because ledger entries keep pointing at them.
  Changes are audited as `FUND_SAVED`.
- **Each ledger entry may carry a fund** (`donation.fund_id`, set at insert like every ledger
  column). Null means the general fund.
  - **A reversal carries its donation's fund**, so per-fund totals net out exactly as mode totals
    do.
  - New donations only go to an **active fund of this trust**. Inactive funds, and other trusts'
    funds (hidden by RLS), are refused as `fundId` field errors.
- **Online donors can earmark a gift.** The donate page lists active funds by name
  (`GET /api/v1/public/donation-funds`). The chosen fund is validated when the order is created,
  stored on the payment intent, and copied to the ledger entry only at verified settlement.
- **The yearly summary gains `byFund`** (net and entry count), alongside `byMode`.
- **Funds sit inside the Donations module** for access limits (ADR 0021).

## Consequences

- **The fund doesn't appear on the 80G receipt.** The receipt certifies a donation to the trust; the
  fund is internal accounting. Revisit if a trust needs fund-specific receipts, for example
  corpus donations.
- **No historical backfill:** donations recorded before V21 are in the general fund.
