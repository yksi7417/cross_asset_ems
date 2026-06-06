---
type: architecture
layer: analytics
status: draft
tags: [architecture/analytics]
---

# TCA — Transaction Cost Analysis

**Post-trade execution analytics** comparing actual fills against benchmarks, decomposing slippage into market-impact and timing components, and feeding the results back into [[arch-smart-order-router|SOR algo wheel]] performance scoring and the Best Execution Committee's periodic review.

## Purpose

Three audiences, three answers:

- **Traders**: "Did my execution beat or miss the benchmark on this order?"
- **Best Execution Committee**: "Across the desk, which brokers / algos are delivering best execution? Where do we re-weight the wheel?"
- **Clients / Regulators (MiFID II RTS 27/28, US best-ex)**: "Demonstrate systematic best execution with audit-quality evidence."

TCA is **post-trade** but **continuous** — slippage events stream out per fill, not batched at end-of-day.

## Architecture

```mermaid
flowchart LR
  subgraph Inputs
    F[Fill events<br/>from [[arch-event-sourcing]]]
    B[Benchmarks<br/>from [[arch-realtime-analytics]]]
    R[Pre-trade recommendations<br/>from [[arch-pretrade-analytics]]]
    M[Market state at fill]
  end

  subgraph "TCA Service"
    SLIP[Slippage Calculator<br/>vs each benchmark]
    DEC[Cost Decomposer<br/>impact vs timing vs opportunity]
    AGG[Aggregator<br/>per broker / algo / venue / instrument]
    FB[Feedback to SOR wheel<br/>performance metrics]
    REP[Report Generator]
  end

  subgraph Outputs
    EV[FillTcaComputed event]
    DB[TCA store]
    SUM[Daily / weekly / monthly reports]
    WS[Algo wheel feed]
  end

  F --> SLIP
  B --> SLIP
  R --> DEC
  M --> SLIP
  M --> DEC
  SLIP --> EV
  SLIP --> DEC
  DEC --> AGG
  AGG --> DB
  AGG --> FB
  FB --> WS
  AGG --> REP
  REP --> SUM
  EV --> DB
```

## Per-fill TCA event

Computed for every fill, emitted to the TCA stream:

```
FillTcaComputed {
  fill_id, order_id, route_id, executed_at
  executed_price, executed_qty
  benchmarks_at_fill:
    arrival_price, mid_at_fill, nbbo_at_fill
    vwap_to_date, twap_to_date, pwp_at_rate, day_close (later)
  slippage_bps:
    vs_arrival, vs_mid, vs_vwap, vs_twap, vs_pwp, vs_close (later, retroactive)
  decomposition:
    impact_bps              // permanent price impact
    timing_bps              // intraday timing cost
    opportunity_cost_bps    // if order was scaled down / cancelled
    spread_capture_bps      // for passive fills
  attribution:
    broker, algo, strategy, venue, sor_strategy_id
  recommendation_at_arrival? : Recommendation snapshot from [[arch-pretrade-analytics]]
  recommendation_vs_actual: {
    recommended_strategy, actual_strategy
    expected_cost_bps, actual_cost_bps
    delta_bps
  }
}
```

The `recommendation_vs_actual` field is the **bridge to pre-trade analytics** — closes the loop on model calibration.

## Benchmark catalog used

Pulled live from [[arch-realtime-analytics]]:

- **Arrival**: mid at order arrival timestamp.
- **VWAP**: from arrival through fill timestamp.
- **TWAP**: from arrival through fill.
- **PWP**: at the order's effective participation rate.
- **Close**: official close (computed retroactively at session end).
- **Mid at fill**: instantaneous reference.

Per-asset-class adjustments: FX uses a chosen benchmark (WMR fix, mid at trade), FI uses bid-ask midpoint at execution venue, ETF includes iNAV.

## Cost decomposition

Slippage = `executed_price - arrival_price` (sign-corrected for side). Decomposed:

| Component | Definition |
|---|---|
| **Impact** | how much price moved due to *our* trading |
| **Timing** | how much price moved between arrival and execution due to *other* market activity |
| **Opportunity** | cost of unfilled residual (cancelled / expired qty) measured against later prices |
| **Spread capture** | for passive fills: half-spread captured |

Decomposition models are themselves pluggable (Almgren-Chriss attribution, Bertsimas-Lo, simple proportional) — versioned, replayable.

## Aggregation views

Standard aggregations served via API + dashboards:

- Per broker per asset class per period
- Per algo / strategy per period
- Per venue per asset class
- Per SOR strategy
- Per instrument tier (liquid / mid / illiquid)
- Per trader / desk

Aggregation is itself a projection from the per-fill TCA events.

## Feedback loop to SOR

The algo wheel's `PERFORMANCE_TIER` selection mode reads from a TCA projection: the rolling N-day performance per (broker, algo, instrument tier). The wheel doesn't auto-rebalance weights (that's BEC sign-off territory) but it does use the metric as input.

**Critical**: this is a **read-only loop from TCA's perspective**. TCA outputs metrics; the wheel reads them. Weight changes are explicit `WheelWeightChanged` events with reasons and signoffs.

## Best-Ex reporting (regulatory)

MiFID II RTS 27/28 and US best-ex require demonstrating systematic best execution. TCA produces:

- **Per-broker quality scores** in standardized format.
- **Periodic reports** with full audit trail (which fills, which benchmarks, which models).
- **Selection rationale** for current algo wheel weights with backing TCA evidence.

These artifacts are subpoena-ready: each report links to its source event log slice + benchmark versions + decomposition model versions.

## Determinism / replay

TCA is a pure projection from `(fill events, benchmark events, pre-trade recommendation events)` under a specified decomposition model version. [[arch-time-replay-server|Replay]] produces identical TCA results for any historical slice. New decomposition models are new versions; old TCA results aren't retroactively re-computed (they're frozen in audit).

## Anti-patterns

- **Benchmark of convenience.** Selecting only flattering benchmarks for reporting. Standard set is mandated by firm policy and shown to BEC unedited.
- **Cherry-picked aggregations.** Hide individual fills inside aggregate averages. Drilldown to per-fill must always be possible.
- **Stale models.** A decomposition model unchanged for years may not reflect current microstructure. BEC reviews model fit quarterly.

## API surface

```
operation: query_tca(filter, aggregation, period) -> TcaReport

operation: subscribe_fill_tca(filter) -> stream<FillTcaComputed>

operation: register_decomposition_model
items: [{ model_id, version, formula_ref, change_reason }]

operation: export_best_ex_report(period, format: MIFID_RTS28 | INTERNAL_BEC)
returns: report_artifact
```

## See also

- [[arch-realtime-analytics]] · [[arch-pretrade-analytics]] · [[arch-smart-order-router]]
- [[arch-event-sourcing]] · [[arch-time-replay-server]] · [[arch-position-service]]
- [[arch-compliance]] · [[arch-surveillance]] · [[regulatory-base]]
- [[route-to-algo]] · [[stp-summary]]
