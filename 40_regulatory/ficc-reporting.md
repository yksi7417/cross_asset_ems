---
type: regulatory
regulator: DTCC
status: draft
tags: [regulatory]
---

# FICC Reporting

The **Fixed Income Clearing Corporation** (FICC), a DTCC subsidiary, operates two services with reporting obligations: the **Government Securities Division (GSD)** (UST cash + repo) and the **Mortgage-Backed Securities Division (MBSD)** (TBA MBS clearing). Members face reporting and operational obligations as a condition of clearing membership.

## What members report

- **Trade submission** — netting-eligible trades submitted intraday for inclusion in the next net.
- **Position reports** — member positions reported daily.
- **Margin reports** — daily MTM, collateral movements.
- **Settlement instructions** — DTC / Fedwire instructions tied to net settlement.
- **Forthcoming SEC Rule 17ad-22 changes (2025-2026)** — expanded UST clearing mandate increases FICC participation; firms previously avoiding FICC must onboard.

## Scope (asset classes)

- US Treasury cash ([[govt-bonds]]) — GSD clears interdealer + some D2C.
- US Treasury repo ([[money-market-repo]]) — GSD GCF Repo Service ([[gcf-repo]]) + bilateral repo (Sponsored Repo Service).
- TBA MBS ([[mbs]]) — MBSD clearing.

## Touchpoints in the EMS

- Sell-side EMS members submit trades to FICC via FIX / proprietary FICC API.
- Buy-side firms typically access FICC through a sponsoring member (under Sponsored Service for repo).
- The 2025+ UST clearing expansion materially affects buy-side EMS — see [[arch-stp-pipeline]] for the post-trade pipeline implications.

## Related

- [[ficc-clearing]] (clearing component) · [[gcf-repo]] (GCF Repo Service)
- [[govt-bonds]] · [[money-market-repo]] · [[mbs]] · [[tba-vs-specified-pool]]
- [[arch-stp-pipeline]] · [[arch-regulatory-reporting-service]]
