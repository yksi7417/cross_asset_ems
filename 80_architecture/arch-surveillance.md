---
type: architecture
layer: auxiliary
status: draft
tags: [architecture/auxiliary]
---

# Surveillance Service

**Post-trade and continuous pattern detection** for market-abuse risk. Sister to [[arch-compliance|Compliance]] — the synchronous "block" path lives in compliance; the asynchronous "detect after the fact" patterns live here. Outputs alerts; in severe cases triggers actor freeze that compliance enforces on subsequent pre-trade gates.

## Purpose

Run continuous detectors for:

- **Spoofing / layering** — cancel-heavy activity on one side, fills on the other.
- **Wash trades** — buy + sell same beneficial owner in close proximity.
- **Marking the close** — large activity in the final minutes in positions of interest.
- **Quote stuffing** — high `35=G`/`35=F` rate causing book churn.
- **Front-running** — own-account trades ahead of client trades.
- **Cross-market manipulation** — coordinated activity across related instruments / venues.
- **Insider trading patterns** — trades preceding material public events (post-hoc with context).

## Architecture

```mermaid
flowchart LR
  EL[Event Log<br/>[[arch-event-sourcing]]] --> SUB[Subscriber: all order/route/fill events]
  SUB --> D1[Spoofing Detector]
  SUB --> D2[Wash-trade Detector]
  SUB --> D3[Quote-stuffing Detector]
  SUB --> D4[Marking-close Detector]
  SUB --> D5[Front-run Detector]
  SUB --> D6[Cross-market Detector]
  REF[News / corporate-action feed] --> D7[Insider-pattern Detector]
  D1 --> A[Alert Pipeline]
  D2 --> A
  D3 --> A
  D4 --> A
  D5 --> A
  D6 --> A
  D7 --> A
  A --> Q[Compliance Officer Queue]
  A --> F{Severe?}
  F -- yes --> FR[Freeze actor via [[arch-compliance]]]
```

## Detector model

Each detector is a **pure projection function** over the event stream with a configured window and threshold:

```
detector {
  id, version
  subscribe: [event types]
  window:     duration
  evaluate(events_in_window, context) -> [Alert]
}
```

Outputs are `Alert` records; alerts go to compliance officers for review. Severity tier is detector-policy. Detectors are **versioned** like FSMs and other reference artifacts — same alert from the same input under the same version reproduces deterministically.

## Output

```
SurveillanceAlertRaised {
  alert_id,
  detector_id, detector_version,
  severity,
  subject_actor,
  subject_events: [event_id],
  window: { start, end },
  rationale,
  evidence_snapshot,
  recommended_action
}
```

Persisted on the [[arch-event-sourcing|event log]]'s surveillance stream. Alerts route to:

- Compliance officer review queue (typically with SLA, e.g. "review within 1 business day").
- If `severity=CRITICAL` → auto-freeze actor via `ActorFrozenByCompliance` event ([[arch-compliance]]); subsequent pre-trade gates block.

## Integration with Compliance

Surveillance **only raises alerts**; it does not block in real time (that would require sub-second pattern detection for cross-event patterns, which is incompatible with reliable detection). Severe-alert auto-freeze is the link back to compliance's synchronous gate.

## Determinism / replay

Detectors are pure over the event stream + clock + reference data. [[arch-time-replay-server|Replay]] re-emits identical alerts. Useful for: testing new detectors against historical data, regulatory subpoena response, periodic re-runs at new detector versions.

## Tuning

Detectors have **threshold parameters** tuned per firm / desk / instrument tier. Tuning is reference data, versioned, with change events. Periodic review by Compliance to balance false-positive rate against detection sensitivity.

## See also

- [[arch-compliance]] · [[arch-event-sourcing]] · [[arch-time-replay-server]] · [[arch-position-service]]
- [[arch-quote-server]] · [[arch-jmx-introspection]] · [[arch-validator]]
- [[regulatory-base]] · [[trace]] · [[finra]] · [[msrb-rtrs]]
