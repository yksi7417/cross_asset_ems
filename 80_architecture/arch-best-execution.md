---
type: architecture
layer: foundation
status: draft
tags: [architecture/foundation, architecture/compliance]
---

# Best-Execution Obligation & Evidence

The **firm-wide capability** to satisfy and demonstrate best-execution obligations across jurisdictions. Cuts across [[arch-smart-order-router|SOR]] (the selection), [[arch-tca|TCA]] (the measurement), [[arch-regulatory-reporting-service|regulatory reporting]] (the periodic reports), and [[arch-compliance|compliance]] (policy adherence). This note is the **audit chain** that ties them together so an auditor or regulator can demand evidence and the firm can produce it mechanically.

## The obligation — per-jurisdiction

Best execution is **not** a single global standard. Each regime imposes its own duty:

| Jurisdiction | Standard | Detail |
|---|---|---|
| **EU MiFID II** | "All sufficient steps" to achieve best result for the client | Five execution factors: price, costs, speed, likelihood of execution, settlement (RTS 28). Plus instrument-class-specific factors. Best-ex policy required, reviewed annually, disclosed to clients. RTS 27 (venue) + RTS 28 (firm top-5 + quality) reports. |
| **UK** (post-Brexit) | Same as MiFID II in substance; FCA COBS 11.2A | Mirror of EU regime; UK-specific reports. |
| **US** | "Reasonable diligence" for retail/institutional | FINRA Rule 5310. Reg NMS imposes trade-through prevention for US equities. Order Protection Rule. |
| **HK** (SFC) | "Best terms reasonably available" | SFC Code paragraph 3.2; equivalent factors. |
| **SG** (MAS) | "Best possible result on a consistent basis" | MAS Guidelines on Provision of Investment Advice. |
| **AU** (ASIC) | Client's-best-interests duty (post-FOFA) | RG 175; sometimes stricter than MiFID for retail. |
| **JP** (JFSA) | "Best execution for customers" | FIEA Article 40-2; published policy required. |
| **CA** (IIROC / CSA) | "Best execution" per UMIR | Reasonable steps + venue analysis. |

The firm operating across multiple jurisdictions implements the **strictest applicable** standard per client/jurisdiction and produces evidence for each.

## The five MiFID II execution factors

Best-ex policy ranks importance of:

1. **Price** — the executed price.
2. **Costs** — commissions, fees, rebates, settlement costs, market impact.
3. **Speed** — time from arrival to execution.
4. **Likelihood of execution & settlement** — fill rate, settle rate.
5. **Size, nature, and other relevant considerations** — block-size handling, urgency, client preference.

For each instrument class, the policy weights these factors. For retail clients, **total consideration** (price + costs) dominates.

## Evidence chain

For any executed order, the firm must reconstruct:

```mermaid
flowchart LR
  P[Best-Ex Policy<br/>versioned] --> O[Order received]
  O --> C[Client classification<br/>retail / professional / ECP]
  C --> F[Applicable factors<br/>+ weights]
  F --> R[Recommendation<br/>[[arch-pretrade-analytics]]]
  R --> SOR[SOR selection<br/>[[arch-smart-order-router]]]
  SOR --> RT[Routing decision<br/>broker / venue / strategy]
  RT --> EXE[Execution]
  EXE --> M[Measurement<br/>[[arch-tca]]]
  M --> S[Slippage decomposition]
  S --> A[Aggregation per period]
  A --> RP[Periodic Reports<br/>(RTS 28, internal BEC)]
  M --> CL[Client on-demand report]
```

Every arrow is an **event** in [[arch-event-sourcing|the log]] with explicit versions:

- Policy version active at order arrival.
- Pre-trade recommendation model + version.
- SOR strategy + version, wheel definition hash, selection seed.
- Pre-trade benchmark snapshot (arrival mid, NBBO, last) for reference.
- Routing decision: chosen broker / venue with **rationale** (which factor drove the choice).
- Execution events including child fills.
- Per-fill TCA results with versioned decomposition model.

A regulator asking "why was broker X chosen for client Y's order on date Z" gets a reproducible answer.

## Per-order best-ex record

For each order:

