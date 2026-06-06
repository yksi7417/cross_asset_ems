---
type: index
status: draft
tags: [index/brokers]
---

# Brokers — Equity Routing Destinations

A **broker note** in this vault describes a sell-side firm as an **equity routing destination** — what the EMS gets when it sends a child order to that broker. Each broker note follows the same shape so cross-broker comparison is mechanical.

## Dual-role disclaimer

The same legal entities (Goldman Sachs, Morgan Stanley, UBS, JPMorgan, Citi, Bank of America, Barclays, Deutsche Bank, BNP Paribas, etc.) operate across **all three** asset classes. They appear under [[brokers/]] here as **equity routing destinations** because the equity routing concept — algo suite, DMA, dark pool, capital commitment, central risk book — is meaningfully distinct from FI dealing or FX market making.

The same firms are reached as:

- **Fixed-income dealers** through [[marketaxess]] / [[tradeweb]] / [[bloomberg-bridge]] dealer-direct RFQ — see [[arch-rfq]] and [[corp-bonds-ig]] / [[corp-bonds-hy]].
- **FX liquidity providers** through [[refinitiv-fxall]] / [[360t]] / [[fxspotstream]] / [[ebs]] and dealer-direct — see [[fx-spot]] and [[arch-rfq]].
- **Swap counterparties** through [[bloomberg-sef]] / [[tradeweb]] / [[bloomberg-bmtf]] for cleared swaps.

A broker note does not duplicate the FI / FX coverage — it covers **equity-routing offering only**.

## Shared template

Each broker note covers, at minimum:

1. **Algorithmic suite** — what algos the broker offers (VWAP, TWAP, IS, POV, dark-seek, IS-x, close, opportunistic, plus broker-specific brands). FIX routing instruction encoding.
2. **DMA (Direct Market Access)** — does the broker offer DMA pass-through, what venues, latency profile.
3. **Dark pool / internalizer** — does the broker run an ATS for client crossing (e.g. Sigma X, Pool, MS Pool, ATS-2, JPMX, Cross Finder).
4. **Capital commitment** — block-trading desk willing to take a position vs. agency-only.
5. **RFQ desk / ETF block** — equity block RFQ and ETF block capabilities.
6. **Central Risk Book (CRB)** — modern broker offering where flow is internalized against a centrally-managed risk book, often with better economics than naked agency.
7. **Connectivity** — FIX dictionary version, drop-copy, allocation instruction handling.

## Brokers in this folder

- [[goldman-sachs]] (Sigma X dark pool, GS-PSI, Atlas algo family, large CRB)
- [[morgan-stanley]] (MS Pool ATS, BLINK / NIGHT-OWL algos)
- [[ubs]] (UBS PIN ATS, UBS Tap algos)
- [[jpmorgan]] (JPMX ATS, AQUA / JET / Open Cross algos)
- [[citi]] (Citi Match ATS, Dagger / Smart Click algos)
- [[bank-of-america]] (Instinct X ATS, Quant Strategies)
- [[barclays]] (LX ATS, Power algos)
- [[instinet]] (agency-only, Newport Pro / EX algo suite, BlockMatch ATS)

## What's *not* in this folder

- **Exchanges** (NYSE, Nasdaq, Cboe, IEX) — they're in [[equity/]].
- **MTFs / ATSs operated by exchanges** — also in [[equity/]].
- **Independent agency brokers** that don't run their own ATS — still here under [[brokers/]] (e.g. [[instinet]]).
- **FI dealers and FX LPs** — reached through the multi-dealer platforms (per dual-role disclaimer).

## Related

- [[_venue-index]] (the parent venue index)
- [[arch-smart-order-router]] (decides between brokers and venues)
- [[arch-best-execution]] (selection-rationale audit)
- [[arch-ioi]] (some brokers source IOIs to influence selection)
- [[bloomberg-emsx]] (reaches all 3,700+ brokers via FIX)
