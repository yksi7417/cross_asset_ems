---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# Fungible vs. Non-Fungible Securities

**Fungible** instruments are **interchangeable units** — one share of AAPL is identical to another; one EUR/USD pip is the same as any other; one TBA-MBS deliverable swaps for any other within the same Good Delivery spec. The buyer doesn't care which specific unit they receive. **Non-fungible** (or **infungible**) instruments are **unique** — each unit has its own attributes that affect value (specified MBS pools, whole loans, bespoke OTC derivatives, NFTs).

Fungibility is **the structural driver of market mechanics**: fungible instruments support CLOB matching (price-time priority works because units are interchangeable); non-fungible instruments require RFQ / bilateral negotiation (each unit must be evaluated separately). It also drives **symbology** granularity (ISIN/FIGI per fungible CUSIP vs per-trade identifiers for non-fungible loans), **settlement** simplicity (fungible netting works; non-fungible doesn't), and **risk aggregation** (positions add for fungible; positions are per-instrument for non-fungible).

Some markets sit in the middle: **TBA-MBS** is fungible within Good Delivery specs (Fannie 30-year 5.5% July TBA) but the deliverable pools are themselves non-fungible. The trade is fungible; the underlying isn't.

## Examples across asset classes

| Asset class | Fungibility |
|---|---|
| Cash equity (one ISIN) | Fungible |
| US Treasuries (one CUSIP) | Fungible |
| Corporate bonds (one CUSIP) | Fungible |
| TBA-MBS (within Good Delivery spec) | Fungible at trade level |
| Specified MBS pools | Non-fungible (each pool unique) |
| Whole loans (mortgages, leveraged loans) | Non-fungible |
| FX spot (one currency pair, one value date) | Fungible |
| Vanilla IRS at standard terms | Fungible (post-CCP novation) |
| Bespoke OTC swaps | Non-fungible |
| Crypto fungible tokens (BTC, ETH) | Fungible |
| NFTs (ERC-721) | Non-fungible by design |
| Physical commodity (per grade) | Fungible at grade level |
| Specific commodity cargo | Non-fungible (per shipment) |
| Prediction-market contracts (specific question) | Fungible within contract |
| Structured products | Non-fungible (per term sheet) |

## Why it matters in an EMS

- **Market structure**: fungibility predicts CLOB vs RFQ workflows ([[clob-vs-rfq]]).
- **Symbology**: fungible instruments get [[arch-symbology-figi|FIGI / ISIN / CUSIP]]; non-fungible flow may require per-trade identifiers.
- **Settlement**: fungible netting in DVP; non-fungible requires per-instrument settlement.
- **Risk**: positions can be summed for fungible; not for non-fungible.
- **Compliance**: pattern detection (spoofing, wash) is mechanically simpler in fungible markets.

## Related

- [[tba-vs-specified-pool]] (the canonical fungible-vs-non-fungible distinction within MBS)
- [[clob-vs-rfq]] (mechanics implied by fungibility)
- [[netting]] · [[arch-aggregation]] (operations on fungible instruments)
- [[arch-symbology-figi]] (identification of fungible vs non-fungible)
