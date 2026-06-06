---
type: concept
status: draft
tags: [concept/glossary, glossary/equity, glossary/regulatory]
---

# ATS / ECN / MTF — Alternative Trading Venues

Three regional names for the same idea — **regulated electronic venues that are NOT exchanges** but provide trade execution under lighter regulatory burden than full exchanges:

- **ATS** (Alternative Trading System) — US SEC-registered venue under Reg ATS. Examples: IEX (later promoted to exchange), most broker dark pools, MEMX (also promoted), Liquidnet, Trumid.
- **ECN** (Electronic Communication Network) — older US term, mostly historical now; modern equivalents are SEC ATSs.
- **MTF** (Multilateral Trading Facility) — EU/UK MiFID II equivalent. Examples: Cboe Europe, Tradeweb MTF, Bloomberg MTF, MarketAxess Europe MTF.

The key distinction from a full exchange: ATSs/MTFs don't have to publicly display quotes pre-trade (so most run dark or selectively displayed), don't have listing functions, and have different post-trade-transparency obligations. They DO have to comply with Reg ATS / MiFID II — order record keeping, best-ex, fair access, system controls.

## Example

[[memx]] started as an ATS in 2019, then was promoted to a national securities exchange in 2020. Most broker dark pools (Sigma X, MS Pool) are SEC-registered ATSs. [[cboe-europe]] runs both lit and dark books as MTFs. The legal status drives the venue's regulatory profile, not its mechanics.

## Why it matters in an EMS

- Routing decisions must understand venue legal status (ATS vs exchange) for Reg-NMS purposes — only exchanges have protected quotes.
- Post-trade transparency timing and tagging differ.
- See [[arch-jurisdictional-compliance]] for the per-regime venue obligations.

## Related

- [[reg-nms]] · [[nbbo-ebbo]] · [[lit-vs-dark]]
- [[iex]] · [[memx]] · [[trumid]] · [[bloomberg-bridge]] (ATSs)
- [[cboe-europe]] · [[bloomberg-bmtf]] (MTFs)
- [[arch-jurisdictional-compliance]] · [[arch-best-execution]]