```
BestExRecord {
  order_id, client_id, client_classification
  policy_version
  factors_applicable      [{ factor, weight }]
  pretrade_recommendation { model_id, model_version, snapshot }
  routing_decisions       [{
    decision_id, decision_at, decision_by (sor_strategy_id | human),
    chosen { broker, venue, algo, params },
    alternatives_considered [{ broker, venue, algo, expected_cost_bps, why_not }],
    rationale_human_readable
  }]
  execution_results [{
    fill_id, executed_at, executed_price, executed_qty,
    benchmarks_at_fill { arrival, vwap, twap, pwp, nbbo, mid },
    slippage { vs_arrival_bps, vs_vwap_bps, ... },
    decomposition { impact_bps, timing_bps, opportunity_bps, spread_capture_bps },
    decomposition_model_version
  }]
  policy_adherence_check {
    policy_version, evaluated_at,
    factor_outcomes [{ factor, expected, actual, outcome_score }],
    overall_outcome ALIGNED | DEVIATED | EXCEPTION,
    explanation
  }
}
```

Projected continuously by [[arch-projection-engine]]. Queryable per-order, per-client, per-period.

## Broker selection audit

The user asked specifically about "trading of best brokers" — the audit chain for broker / algo / venue selection:

### Pre-selection: panel construction

Per asset class, per instrument tier, per client class, the firm maintains a **broker panel**:

- Eligibility (regulatory permission, [[counterparty-enablement]] active, documentation in place — see [[isda]] / [[csa]] / [[gmra]]).
- Tier (top tier / second tier / sit-out per current cycle).
- Strategy coverage (which algos this broker supports).

Panel changes are events:

```
BrokerPanelAmended {
  panel_id, panel_version,
  added: [broker],
  removed: [broker],
  tier_changes: [{ broker, old_tier, new_tier }],
  reason,
  signed_off_by   # Best-Execution Committee
}
```

### Selection: SOR algo wheel + recommendation

