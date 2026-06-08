---
type: documentation
status: draft
tags: [documentation]
---

# SIFMA TBA Guidelines (Good Delivery)

**SIFMA Good Delivery Rules** for TBA-MBS — the industry-standard documentation defining what constitutes a "good delivery" against a TBA trade: which pool characteristics are acceptable, what settlement-date conventions apply, what counts as a fail.

## Where it is required

- All [[mbs|TBA-MBS]] trades.
- Specified-pool trades reference TBA standards for stipulations beyond.
- See [[tba-vs-specified-pool]] for the fungibility framework that depends on these rules.

## Key terms

### Settlement schedule

- **Monthly Class A/B/C/D** settlement dates by coupon/agency:
  - Class A: 30-year Conventional (FN, FH 30y) — typically mid-month.
  - Class B: 30-year Government (GN 30y) — slightly different date.
  - Class C: 15-year Conventional + Government — late month.
  - Class D: ARMs and other.
- The exact dates per month are published by SIFMA annually.

### Good Delivery rules

- **Pool aggregation tolerance** — small variance per delivery allowed (e.g. 0.01% of par).
- **Maximum number of pools** per million face of TBA trade.
- **Variance tolerance** between TBA face and delivered pool face.
- **Settlement-date discipline** — must deliver on the specified Class date.

### Fail rules

- A delivered set of pools that fails Good Delivery is a "fail" — the party must redeliver acceptable pools.
- Repeated fails trigger cure / dispute procedures.

## EMS implications

- TBA trade representation requires SIFMA Good Delivery context — coupon / agency / settlement month.
- Specified-pool trades use TBA as the base, with additional stipulations ([[wac-wam-wala]]).
- Settlement breaks on TBA Good Delivery violations feed [[arch-stp-pipeline]] and [[arch-notification-service]].

## Related

- [[mbs]] · [[tba-vs-specified-pool]] · [[dollar-roll]] · [[wac-wam-wala]]
- [[bloomberg-tba]] · [[tradeweb]] (execution venues)
- [[psa]] (per-pool documentation) · [[arch-stp-pipeline]]
