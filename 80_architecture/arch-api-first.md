---
type: architecture
layer: surface
status: draft
tags: [architecture/surface]
---

# API-First Design

The single source of truth for every operation in the EMS is the **API**. UIs, FIX gateways, and automation rules are all clients of the same surface. There is no operation that the UI can perform that the API cannot.

## Principles

- **One operation set.** Every action a trader, sales person, or automation can take is an API call. Internal layers expose no privileged side door.
- **Batch by default.** Every operation takes a list of items. Submitting one order is `submit_orders([order])` — a batch of size one. This eliminates the "do we need a bulk endpoint?" question and forces all error handling, idempotency, and partial-success semantics to be designed for N from day one.
- **Two interaction models, one surface.** Request/response **and** publish/subscribe both ride the same schema and identity. Cite [Bloomberg BLPAPI](https://bloomberg.github.io/blpapi-docs/) as the design reference — `Session`, `Service`, `Request`, `Subscription`, `Event`.
- **Identity is required.** No anonymous calls. Authentication, [[arch-firm-desk-user|firm/desk/user hierarchy]], and [[arch-tag-permissions|tag permissions]] are evaluated on every operation.
- **Auditable.** Every API call becomes one or more events in the [[arch-event-sourcing|event log]]. No call is fire-and-forget.

## Batch envelope (canonical shape)

```
Request {
  request_id:     UUID         # client-assigned, idempotency key
  client_seq:     uint64       # monotonic per session — see [[arch-sequence-recovery]]
  identity:       Identity     # firm, desk, user, auth token
  operation:      OperationType
  items:          [Item]       # 1..N
  options:        { partial_ok: bool, on_error: STOP|CONTINUE }
}

Response {
  request_id:     UUID
  results:        [ItemResult]  # same order/cardinality as request.items
  summary:        { ok: N, rejected: M, deferred: K }
}

ItemResult {
  status:    ACCEPTED | REJECTED | DEFERRED
  ref_id:    EMS-side ID (e.g. order_id, route_id)
  error:     { code: "EMS-2014", message: "...", admin: "..." }  // see [[arch-validator]]
}
```

Single-item callers ignore `items[0]` ceremony and the response collapses cleanly; batch callers get partial-success guarantees for free.

## Operation categories

| Category | Examples | Backed by |
|---|---|---|
| Order intake | `stage_orders`, `amend_orders`, `cancel_orders` | [[arch-order-staged]] |
| Routing | `route_orders`, `cancel_routes`, `replace_routes` | [[arch-router-layer]] |
| Automation | `bind_rule`, `unbind_rule`, `list_rule_firings` | [[arch-automation-layer]] |
| Quote sub | `subscribe`, `unsubscribe` | [[arch-quote-server]] |
| Reference data | `resolve_figi`, `bulk_resolve` | [[arch-symbology-figi]] |
| Admin | `inject_event`, `dump_state` (privileged) | [[arch-jmx-introspection]] (planned) |

## Why this matters

- **FIX becomes a subset.** [[arch-fix-api-bridge]] maps inbound FIX messages onto a closed subset of operations — same validator, same audit, same state machine. No "FIX-only" code paths.
- **Automation is a peer.** [[arch-automation-layer]] rules call the same `route_orders` operation a human does. The router cannot tell them apart at the schema level.
- **Replay parity.** Any historical session can be replayed through the API surface and produce byte-identical SBE events — see [[arch-event-sourcing]].

## References

- BLPAPI Core Developer Guide — `Session`, `Service`, `Element`, schema-typed messages, identity propagation.
- OpenFIGI [OpenAPI spec](https://www.openfigi.com/api/openapi-spec) — model for the batch resolution endpoint pattern used in [[arch-symbology-figi]].

## See also

- [[arch-fix-api-bridge]]
- [[arch-sequence-recovery]]
- [[arch-sbe-aeron-transport]]
- [[arch-validator]]
