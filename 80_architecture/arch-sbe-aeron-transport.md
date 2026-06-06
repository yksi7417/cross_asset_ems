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
  block_length  uint16
  template_id   uint16   // FIX-like msg type, e.g. 0xD001 = StageOrders
  schema_id     uint16
  version       uint16
}
SessionHeader {
  session_id    uint64   // edge-assigned per logon
  seq_num       uint64   // see [[arch-sequence-recovery]]
  send_time     uint64   // see [[arch-time-replay-server]]
  source        enum     // FIX | API | AUTOMATION | REPLAY
}
```

`source` lets every downstream component classify origin without duplicating logic (cf. the [[arch-fix-api-bridge]] mixed-client rule).

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

## See also

- [[arch-api-first]]
- [[arch-fix-api-bridge]]
- [[arch-sequence-recovery]]
- [[arch-event-sourcing]]
- [[arch-quote-server]]
- [[arch-venue-connectivity]]
