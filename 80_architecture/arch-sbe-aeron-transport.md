---
type: architecture
layer: transport
status: draft
tags: [architecture/transport]
---

# SBE + Aeron Transport

The internal protocol between components is **Simple Binary Encoding (SBE)** messages over **Aeron** channels. FIX and the public API are encoded **at the edge**; everything past the edge is SBE.

## Why SBE

- Fixed-layout, zero-allocation decoding. Hot-path components do not parse tag-value text.
- Schema-first: the `.xml` SBE schema is the contract; codecs are generated.
- Versionable: forward/backward compatibility through `sinceVersion` semantics.
- Maps cleanly to the FIX semantic model — same message types, same field meanings, different bytes on the wire.

## Why Aeron

- UDP unicast for component-to-component request/response.
- UDP multicast for fan-out (e.g. [[arch-quote-server]] distribution, event-log tail).
- Reliable delivery (NAK-based recovery) without TCP head-of-line blocking.
- Per-stream sequence numbers — these complement the application-level [[arch-sequence-recovery|session sequence]].
- **Aeron Cluster** (Raft-based replicated state machine) + **Aeron Archive** (recorded streams with position-precise replay) directly enable [[arch-resilience-24x7|24/7 hot-warm continuity]]. The transport itself supplies the primitives — clustered consensus, snapshots, archive replay — that we lean on rather than reinventing.

## Channel layout (illustrative)

| Channel | Direction | Schema(s) | Notes |
|---|---|---|---|
| `aeron:udp?endpoint=staging.in:40001`   | edge → order layer  | `StageOrders`, `AmendOrders` | One stream per source desk |
| `aeron:udp?endpoint=router.in:40002`    | order → router      | `RouteOrders`, `CancelRoutes` | Includes automation-originated traffic |
| `aeron:udp?endpoint=venue-out:40010+`   | router → venue adapter | Per-venue dialect      | One adapter per venue, see [[arch-venue-connectivity]] |
| `aeron:udp?endpoint=224.0.1.10:50000`   | multicast quote bus | `QuoteSnapshot`, `QuoteIncrement` | See [[arch-quote-server]] |
| `aeron:udp?endpoint=224.0.1.20:50010`   | multicast event tail | `Event` (sealed union)  | Subscribers replay-capable, see [[arch-event-sourcing]] |

## Message envelope

Every SBE message carries a small fixed-layout header:

```
MessageHeader {
  block_length      uint16
  template_id       uint16   // FIX-like msg type, e.g. 0xD001 = StageOrders
  schema_id         uint16
  version           uint16
}
SessionHeader {
  session_id        uint64   // edge-assigned per logon; see [[arch-sequence-recovery]]
  seq_num           uint64
  send_time         uint64   // see [[arch-time-replay-server]] — clock-injected, not wall
  source            enum     // FIX | API | AUTOMATION | REPLAY | CLUSTER

  // Observability — see [[arch-observability]]
  trace_id          bytes[16] // W3C trace context
  parent_span_id    bytes[8]
  trace_flags       uint8     // sampled bit etc.

  // Identity chaining — see [[arch-identity-chaining]]
  initial_order_id  bytes[16] // UUID; zero if not order-related
  initial_route_id  bytes[16] // UUID; zero if not route-related
}
```

`source` lets every downstream component classify origin without duplicating logic (cf. the [[arch-fix-api-bridge]] mixed-client rule).

The `trace_id` + `parent_span_id` + `trace_flags` triple is the W3C trace context, propagated on every event so the [[arch-observability|observability stack]] can correlate across components without per-call enrichment. The `initial_order_id` + `initial_route_id` give every consumer a one-lookup join back to the entire chain history regardless of cancel/replace ClOrdID mutations — see [[arch-identity-chaining]] for the discipline.

Total envelope overhead: ~73 bytes (24-byte session/seq/time + 1-byte source + 25-byte trace + 32-byte chain IDs). Acceptable for the audit, replay, and correlation value it buys.

## Schema evolution rules

- **Never reorder fields.** Append only.
- **Never narrow a type.** Widening (`uint32` → `uint64`) is forward-compatible.
- **New fields must have a `sinceVersion`.** Old readers skip cleanly.
- **Deprecated fields stay in the schema.** They are marked `deprecated=true` and read as nullable by consumers.
- Schema changes ship with **golden replays** verified against [[arch-event-sourcing]] — same input bytes must produce the same downstream state.

## Why not raw FIX internally?

- Tag-value parsing is a measurable hot-path cost in mid/high message rates.
- Type safety: FIX tags are stringly-typed; SBE generated codecs are not.
- Backwards-compatibility ergonomics: FIX custom-tag namespaces are politically expensive; SBE schema versions are mechanical.

## What we still borrow from FIX

- Message-type naming and semantics (template_id maps 1:1 to a FIX msg type where possible).
- Reject/business-reject patterns at the [[arch-validator|validator]] boundary.
- The session model — see [[arch-sequence-recovery]].

## Aeron Cluster — replicated state machine

State-machine components ([[arch-order-staged|Order Layer]], [[arch-router-layer|Router]], [[arch-allocation-service|Allocation Service]], the [[entry-point-aaa|AAA service]]) run as **Aeron Cluster** nodes:

- **Raft consensus** across N nodes (typically 3 or 5). Leader serves writes; followers replicate.
- **Quorum commits** — events committed only when a majority acks. No split-brain.
- **Deterministic SBE-driven state machine** — all nodes apply the same events in the same order ([[arch-fix-fsm-design|the shared FSM]] ensures determinism), reaching byte-identical state.
- **Snapshots** — each node periodically snapshots its state to local storage; on restart, load latest snapshot + replay tail from Archive.
- **Sub-second leader election** on failure; clients reconnect, [[arch-sequence-recovery|sequence recovery]] handles in-flight retransmits.

See [[arch-resilience-24x7]] for the full hot-warm-cold deployment model, rolling-restart pattern, and cross-region active-passive.

## Aeron Archive — record and replay

The Aeron Archive captures all live streams to disk with **position-precise** indexing:

- Every event has a `(stream_id, position)` coordinate.
- Replay from any position is supported — byte-precise.
- Used by: warm-standby catchup, cold-start recovery, [[arch-time-replay-server|golden replay]] for verification, [[arch-event-sourcing|event log]] cold-archive tiering, [[arch-best-execution|best-ex audit reconstruction]].

The Archive is the transport-layer companion to the application event log: faster (no application decode), less semantically rich. Both serve replay; both must agree byte-for-byte under [[arch-time-replay-server|simulated clock]].

Archive segments rotate by time/size; older segments tier off to S3 / Azure Blob per [[arch-jurisdictional-compliance|jurisdictional retention requirements]] (5y MiFID II, 7y SEC, etc.).

## See also

- [[arch-api-first]] · [[arch-fix-api-bridge]] · [[arch-sequence-recovery]] · [[arch-event-sourcing]]
- [[arch-resilience-24x7]] (Aeron Cluster + Archive as the continuity spine)
- [[arch-observability]] (trace context in the envelope) · [[arch-identity-chaining]] (chain IDs in the envelope)
- [[arch-time-replay-server]] · [[arch-fix-fsm-design]]
- [[arch-quote-server]] · [[arch-venue-connectivity]]