[[arch-smart-order-router|SOR's algo wheel]] selects from the panel. Logged on every selection:

```
WheelSelectionLogged {
  rfq_or_route_id, wheel_id, wheel_version,
  selection_mode: ROUND_ROBIN | WEIGHTED_RANDOM | PERFORMANCE_TIER | COMMISSION_TIER
  bucket_chosen { broker, algo, params, weight }
  alternatives_considered [{ bucket, weight, why_not }]
  seed_for_random         # for replay reproducibility
  performance_inputs_used [{ broker, recent_perf_metric_value }]
}
```

This is the **best-ex audit answer** to "why was broker X chosen": the wheel version, the weights, the seed, the alternatives considered, and the performance-history inputs.

### Manual selection (trader override)

When a trader manually selects a broker bypassing SOR, the rationale must be captured:

```
ManualBrokerSelection {
  order_id, broker_chosen, by,
  reason_code: BLOCK_TRADE | NATURAL_AXE | CLIENT_DIRECTED | OTHER
  free_text_rationale, signed_off_by?
}
```

Some firms require senior sign-off on manual override of SOR for non-trivial size.

## Execution slippage evidence

Per-fill TCA ([[arch-tca]]) produces:

- Multiple benchmarks (arrival, VWAP, TWAP, PWP, close).
- Slippage in basis points vs each.
- Cost decomposition (impact / timing / opportunity / spread-capture).

Per-broker aggregates roll up to:

- **Median slippage** vs benchmark per broker per asset-class tier.
- **Fill rate** per broker.
- **Latency** distribution.
- **Reject rate**.
- **Effective spread captured** for passive fills.

These metrics feed:

- The algo wheel's `PERFORMANCE_TIER` selection (read-only).
- RTS 28 / internal BEC reports.
- Per-broker scorecards reviewed quarterly by the Best-Execution Committee.

## Periodic reports

### MiFID II RTS 28 (firm — annual)

Top-5 venues per asset class per client class, with execution-quality metrics:

```
Rts28Report {
  firm_lei, reporting_period,
  per_asset_class [
    per_client_class [
      top5_venues [{ venue, percentage_volume, percentage_orders, passive_pct, aggressive_pct }],
      summary_of_execution_quality (narrative + numbers)
    ]
  ]
}
```

Generated by [[arch-regulatory-reporting-service]] pulling from TCA aggregates.

### MiFID II RTS 27 (venue — quarterly; firm consumes)

Per-venue execution quality, published by the venue. The firm consumes these to inform its own RTS 28 narrative.

### Internal Best-Execution Committee (BEC) review

Quarterly (or more frequent) review:

- Per-broker scorecards.
- Per-venue scorecards.
- Outlier orders (top N largest slippage).
- Manual override frequency.
- Algo wheel weight recommendations.
- Policy amendments proposed.

BEC decisions are events:

```
BecDecisionRecorded {
  bec_meeting_id, decisions: [
    { kind: WHEEL_WEIGHT_CHANGE | PANEL_AMENDMENT | POLICY_UPDATE | INVESTIGATION,
      details, signed_off_by }
  ]
}
```

### US — quarterly reviews

FINRA Rule 5310 requires "regular and rigorous" review. Less prescriptive than RTS 28 but evidence required on demand.

### Other jurisdictions

Each has its own cadence and form; the engine handles all from the same TCA aggregates.

## Client on-demand reports

Clients (especially institutional) request periodic best-ex reports. The system produces:

- Per-period TCA summary.
- Per-broker / per-venue distribution.
- Slippage vs the client's chosen benchmark.
- Outliers with explanation.

Formats: PDF, XLSX, structured (CSV / JSON) per client preference. Generated via [[arch-bulk-io]] export.

## Audit reconstruction

A regulator asks: "On 2026-03-15, why did you choose broker GS via algo VWAP for order #12345?"

System answer (mechanical):

```
order #12345 staged 2026-03-15 09:32:14.123 UTC
  client: ACME Pension Fund (classification: Professional)
  best-ex policy active: v17 (effective 2026-01-01, signed off 2025-12-15 by BEC)
  factors weighted: price 40%, cost 25%, speed 15%, likelihood 10%, size 10%
  pretrade recommendation: model market_impact_v9 -> VWAP strategy, expected cost 4.2bps
  SOR strategy: EQ_US_LARGE_CAP_WHEEL_v3 (signed off 2025-10-10)
    selection mode: WEIGHTED_RANDOM with deterministic seed 0xAB12...
    buckets considered: GS/VWAP (w=25), MS/IS (w=25), JPM/TWAP (w=20), CITI/POV (w=20), BAML/VWAP (w=10)
    chosen: GS/VWAP (w=25, random selection)
  performance inputs at time of selection:
    GS/VWAP rolling 30d IS bps: 3.8 (median)
  execution: 17 child fills 09:32:18 - 12:15:45 UTC
  TCA: vs_arrival 3.6 bps, vs_vwap +0.2 bps
  decomposition (model bertsimas_lo_v2): impact 2.1, timing 1.5
  policy adherence: ALIGNED (cost outcome within expected band)
```

Every line of the answer references events in the log; every event has a version pinned. The auditor can verify each component independently.

## Anti-patterns

- **"We followed the policy" without evidence.** Regulators require demonstrable evidence per order, not blanket assertions.
- **Cherry-picked metrics.** RTS 28 narrative must include unflattering outliers, not just averages.
- **Stale policy.** Policy reviewed annually minimum; major market structure changes can require ad-hoc updates.
- **Manual selection without rationale.** A trader picking GS over the wheel-chosen MS without recording why = audit failure.
- **TCA model that always shows the firm winning.** If the decomposition model consistently flatters, BEC must investigate.
- **No closing of the loop.** Periodic reports without resulting policy / panel / weight changes = process box-ticking.

## Anatomy of the audit chain across services

| Concern | Owner | Note |
|---|---|---|
| Policy storage + versioning | [[arch-reference-data-service]] | Each version is reference data |
| Policy adherence per order | [[arch-compliance]] | Adherence check at routing time |
| Pre-trade strategy recommendation | [[arch-pretrade-analytics]] | Recommendation logged |
| SOR strategy + wheel selection | [[arch-smart-order-router]] | Full selection rationale logged |
| Manual override rationale | [[arch-order-staged]] / [[arch-router-layer]] | Captured at decision |
| Execution measurement | [[arch-tca]] | Per-fill events |
| Periodic reports | [[arch-regulatory-reporting-service]] | RTS 28, US 5310 |
| Audit reconstruction | [[arch-event-sourcing]] + [[arch-time-replay-server]] | Replay any moment |
| Cross-jurisdiction obligations | [[arch-jurisdictional-compliance]] | Per-jurisdiction standards |

## See also

- [[arch-tca]] · [[arch-smart-order-router]] · [[arch-pretrade-analytics]] · [[arch-realtime-analytics]]
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]] · [[arch-compliance]]
- [[arch-event-sourcing]] · [[arch-time-replay-server]] · [[arch-projection-engine]] · [[arch-reference-data-service]]
- [[arch-bulk-io]] (client report exports) · [[arch-firm-desk-user]] (client classification)
- [[counterparty-enablement]] · [[broker-codes]] · [[allocation-prime-broker]]
- [[route-to-algo]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[arch-router-layer]]
