---
type: regulatory
regulator: CFTC
status: draft
tags: [regulatory]
---

# CFTC SDR (Swap Data Repository)

CFTC-registered Swap Data Repositories — the post-trade reporting destination for **CFTC-regulated swaps**. Established under Dodd-Frank Title VII. See the [[cftc-sdr|glossary entry]] for the quick definition.

## Major SDRs

- **DTCC DDR** (Data Repository) — the dominant credit / rates / equity SDR. Default choice for most CFTC reporting parties.
- **CME SDR** — rates / commodity focused.
- **ICE Trade Vault** — credit / commodity focused.

Reporting parties choose an SDR per asset class.

## Scope (asset classes)

- [[interest-rate-swaps|IRS, OIS]], cross-currency basis, inflation swaps.
- [[credit-default-swaps|CDS]] indices and single names.
- [[fx-ndf|FX NDFs]] and CFTC-jurisdictional FX swaps / forwards.
- [[commodity-futures|Commodity swaps]] (commodity derivative swaps, not futures).
- [[equity-swaps|Equity swaps]] (under CFTC jurisdiction when treated as a swap).

## Reporting fields / timing

- **Real-time public report**: within minutes of execution, masked counterparty info, basic economic terms.
- **Regulator report**: complete counterparty + economic detail to CFTC, T+1.
- **Lifecycle events**: amendments, partial novations, terminations all report.
- **Fields**: ~120-140 fields depending on asset class — full ISDA Common Domain Model coverage. Includes USI/UTI ([[usi-uti]]), parties (with LEI — see [[lei]]), notional, price/rate, dates, clearing flag, FCM, MAT flag.

## Touchpoints in the EMS

- [[arch-regulatory-reporting-service]] handles SDR submission for trades the firm is obligated to report on.
- The reporting party determination logic (which party reports) follows CFTC rules — typically the SEF, dealer, or higher-order party.
- USI/UTI ([[usi-uti]]) is propagated through the trade's [[arch-identity-chaining]] and surfaced in SDR submissions.
- Lifecycle event detection (resets, amendments) triggers downstream submissions.

## Related

- [[cftc-sdr|CFTC SDR glossary]] · [[mat]] · [[novation]] · [[fcm]] · [[usi-uti]]
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[fx-ndf]]
- [[sef-reporting]] · [[dtcc-sdr]] (sibling DTCC component)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
