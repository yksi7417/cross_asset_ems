---
type: clearing_settlement
kind: icsd
status: draft
tags: [clearing/icsd]
---

# Euroclear

**Euroclear Group** is one of the two main European International Central Securities Depositories (ICSDs) — the other being [[clearstream]]. Settles **EU and international cross-border securities** including bonds, equity, repos. Headquartered in Brussels; operates national CSDs in multiple EU countries (Belgium, France, Netherlands, UK, Sweden, Finland, Ireland).

## Asset classes / instruments

- European govts ([[govt-bonds]]) — Bunds, OATs, BTPs, SPGBs.
- European [[corp-bonds-ig]] / [[corp-bonds-hy]] / [[convertibles]].
- Eurobonds (USD/EUR/GBP/JPY denominated international bonds).
- European [[cash-equity]] (via local CSDs).
- European money-market instruments.
- Cross-border repo (Euroclear Bank's tri-party).

## Settlement cycle

- EU equity / corp: **T+2** standard ([[tplus-1-tplus-2]]); transitioning toward T+1.
- Eurobonds: T+2.
- Repo: T+0 or T+1.
- DVP / RVP / FOP supported.

## Membership / access

Euroclear participants: banks, brokers, custodians, central banks. Buy-side firms access via their custodian or as direct participants (very large firms).

## EMS touchpoints

- Settlement instructions generated post-allocation per [[arch-allocation-service]].
- Euroclear participant codes per account in [[arch-reference-data-service]].
- CSDR ([[emir-sftr-csdr]]) settlement-discipline penalties apply for fails post-T+2.
- T2S (TARGET2-Securities) is the ECB's settlement platform that Euroclear and other EU CSDs integrate with.

## Related

- [[clearstream]] (sibling ICSD)
- [[govt-bonds]] · [[corp-bonds-ig]] · [[money-market-repo]]
- [[allocation-affirmation-confirmation]] · [[dvp-rvp-fop]] · [[tplus-1-tplus-2]]
- [[emir-sftr-csdr]] (CSDR settlement discipline)
- [[arch-stp-pipeline]] · [[arch-jurisdictional-compliance]]
