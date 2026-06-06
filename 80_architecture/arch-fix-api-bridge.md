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

The order/route lifecycle states this matrix references — `Pending Replace` / `Replaced` / `Pending Cancel` / `Canceled` and the `OrderCancelReject` reject paths — are defined in [[arch-order-route-lifecycle]].

| FIX msg | API op | Notes |
|---|---|---|
| `D` NewOrderSingle | `stage_orders` (batch=1) | Optional auto-route via [[auto-route]] |
| `E` NewOrderList | `stage_orders` (batch=N) | Batch by default — see [[arch-api-first]] |
| `AB` NewOrderMultileg | `stage_orders` with multileg envelope | See [[arch-multileg]] |
| `s` NewOrderCross | `stage_orders` with cross envelope | |
| `G` OrderCancelReplaceRequest | `amend_orders` | Tag 11 (ClOrdID) → new ID, 41 (OrigClOrdID) → prior ID. Generates outbound `35=8 ExecType=E Pending Replace`, then `35=8 ExecType=5 Replaced` on success or `35=9 OrderCancelReject` on failure. See [[amend-order]] and [[arch-order-route-lifecycle]] § "Cancel/replace semantics". |
| `AC` MultilegOrderCancelReplace | `amend_orders` (multileg) | Same Pending Replace lifecycle, but legs amended atomically. |
| `F` OrderCancelRequest | `cancel_orders` | Tag 41 (OrigClOrdID) → current ClOrdID. Generates `35=8 ExecType=6 Pending Cancel`, then `35=8 ExecType=4 Canceled` on success or `35=9 OrderCancelReject` on failure. |
| `H` OrderStatusRequest | `query_order_status` | Returns current state via `35=8`. |
| `8` ExecutionReport | (outbound) emitted on every state transition | Generated from event log — see [[arch-event-sourcing]]. Carries `OrdStatus` (39) + `ExecType` (150) per [[arch-order-route-lifecycle]] mapping. |
| `9` OrderCancelReject | (outbound) emitted on rejected `F` or `G` | Carries `CxlRejReason` (102). **Does not terminate the original order** — it stays in its prior state. |
| `j` BusinessMessageReject | (outbound) translated from pre-state validator reject | See [[arch-validator]]. |

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
