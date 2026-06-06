---
type: clearing_settlement
kind: wire
status: draft
tags: [clearing/wire]
---

# Fedwire (Federal Reserve)

The **Federal Reserve's wholesale wire payment and securities settlement system** — operates two distinct services: **Fedwire Funds Service** (USD wire payments) and **Fedwire Securities Service** (US Treasury / Agency securities depository). Operated by the Federal Reserve Banks.

## Asset classes / instruments

### Fedwire Funds
- USD large-value wire payments — interbank, commercial, settlement of clearings (DTC, CHIPS).

### Fedwire Securities
- US [[govt-bonds|Treasury securities]] (cash, on-the-run, off-the-run).
- US Agency debt (Fannie, Freddie, Ginnie, FHLB, Farm Credit) — discount notes + bonds.
- Some Agency MBS pass-through.
- US [[money-market-tbills|T-bills]].

## Settlement cycle

- UST: **T+1** typically; same-day for some intraday transactions.
- Agency: T+1.
- Fedwire Funds: real-time gross settlement (RTGS) — settles within seconds.

## Membership / access

Fedwire participants: depository institutions with Fed accounts (banks, thrifts), plus some agency exceptions. Buy-side firms access Fedwire Securities through their primary dealer or custodian.

## EMS touchpoints

- UST settlement instructions flow to the primary dealer / custodian, which uses Fedwire for the depository leg.
- Real-time fail / settle confirmations come back via the custodian / dealer.
- Cash leg (Fedwire Funds) settles concurrently with the security leg for DVP.

## Operating hours

- Fedwire Funds: 21+ hours/day (Sunday 9pm ET start of business day).
- Fedwire Securities: 12+ hours/day (Monday-Friday).

## Related

- [[govt-bonds]] · [[money-market-tbills]] · [[money-market-repo]]
- [[treasury-direct]] (auction portal — settles via Fedwire)
- [[dtc]] (US equity / corp sibling) · [[ficc-clearing]] (FICC clears interdealer UST/repo)
- [[arch-stp-pipeline]]
