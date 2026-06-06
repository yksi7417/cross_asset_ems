---
type: architecture
layer: surface
status: draft
tags: [architecture/surface, architecture/fix]
---

# FIX-API Bridge

FIX is **not** a parallel surface. It is a **subset** of [[arch-api-first|the API]], encoded in the FIX wire format. There is no FIX-only state and no FIX-only validator.

## Mapping principle

Every supported inbound FIX message decodes to **one API operation**. There is a single bridge layer:

```
FIX wire → FIX parser → API operation (SBE) → core layers
```

The bridge owns:
- Tag-by-tag translation tables (one per supported message type).
- Session-level concerns (logon, heartbeat, sequence reset) — locally implemented to match FIX semantics without a third-party engine. See [[arch-sequence-recovery]].
- Asymmetric capability: some API operations have no FIX counterpart (e.g. fine-grained rule binding). Those are simply unavailable to pure FIX clients.

## Operation coverage matrix (illustrative)

| FIX msg | API op | Notes |
|---|---|---|
| `D` NewOrderSingle | `stage_orders` (batch=1) | Optional auto-route via `[[auto-route]]` |
| `G` OrderCancelReplace | `amend_orders` | Tag 11 → request_id, 41 → orig_request_id |
| `F` OrderCancelRequest | `cancel_orders` | |
| `E` NewOrderList | `stage_orders` (batch=N) | Batch by default — see [[arch-api-first]] |
| `AB` NewOrderMultileg | `stage_orders` with multileg envelope | See planned `arch-multileg` |
| `s` NewOrderCross | `stage_orders` with cross envelope | |
| `j` BusinessMessageReject | (outbound) translated from validator reject | See [[arch-validator]] |
| `8` ExecutionReport | (outbound) emitted from route/fill events | Generated from event log |

## The mixed-client (FIX + API) rule

A single client may operate both a FIX session and an API session simultaneously. To prevent state divergence:

| Source of operation | Allowed actions |
|---|---|
| **FIX session** | Staging only (`stage_orders`). Cannot amend, cannot cancel non-FIX-staged orders, cannot bind rules. |
| **API session (same client)** | Full manipulation of orders staged via either FIX or API. Can route, amend, cancel, allocate. |

All resulting state changes — wherever initiated — are **mirrored back to the FIX client** as `ExecutionReport` (`8`), `OrderCancelReject` (`9`), or `BusinessMessageReject` (`j`). The FIX client sees a complete order lifecycle even when humans on the API side are driving it.

> This degrades the API to a "bare minimum" for FIX-paired clients on the staging side, exactly as specified: FIX clients can only stage; everything else is API-driven, with FIX-shaped echoes flowing outbound.

## Why this works

- **Single validator.** Any reject at the API layer becomes a `j` to the FIX client with a code-mapped reason; reverse is true on the way in. See [[arch-validator]].
- **Single audit log.** [[arch-event-sourcing|Event sourcing]] does not branch on origin — `source: FIX | API` is metadata on the event, not a state fork.
- **Schema evolution stays in one place.** When the order model gains a field, the SBE schema gains it, the bridge gains a tag mapping, and the FIX dictionary is extended. No parallel implementation.

## Internal encoding

The bridge produces **SBE-encoded** API operations on its outbound side. From the core's perspective, FIX-origin and API-origin operations are byte-identical SBE messages. See [[arch-sbe-aeron-transport]].

## Anti-patterns to avoid

- A `FIXOrder` table separate from the canonical order table.
- A validator that runs different rules for FIX vs API.
- Sequence-number state living inside the FIX engine instead of in the [[arch-sequence-recovery|session layer]].

## See also

- [[arch-api-first]]
- [[arch-sequence-recovery]]
- [[arch-sbe-aeron-transport]]
- [[arch-validator]]
- [[arch-order-staged]]
