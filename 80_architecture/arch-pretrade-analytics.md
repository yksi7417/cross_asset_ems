---
type: architecture
layer: analytics
status: draft
tags: [architecture/analytics]
---

# Pre-trade Analytics

A **pluggable quant model registry** that takes an order intent and returns recommended execution strategies, expected cost, and trajectory advice. Consumed primarily by [[arch-smart-order-router|SOR]] for strategy selection and by traders / FXPV-style UIs for pre-flight reasoning.

## Purpose

Replace ad-hoc trader intuition + spreadsheet TCA estimates with **systematic, replayable, model-driven** pre-trade advice:

- **Estimated cost** of executing this order under candidate strategies.
- **Recommended strategy** (algo wheel input).
- **Optimal trajectory** (Almgren-Chriss style — pace, urgency).
- **Liquidity forecast** — expected available volume over the day / window.
- **Spread forecast** — expected bid-ask through the day.
- **Impact estimate** — temporary vs. permanent.

The recommendations are **advisory** — they inform automation and humans but do not block. Decisions still flow through [[arch-validator|Validator]] → [[arch-compliance|Compliance]] → [[arch-risk-engine|Risk]] → routing.

## Architecture

```mermaid
flowchart LR
  subgraph Inputs
    O[Order intent<br/>instrument, side, qty, urgency, account]
    M[Market state<br/>[[arch-realtime-analytics]], [[arch-quote-server]]]
    H[Historical context<br/>own-fills + market history]
    R[Reference data<br/>liquidity tiers, spread profiles]
  end

  subgraph "Pre-trade Analytics Service"
    REG[Model Registry<br/>versioned models]
    DISP[Dispatcher<br/>asset-class -> model]
    EVAL[Model Evaluator]
    CACHE[Recommendation cache<br/>per (order signature)]
  end

  subgraph Outputs
    REC[Recommendation envelope]
    EV[PreTradeRecommended event]
  end

  O --> DISP
  M --> EVAL
  H --> EVAL
  R --> EVAL
  DISP --> EVAL
  REG --> EVAL
  EVAL --> CACHE
  CACHE --> REC
  REC --> EV
```

## Pluggable model interface

```
PreTradeModel {
  model_id              string
  version               int
  asset_classes         set<AssetClass>
  instruments_filter    Predicate           // e.g. liquidity tier, sector
  inputs_required       [InputKind]         // e.g. NBBO, VWAP, ADV (Average Daily Volume), historical spread
  evaluate(order, market_snapshot, history) -> Recommendation
}

Recommendation {
  model_id, model_version, generated_at
  cost_estimate         { bps_to_arrival, bps_to_close, variance }
  strategy_advice       [{ strategy: ALGO_VWAP | ALGO_IS | DARK_FIRST | ...,
                           score, expected_cost_bps, rationale }]
  trajectory            optional schedule [{ time, slice_qty, target_price }]
  liquidity_forecast    optional curve
  cautions              [string]            // e.g. "earnings tonight", "low ADV today"
  inputs_used           snapshot            // for audit + replay
}
```

## Model categories (illustrative)

| Model kind | Inputs | Output |
|---|---|---|
| Market Impact (linear / square-root / Almgren-Chriss) | ADV, volatility, spread, order qty | bps cost estimate, optimal duration |
| Liquidity Profile (intraday volume curve) | historical 5-minute bars | volume forecast for slicer |
| Spread Forecast | historical spread, current state | expected spread by hour |
| Strategy Recommender | order shape, urgency, instrument tier | ranked strategies with scores |
| Optimal Trajectory (Almgren-Chriss) | risk aversion, volatility, urgency | time-sliced schedule |
| Liquidity Aggressor Detector | recent venue activity | should-be-passive vs should-be-aggressive flag |
| ETF arbitrage premium | iNAV vs ETF mid | enter / wait / split rec |

Each is registered, versioned, asset-class-scoped. Multiple competing models per category are normal; the dispatcher picks based on **firm-policy + instrument tier + model performance** (informed by [[arch-tca|TCA]] feedback).

## How SOR consumes it

The SOR pipeline gains an optional `consult_pretrade_analytics` step:

```
Route arrives at SOR
  ├── consult_pretrade_analytics(order)        # NEW; optional
  │     -> Recommendation
  ├── strategy_selector(recommendation, wheel) # wheel still picks broker, but seeded by rec
  └── decide(child routes per chosen strategy)
```

SOR's [[arch-smart-order-router|algo wheel]] can incorporate the recommendation:

- `STRATEGY_RECOMMENDED`: pick the wheel bucket matching the model's top strategy advice.
- `COST_RANKED`: weight buckets by inverse expected cost.
- `IGNORE`: wheel runs purely on round-robin / random; recommendation is captured for audit only.

The choice is **per-firm policy** with full audit.

## Determinism / replay

Models are **pure functions**. Inputs are snapshotted on the recommendation (market state at time T, historical window). [[arch-time-replay-server|Replay]] re-runs the same model at the same version and produces identical recommendations. Model upgrades produce new versions; old events keep their old version's recommendation.

## Anti-patterns

- **Models with external network calls.** Breaks replay. Pre-fetch all data; evaluate in-memory.
- **Mutable model state.** A model whose internal parameters drift between calls is unauditable. Parameters are versioned reference data; bumps are events.
- **Recommendation as policy.** The model recommends; the firm decides whether to follow. Hard-wiring "always do what the model says" loses the human-judgment safety net.

## API surface

```
operation: pretrade_recommend
items: [{ order_intent, urgency?, override_policy? }]
returns: Recommendation

operation: register_pretrade_model
items: [{ model_id, version, asset_classes, code_artifact_ref, change_reason }]

operation: amend_pretrade_model
items: [{ model_id, version_bump, parameters, change_reason, signed_off_by }]

operation: list_pretrade_models(filter)
```

## See also

- [[arch-smart-order-router]] · [[arch-realtime-analytics]] · [[arch-tca]]
- [[arch-quote-server]] · [[arch-event-sourcing]] · [[arch-time-replay-server]]
- [[arch-validator]] · [[arch-compliance]] · [[arch-firm-desk-user]] · [[arch-tag-permissions]]
- [[route-to-algo]] · [[fx-automation-tradebest]]
