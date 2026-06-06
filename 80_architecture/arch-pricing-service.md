---
type: architecture
layer: reference_data
status: draft
tags: [architecture/reference_data]
---

# Pricing Service

A central **valuation pricing** service distinct from market-data quotes ([[arch-quote-server]]) and streaming benchmarks ([[arch-realtime-analytics]]). Handles fair-value / indicative / mark-to-market / NAV pricing — typically model-driven — and serves as the **fallback price source** for [[arch-compliance|compliance]] (fat-finger reference), [[arch-position-service|positions]] (unrealized P&L), [[arch-risk-engine|risk]] (mark-to-market), and [[arch-tca|TCA]] (benchmark reference when live quote unavailable).

## Pricing vs. quotes vs. analytics — the three-way distinction

| Service | Source | Use |
|---|---|---|
| [[arch-quote-server]] | Live market data (venue/vendor feeds) | "What's the current market quote?" |
| [[arch-realtime-analytics]] | Derived from market data + own fills | "What's the running benchmark (VWAP/PWP/etc.)?" |
| `arch-pricing-service` (this) | Models + curves + manual marks | "What's this instrument worth right now if I had to value it?" |

Distinct because:

- Many instruments don't have live quotes (illiquid bonds, OTC derivatives, private positions).
- Risk needs **stable** marks even during quote gaps.
- Compliance fat-finger needs a price reference even when no quote exists.
- NAV / iNAV / model-based pricing are valuation, not quoting.

## Capabilities

| Capability | Use |
|---|---|
| **Indicative pricing** | Illiquid instruments where no live quote exists |
| **Model-based pricing** | OTC derivatives priced from curves + parameters |
| **Mark-to-market** | EOD / intraday valuation for risk and P&L |
| **NAV** | Fund / ETF net asset value |
| **iNAV** (intraday NAV) | Real-time ETF approximation (also published via realtime-analytics topic) |
| **Curve construction** | IR curves, credit curves, FX forward curves |
| **Surface construction** | Vol surfaces for options |
| **Manual marks** | Trader overrides with audit |

## Architecture

```mermaid
flowchart LR
  subgraph Inputs
    Q[[[arch-quote-server]]]
    HD[Historical data]
    REF[Curves / surfaces / parameters]
    MM[Manual marks]
  end

  subgraph "Pricing Service"
    REG[Pricing Model Registry<br/>versioned models]
    DISP[Dispatcher<br/>instrument -> model]
    EVAL[Model Evaluator]
    CACHE[Price cache + tail]
    AUD[Audit log]
  end

  subgraph Outputs
    PT[Price topic via SBE/Aeron]
    API[Snapshot API]
  end

  Q --> EVAL
  HD --> EVAL
  REF --> EVAL
  MM --> EVAL
  REG --> EVAL
  DISP --> EVAL
  EVAL --> CACHE
  CACHE --> PT
  CACHE --> API
  EVAL --> AUD
```

## Model registry

Pricing models are **pluggable, versioned, asset-class-scoped**:

| Model | Instrument scope | Inputs |
|---|---|---|
| Black-Scholes | listed options | underlying spot, vol, rate |
| Local-vol / SABR | option books | full vol surface |
| Bond YTM | govt + corp | cash flows, yield |
| OAS model | callable bonds | curves + vol |
| CDS pricing | CDS | credit curve |
| IRS pricing | IRS | yield curve |
| ETF iNAV | ETFs | constituent prices + weights |
| Composite | private | multiple sources weighted |

Each model has a `version`, declared input dependencies, and a pure `evaluate(instrument, context) -> Price`.

## Price envelope

```
Price {
  instrument                   FIGI
  kind                         INDICATIVE | MODEL | MTM | NAV | iNAV | MANUAL
  value                        decimal
  currency                     ccy
  side?                        BID | OFFER | MID
  confidence                   0..1
  computed_at                  timestamp
  model_id, model_version
  inputs_snapshot              {...}                 # for audit + replay
  source_quote_age?            duration              # how stale the underlying quote was
}
```

## Fallback chain (used by Compliance)

[[arch-compliance|Compliance's fat-finger check]] queries the pricing service with a configured fallback chain:

```
price_with_fallback(instrument):
  1. LIVE_L1 from quote_server (must be fresh < threshold)
  2. LAST_TRADE (fresh tolerance)
  3. PRICING_SERVICE_MODEL (this service)
  4. PRICING_SERVICE_INDICATIVE
  5. PRICING_SERVICE_CONSERVATIVE_UPPER_BOUND
  6. (none -> policy: block or use last_close)
```

The pricing service is steps 3-5. Each source is captured on the check event for audit.

## Manual marks

Trader / risk officer overrides:

- `set_manual_mark(instrument, value, expiry?, rationale, signed_off_by)` — gated by `#manual-mark-author` (3-layer per [[arch-tag-permissions]]).
- Manual marks are events; auditable.
- Expiry: manual marks expire after configurable window; after expiry, model price takes over.
- High-impact instruments (typically illiquid corp bonds, OTC derivatives) require four-eyes signoff.

## Curve / surface management

Curves (IR yield curves, FX forwards, credit curves) and surfaces (vol surfaces) are versioned reference data. Updates happen periodically (intraday or EOD); each update is an event. Older versions remain queryable for replay.

## License metering for pricing data

Some pricing inputs are licensed (e.g. IDC pricing, Bloomberg BVAL). The service:

- Tracks which licensed source contributed to each price.
- Meters per-firm access counts.
- Strips licensed values from outbound when receiving counterparty's license doesn't cover them.

Same pattern as [[arch-symbology-figi|symbology license metering]].

## Determinism / replay

Pricing model evaluations are pure functions. Inputs (curves, surfaces, quotes) are snapshotted on the produced price. [[arch-time-replay-server|Replay]] re-derives identical prices.

## API surface

```
operation: get_price
items: [{ instrument, kind?, as_of? }]
returns: Price

operation: subscribe_prices(filter) -> stream<Price>

operation: register_pricing_model
items: [{ model_id, version, asset_classes, code_artifact_ref, change_reason }]

operation: set_manual_mark
items: [{ instrument, value, expiry?, rationale, signed_off_by? }]

operation: register_curve / register_surface
operation: amend_curve / amend_surface
```

## See also

- [[arch-quote-server]] · [[arch-realtime-analytics]] · [[arch-tca]] · [[arch-symbology-figi]]
- [[arch-compliance]] (fat-finger fallback) · [[arch-risk-engine]] · [[arch-position-service]]
- [[arch-rfq]] (NAV-based ETF RFQ) · [[arch-event-sourcing]] · [[arch-time-replay-server]]
- [[arch-tag-permissions]] · [[arch-firm-desk-user]]
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[convertibles]] · [[mbs]] · [[abs]]
