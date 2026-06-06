---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# WAC / WAM / WALA — MBS Pool Metrics

Three weighted-average characteristics of an MBS pool that drive its prepayment behavior and therefore its price relative to TBA:

- **WAC** (Weighted Average Coupon) — the average gross coupon paid by the underlying loans; the borrower rate, before servicing strip. Higher WAC means higher refi incentive when rates fall.
- **WAM** (Weighted Average Maturity) — average remaining months until the loans' contractual maturity (a borrower with 25 years left has higher WAM than one with 10).
- **WALA** (Weighted Average Loan Age) — average months since origination. Older pools have lower WALA-driven prepay sensitivity (most of the optionality has played out).

These three are reported on every pool factor sheet and feed prepayment models (PSA, CPR conventions). Specified-pool trades are differentiated from TBA by these metrics — a "low loan balance, high FICO, NY-concentrated" specified pool pays up to TBA precisely because its WAC/WAM/WALA profile implies different prepay behavior.

## Example

A 30-year Fannie 5.5% pool with WAC 6.25%, WAM 358, WALA 2 — newly originated, high refi optionality. Same coupon TBA delivers any conforming pool; this specific pool pays up because its newness and WAC profile makes it less prepay-sensitive than average.

## Why it matters in an EMS

- Specified-pool tickets carry these fields on every trade.
- Best-ex for specified pools requires comparing pay-up against a model expectation given WAC/WAM/WALA.
- The reference-data service ([[arch-reference-data-service]]) carries the pool factor history.

## Related

- [[tba-vs-specified-pool]] · [[mbs]] · [[bloomberg-tba]]
- [[arch-pricing-service]] · [[arch-reference-data-service]]
