---
type: clearing_settlement
kind: ccp
status: draft
tags: [clearing/ccp]
---

# FICC (Fixed Income Clearing Corporation)

**FICC** is a DTCC subsidiary providing **clearing and netting for US Treasury cash, UST repo, and TBA MBS**. Two operational divisions:

## Divisions

### GSD — Government Securities Division

- Clears UST cash trades (interdealer + Sponsored — see below).
- Operates **GCF Repo Service** ([[gcf-repo]]).
- Operates **Sponsored Service** for bilateral repo (buy-side access via a sponsor).
- Operates **CCIT (Centrally Cleared Institutional Tri-party)** — emerging buy-side clearing.

### MBSD — Mortgage-Backed Securities Division

- Clears TBA MBS interdealer flow.
- Net settlement around the SIFMA monthly Class A/B/C/D dates.

## Asset classes / instruments

- US [[govt-bonds|Treasuries]] cash and [[money-market-tbills|T-bills]].
- US Treasury [[money-market-repo|repo]] (GCF and bilateral via Sponsored).
- TBA [[mbs]].
- **Future expansion (2025-2026)**: SEC Rule 17ad-22 expands UST clearing mandate — significant buy-side onboarding wave incoming.

## Settlement / margining

- Multilateral netting reduces gross settlement by ~95% for interdealer UST.
- Daily margin with VaR-based methodology.
- DVP via [[fedwire]] for net amounts.

## Membership / access

Clearing members are major US banks. Buy-side firms historically did not clear FICC; the 2025+ expansion changes this — many now onboarding via the Sponsored Service.

## EMS touchpoints

- FICC trade submission via FIX for member firms.
- Buy-side firms access via sponsor — submission through the sponsor's clearing pipeline.
- Settlement nets via Fedwire — see [[fedwire]].

## Related

- [[govt-bonds]] · [[money-market-tbills]] · [[money-market-repo]] · [[mbs]]
- [[gcf-repo]] · [[tri-party-vs-bilateral-repo]]
- [[fedwire]] (settlement leg) · [[dtc]] (DTCC sister)
- [[ficc-reporting]] (reporting component) · [[arch-stp-pipeline]]
