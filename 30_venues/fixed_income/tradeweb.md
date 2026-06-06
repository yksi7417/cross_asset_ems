---
type: venue
venue_kind: mtf
asset_classes: ["fixed_income", "rates_credit_deriv"]
status: draft
tags: [venue/mtf]
---

# Tradeweb

Multi-asset electronic trading network covering rates, credit, money markets, ETFs, and US equity-style products. Operates regulated venues in the US (SEC ATS + CFTC SEF), EU (MTF / OTF), UK (MTF), and APAC.

## Asset classes

- US Treasuries (on-the-run + off-the-run, primary dealer-to-client)
- EU and UK govt bonds (Bund, Gilt, BTP, OAT, etc.)
- USD / EUR / GBP IG and HY corporate bonds (D2C RFQ + portfolio trading)
- IRS, OIS (multiple SEFs / MTFs)
- CDS index (USD CDX, EUR iTraxx)
- ETF RFQ
- Mortgages (TBA-MBS), agency CMOs
- Money markets (CP, CD, repo D2C)
- US equity ETFs (via AiEX wrapper)

## Workflow mechanisms

- **Request-for-Quote (RFQ)** — disclosed to dealer set; the dominant D2C model.
- **Request-for-Market (RFM)** — two-sided quote.
- **AiEX** — automated execution wrapper for low-touch flow.
- **Portfolio Trading (PT)** — basket negotiation with risk-pricing.
- **Click-to-Trade (CTT)** — streaming dealer prices for govts and IRS.
- **Dealerweb** — interdealer (separate venue family).

## Connectivity

- **FIX 4.2 / 4.4** for order entry, RFQ, allocations, RFM. Custom tags for AiEX parameters, PT basket IDs, multi-leg swap definitions.
- REST API for reference and post-trade.

## Key facts

- Tradeweb pioneered D2C RFQ for US Treasuries.
- Strong in EU credit (alongside MarketAxess).
- Dealerweb is the interdealer arm (UST, MBS, repo).

## Related

- [[govt-bonds]] · [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[mbs]] · [[interest-rate-swaps]] · [[credit-default-swaps]]
- [[marketaxess]] · [[brokertec]] (competitors / complements)
- [[arch-rfq]] · [[arch-bulk-io]] (portfolio trading data ingest)
