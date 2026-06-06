---
type: architecture
layer: post_trade
status: draft
tags: [architecture/post_trade]
---

# STP Pipeline (Straight-Through Processing)

The orchestrated **post-trade pipeline** that runs after each fill: allocation → confirmation/affirmation → settlement instruction generation → regulatory reporting → books-and-records booking. Each stage is an independent service ([[arch-allocation-service]], [[arch-confirmation-affirmation]], [[arch-regulatory-reporting-service]], etc.); this note documents the **orchestration contract** that ties them together.

The [[stp-summary]] workflow gives the user-facing summary; this note is the architecture.

## Why architect this as a pipeline

Each stage is independently complex and independently fails. Without explicit pipeline orchestration:

- Stages run in unclear order, race each other.
- Failure recovery is per-stage and inconsistent.
- Re-runs after correction cascade unpredictably.
- Audit reconstruction is per-stage rather than per-trade.

With a documented pipeline:

- Stage ordering is declarative.
- Each stage's success/failure events drive the next.
- Pipelines pause on anomaly with ops queue.
- Replay reproduces the full pipeline byte-identically.

## Pipeline shape

```mermaid
flowchart LR
  F[Fill event] --> ALLOC[Allocation Service<br/>[[arch-allocation-service]]]
  ALLOC --> CONF[Confirmation / Affirmation<br/>[[arch-confirmation-affirmation]]]
  ALLOC --> SI[Settlement Instruction<br/>(per clearer)]
  ALLOC --> REG[Regulatory Reporting<br/>[[arch-regulatory-reporting-service]]]
  ALLOC --> BR[Books & Records<br/>internal accounting]

  CONF -.unmatched.-> OPS1[Ops Queue]
  SI -.failed.-> OPS2[Ops Queue]
  REG -.nack.-> OPS3[Ops Queue]
  BR -.mismatch.-> OPS4[Ops Queue]
```

Stages downstream of `ALLOC` run **in parallel** — confirmation, SI, reg-reporting, and B&R are independent.

## Stage ordering rules

| Stage | Triggered by | Prerequisites |
|---|---|---|
| Allocation | `RouteFilled` / `RoutePartiallyFilled` | Order has resolved template (or `AllocationDeferred`) |
| Confirmation | `AllocationApplied` | Counterparty enabled for confirmation |
| Settlement Instruction | `AllocationApplied` | Clearer enabled; account settlement instructions valid |
| Regulatory Reporting | `AllocationApplied` (or `RouteFilled` for venue-side reports) | Required fields populated; reporting deadline window |
| Books & Records | `AllocationApplied` | Internal account exists |

A single `AllocationApplied` event fans out to 4 downstream subscribers. Each subscriber tracks its own pipeline state per allocation.

## Per-trade pipeline state

For each fill, the pipeline tracks:

```
StpState {
  fill_id, order_id
  allocation_state         PENDING | APPLIED | REVERSED | DEFERRED | ANOMALY
  confirmation_state       PENDING | MATCHED | UNMATCHED | NOT_REQUIRED
  settlement_state         PENDING | INSTRUCTED | SETTLED | FAILED
  reporting_state          PENDING | SUBMITTED | ACKED | NACKED | RETRY
  books_state              PENDING | BOOKED | FAILED
  overall                  IN_PROGRESS | COMPLETE | ANOMALY
}
```

Projection from per-stage events. Surfaced on the operator dashboard so ops can see pipeline status for any trade.

## Anomaly handling

Anomalies (the unhappy paths):

| Anomaly | Stage | Resolution |
|---|---|---|
| Confirmation unmatched | Confirmation | Ops investigates; amend on one side; re-confirm |
| SI rejected by clearer | Settlement | Fix SI; resubmit |
| Reg report nack | Reporting | Service retries per policy; final fail → ops resubmits |
| B&R mismatch | Books | Investigate qty/price discrepancy; correct |
| Allocation impossible (account closed) | Allocation | Set new template; back-allocate |

Anomalies do **not** block other stages — confirmation can proceed even if reporting failed. Each is its own queue.

## Replay / re-run

A failed pipeline stage can be re-run after correction:

- Operator amends the broken artefact (e.g. SI fields).
- Triggers `resume_pipeline_stage(fill_id, stage)`.
- Stage re-evaluates with the corrected inputs; emits new attempt event.
- Other stages unaffected.

Full replay through [[arch-time-replay-server]] reproduces the original pipeline outcome under the original versions.

## Cross-stage events

```
StpFillIngested { fill_id, order_id }
StpPipelineStarted { fill_id, stages_planned: [...] }
StpStageComplete { fill_id, stage, outcome, downstream_artefact_ref }
StpStageAnomaly { fill_id, stage, reason, ops_queue }
StpPipelineComplete { fill_id, summary }
StpStageRetryRequested { fill_id, stage, by, reason }
```

## Determinism / replay

Pipeline orchestration is event-driven; each stage's decision is deterministic. Replay reproduces the same pipeline events under pinned versions. Outbound calls (e.g. to PB, regulator) are sandboxed in [[arch-time-replay-server|replay mode]].

## Per-asset-class variants

Different asset classes have different stages:

- **Cash equity**: allocation → SI ([[dtc]]) → TRACE not applicable → B&R.
- **Corp bond**: allocation → confirmation (less electronic) → SI ([[dtc]]) → [[trace|TRACE]] → B&R.
- **FX**: allocation → confirmation (often [[markitserv]]) → SI (bilateral or [[triparty-clearing|triparty]]) → CFTC/SDR (where applicable) → B&R.
- **OTC IRS / CDS**: allocation → confirmation ([[markitserv]]) → SI (CCP: [[lch]] / [[ice-clear]]) → [[cftc-sdr]] → B&R.
- **TBA / MBS**: allocation → confirmation (SIFMA TBA) → SI ([[ficc-clearing]]) → TRACE → B&R.

The pipeline orchestrator selects the per-asset stage profile from a configuration table.

## See also

- [[stp-summary]] · [[arch-allocation-service]] · [[arch-confirmation-affirmation]] · [[arch-regulatory-reporting-service]]
- [[arch-event-sourcing]] · [[arch-time-replay-server]] · [[arch-jmx-introspection]]
- [[arch-position-service]] · [[arch-venue-connectivity]]
- 40_regulatory/ · 50_clearing_settlement/ notes
