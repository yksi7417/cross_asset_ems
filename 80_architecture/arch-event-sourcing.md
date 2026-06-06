---
type: architecture
layer: persistence
status: draft
tags: [architecture/persistence]
---

# Event Sourcing — The Auditable Spine

All system state is derived from an **append-only sequence of events**. There is no mutable "current state" table that is the source of truth; the log is. Every other store is a projection.

## Why

- **Audit** — compliance and post-trade reviews can reconstruct any moment.
- **Replay** — historical sessions re-execute through the same code paths to verify behaviour after fixes or schema changes. See [[arch-time-replay-server]].
- **Recovery** — components rebuild state from the log after restart. No separate snapshot/recovery code path.
- **Determinism** — given the same input event sequence and the same code version, every component reaches byte-identical state. Tested by `golden replay` on every release.

## Event envelope

```
Event {
  event_id      UUID
  global_seq    uint64           // append order, log-wide
  stream_id     string           // partition key (order_id, route_id, session_id, etc.)
  stream_seq    uint64           // monotonic within stream_id
  template_id   uint16           // SBE template, see [[arch-sbe-aeron-transport]]
  payload       bytes            // SBE-encoded
  occurred_at   timestamp        // wall or simulated, see [[arch-time-replay-server]]
  recorded_at   timestamp        // when the event store persisted it
  caused_by     UUID             // upstream event_id, optional
  source        FIX|API|AUTO|REPLAY
  actor         identity         // see [[arch-firm-desk-user]]
}
```

## Streams

Each domain entity has its own stream:

| Stream | Examples of events |
|---|---|
| `order.{order_id}` | `OrderStaged`, `OrderAmended`, `OrderRouted`, `OrderCancelled`, `OrderFilled` |
| `route.{route_id}` | `RouteSent`, `RouteAcked`, `RouteWorking`, `RouteFilled`, `RouteRejected` |
| `session.{session_id}` | `SessionLogon`, `SessionLogout`, `SessionGapDetected` |
| `rule.{rule_id}` | `RuleBound`, `RuleFired`, `RuleSuppressed` |
| `quote.{topic}` | (multicast tail — sampled into log) |
| `admin` | `EventInjected`, `SequenceReset`, `ConfigChanged` |

## Projections

Read-side stores are rebuilt by consuming the log:

- The order blotter view.
- The router state table.
- The quote subscriber registry.
- Per-firm/desk/user dashboards.

Projections are **idempotent** in event id. Rebuilds from scratch are routine.

## Guarantees

1. **Append-only.** No deletes, no edits. Corrections are new events (`OrderCorrected`, `RouteAdjusted`).
2. **Total order within stream.** `stream_seq` is contiguous; gaps indicate corruption.
3. **Global order is partial.** `global_seq` is a tiebreaker, not a causal order claim.
4. **Causality is explicit.** `caused_by` links downstream events to their trigger.

## Replay

A replay run takes:
- A log slice (e.g. `2025-11-03 09:30:00 → 16:00:00 UTC`).
- A target code version.
- A clock source — usually the [[arch-time-replay-server|time/replay server]] in `simulated` mode.

It produces:
- Re-derived state.
- A new event stream (kept separate) that is `diff`ed against the original. Any divergence is a bug — either in the code change or in a non-deterministic dependency that needs to be eliminated.

## Determinism rules

Code that participates in event derivation **must not**:
- Read wall-clock time directly. Use [[arch-time-replay-server|the clock interface]].
- Generate UUIDs from time. Use a deterministic seed where required.
- Depend on iteration order of hash maps where outputs are externalized.
- Make outbound network calls outside the [[arch-venue-connectivity|venue adapter boundary]].

## See also

- [[arch-api-first]]
- [[arch-sbe-aeron-transport]]
- [[arch-time-replay-server]]
- [[arch-sequence-recovery]]
- [[arch-validator]]
