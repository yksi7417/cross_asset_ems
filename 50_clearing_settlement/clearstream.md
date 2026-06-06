---
type: clearing_settlement
kind: icsd
status: draft
tags: [clearing/icsd]
---

# Clearstream

**Clearstream Group** (Deutsche Boerse subsidiary) is the other major European International Central Securities Depository alongside [[euroclear]]. Particularly strong in German and EU securities, fixed income, and tri-party collateral management. Operates **Clearstream Banking Luxembourg** (international) and **Clearstream Banking Frankfurt** (German national CSD).

## Asset classes / instruments

- European govts ([[govt-bonds]]) — especially German Bunds.
- European [[corp-bonds-ig]] / [[corp-bonds-hy]] / [[convertibles]].
- Eurobonds (international bond issuance).
- German [[cash-equity]] (via Clearstream Banking Frankfurt).
- Tri-party repo (Clearstream's GMRA-documented tri-party — see [[tri-party-vs-bilateral-repo]]).

## Settlement cycle

- EU equity / corp: T+2 standard; transitioning toward T+1.
- Eurobonds: T+2.
- Repo: T+0 or T+1.
- DVP / RVP / FOP supported.

## Membership / access

Clearstream participants: banks, brokers, custodians, central banks. Buy-side firms access via custodian or as direct participants.

## EMS touchpoints

- Settlement instructions generated post-allocation per [[arch-allocation-service]].
- Clearstream participant codes per account in [[arch-reference-data-service]].
- Tri-party collateral instructions for repo flow via Clearstream's tri-party API.
- T2S integration like Euroclear.

## Related

- [[euroclear]] (sibling ICSD)
- [[govt-bonds]] · [[corp-bonds-ig]] · [[money-market-repo]]
- [[tri-party-vs-bilateral-repo]] (Clearstream is a tri-party agent in EU)
- [[allocation-affirmation-confirmation]] · [[dvp-rvp-fop]] · [[tplus-1-tplus-2]]
- [[arch-stp-pipeline]] · [[arch-jurisdictional-compliance]]
