---
type: architecture
layer: surface
status: draft
tags: [architecture/surface]
---

# API-First Design

The single source of truth for every operation in the EMS is the **API**. UIs, native client
programs, FIX gateways, and automation rules are all clients of the same surface. There is no
operation the UI can perform that the API cannot.

## API-first is not REST-first

"API-first" here means the **programming interface** — a typed `Session / Service / Request /
Subscription / Event` model in the spirit of [Bloomberg BLPAPI](https://bloomberg.github.io/blpapi-docs/)
— is the contract. It is **not** a commitment to REST, and not a commitment to a single wire
protocol. REST/HTTP is one *edge binding* among several (convenient for browsers), not the surface
itself.

The surface carries **two interaction models on one schema and one identity**:

- **Request/response** — `stage_orders`, `route_orders`, `cancel_routes`, … each a typed batch call.
- **Publish/subscribe** — `subscribe` to an order, a route, a desk's blotter, a quote stream, the
  event tail; receive typed `Event`s as state changes happen.

Both ride the **same session channel** (below), so a client never juggles "the REST API" and "the
streaming API" as two systems with two auth models and two recovery stories. It is one
authenticated, sequenced, resumable stream.

## Principles

- **One operation set.** Every action a trader, salesperson, or automation can take is an API
  operation. Internal layers expose no privileged side door.
- **Transport is Aeron; surfaces are edge bindings.** Past the edge, everything is SBE over Aeron
  (see [[arch-sbe-aeron-transport]]). FIX, REST/WebSocket, and the native SDK all encode **to** the
  same SBE operations at the edge and decode the same SBE events on the way back.
- **Batch by default.** Every operation takes a list. Submitting one order is `stage_orders([order])`
  — a batch of size one. This forces error handling, idempotency, and partial-success semantics to
  be designed for N from day one.
- **The three A's, on every operation.** **Authentication** (who), **Authorization**
  ([[arch-firm-desk-user|firm/desk/user]] + [[arch-tag-permissions|tag permissions]]), and
  **Accounting** (every call becomes one or more events in the [[arch-event-sourcing|event log]] —
  no call is fire-and-forget). The [[entry-point-aaa|AAA service]] is the front door; nothing
  reaches the order layer without passing it.
- **Identity is required.** No anonymous calls.

## Client surfaces — one API, three bindings

A user reaches the same API through whichever surface fits them, and gets a **consistent view of the
system** regardless of which they pick (and they may use more than one at once — see the
[[arch-fix-api-bridge#The mixed-client (FIX + API) rule|mixed-client rule]]).

| Surface | Binding | Transport to edge | Who uses it |
|---|---|---|---|
| **UI** | REST + WebSocket gateway | HTTP/WS → SBE | Traders, sales, ops on a browser blotter |
| **Native program** | SDK (Python / C++ / Java) | SBE over Aeron (direct) | Quant/algo desks, integrations driving the system programmatically |
| **FIX** | [[arch-fix-api-bridge|FIX bridge]] | FIX wire → SBE | Buy-side OMSs and counterparties speaking FIX |

The REST/WS gateway and the FIX bridge are **adapters** that terminate their wire protocol and speak
the same SBE session channel inward. The native SDK speaks it directly. None of them is privileged;
none has a private state path. A `stage_orders` from a Python script, a browser, and a `35=D` over
FIX converge to byte-identical SBE operations and produce the same events — see
[[#Consistency across surfaces]].

## The session channel — resumable, sequenced, AAA streaming construct

The streaming construct is a **bidirectional, resumable, sequence-numbered session channel**, modelled
on FIX session semantics but carried over Aeron instead of a third-party FIX engine. The mechanics —
logon, per-direction `session_seq`, heartbeats/`TEST_REQUEST`, gap detection, `RESEND`/`RESET`,
cold-restart from snapshot — are owned by the session layer and specified in
[[arch-sequence-recovery]]; the envelope that carries `session_id` / `seq_num` / `source` / identity
and trace chain IDs is specified in [[arch-sbe-aeron-transport#Message envelope|the SBE envelope]].
This section describes only how the **API surface** sits on top of them; it does **not** restate the
recovery rules (one source of truth — see [[arch-sequence-recovery#No physical FIX engine]]).

### The pipeline is sequenced hop-by-hop

A request does not hit one monolith. It threads a pipeline of **independently deployable,
independently restartable** components — and that is exactly why the sequence number is load-bearing:

```
   UI (REST/WS)        Native SDK (Py/C++/Java)         FIX client
        │                        │                          │
        ▼                        ▼                          ▼
  ┌──────────────────────────────────────────────────────────────┐
  │  Edge binding:  REST/WS gateway · Aeron SDK · FIX bridge      │  encode → SBE
  └──────────────────────────────────────────────────────────────┘
        │   authenticated, sequenced, resumable session channel (Aeron)
        ▼
   ┌────────┐  seq   ┌────────┐  seq   ┌────────┐  seq   ┌─────────┐
   │  AAA   │═══════▶│ Order  │═══════▶│ Router │═══════▶│  Venue  │
   │ authn  │  cmds  │ layer  │  cmds  │ layer  │  cmds  │ adapter │
   │ authz  │◀═══════│        │◀═══════│        │◀═══════│         │
   │ acct   │ events │        │ events │        │ events │         │
   └────────┘        └────────┘        └────────┘        └─────────┘
```

- **Every hop is its own authenticated, sequenced, resumable Aeron link.** `client → aaa`,
  `aaa → order`, `order → router`, `router → venue` each carry the
  [[arch-sbe-aeron-transport#Message envelope|SessionHeader]] with a per-direction `seq_num`.
- **AAA is the entry point, not a library.** The client authenticates to AAA once; AAA stamps the
  established [[arch-firm-desk-user|identity]] into the envelope and forwards downstream. Order and
  router layers trust the stamped identity (and re-check [[arch-tag-permissions|tag scope]] where the
  operation demands it) rather than re-authenticating.
- **Each component dedups its inbound stream by `seq_num`.** Because the components are distributed
  and any one can restart, retransmit, or replay (Aeron NAK recovery, cluster leader failover,
  Archive replay), a hop can legitimately see the same message twice. The receiver's
  `next_expected_seq` makes the pipeline **exactly-once at the application layer**: `seq < expected`
  → duplicate, drop (idempotency confirmed by `request_id`); `seq > expected` → gap, `RESEND`. This
  is the FIX guarantee, applied to *every internal hop*, not just the client edge.

### Resume

A dropped connection — browser tab closed, SDK process bounced, FIX session disconnected, or an
Aeron cluster leader election — is recovered the same way on every surface:

1. The client re-logs on declaring its `next_expected_seq` ([[arch-sequence-recovery#Session lifecycle]]).
2. The session layer replays the outbound buffer (or, post-crash, rebuilds from the
   [[arch-event-sourcing|event log]] + `SessionSnapshot`) from that point.
3. Pub/sub subscriptions resume from the subscriber's last delivered `seq_num` — no missed fill, no
   double fill. A subscription is a **cursor over the sequenced event stream**, not a live tap, so
   intermediate states (`WORKING → PARTIALLY_FILLED → FILLED`) are never collapsed across a gap.

Heartbeats keep the channel live and detect silent death; their interval and the `TEST_REQUEST` /
two-missed-beats → `STALE` rules live in [[arch-sequence-recovery#Heartbeats]].

## Batch envelope (canonical shape)

```
Request {
  request_id:     UUID         # client-assigned, idempotency key
  session_seq:    uint64       # monotonic per session/direction — see [[arch-sequence-recovery]]
  identity:       Identity     # firm, desk, user, auth token (stamped by AAA)
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

A `Subscription` is the pub/sub dual of a `Request`: the client names a topic (order, route, desk,
quote stream, event tail) and a starting `seq_num`; the server streams typed `Event`s with monotonic
sequence numbers it can resume from. Single-item callers ignore `items[0]` ceremony; batch callers
get partial-success guarantees for free.

## Operation categories

| Category | Examples | Backed by |
|---|---|---|
| Order intake | `stage_orders`, `amend_orders`, `cancel_orders` | [[arch-order-staged]] |
| Routing | `route_orders`, `cancel_routes`, `replace_routes` | [[arch-router-layer]] |
| Automation | `bind_rule`, `unbind_rule`, `list_rule_firings` | [[arch-automation-layer]] |
| Subscriptions | `subscribe`, `unsubscribe` (orders, routes, blotter, quotes, event tail) | this doc + [[arch-quote-server]] |
| Reference data | `resolve_figi`, `bulk_resolve` | [[arch-symbology-figi]] |
| Admin | `inject_event`, `dump_state` (privileged) | [[arch-jmx-introspection]] (planned) |

## Consistency across surfaces

"A consistent view from UI, native program, and FIX" is a **testable invariant**, not a slogan:

> The same logical operation, submitted through any surface, produces the **same SBE operation**, the
> **same events** in the [[arch-event-sourcing|log]], and therefore the **same projection** — which is
> exactly what every subscriber (on any surface) sees next.

This holds because:

- **One encoding.** All three bindings converge to the same SBE operation before the order layer (see
  [[arch-fix-api-bridge#Internal encoding]]). `source: FIX | API | AUTOMATION` is metadata on the
  event, not a state fork.
- **One validator, one audit log.** Any reject is the same reject regardless of origin; the event log
  does not branch on surface ([[arch-validator]], [[arch-event-sourcing]]).
- **Mirrored echoes.** A state change initiated on one surface is delivered to a client watching on
  another — the [[arch-fix-api-bridge#The mixed-client (FIX + API) rule|mixed-client rule]] is the
  concrete contract: a FIX client sees the full lifecycle as `35=8` even when a human drives the order
  from the UI.

A surface-parity test asserts this directly: drive the identical operation via FIX, the native SDK,
and REST/WS, and assert byte-identical events and identical resulting projections.

## Why this matters

- **FIX becomes a subset.** [[arch-fix-api-bridge]] maps inbound FIX onto a closed subset of
  operations — same validator, same audit, same state machine. No "FIX-only" code paths.
- **Automation is a peer.** [[arch-automation-layer]] rules call the same `route_orders` a human does.
  The router cannot tell them apart at the schema level.
- **Replay parity.** Any historical session replays through the API surface and produces
  byte-identical SBE events — see [[arch-event-sourcing]] and [[arch-time-replay-server]].

## References

- BLPAPI Core Developer Guide — `Session`, `Service`, `Subscription`, `Event`, schema-typed messages,
  identity propagation.
- OpenFIGI [OpenAPI spec](https://www.openfigi.com/api/openapi-spec) — model for the batch resolution
  endpoint pattern used in [[arch-symbology-figi]].

## See also

- [[arch-sequence-recovery]] (the session channel mechanics: seq, heartbeat, gap, recovery)
- [[arch-sbe-aeron-transport]] (SBE envelope + Aeron transport + cluster/archive)
- [[arch-fix-api-bridge]] (FIX as a subset of this surface; mixed-client rule)
- [[entry-point-aaa]] (the AAA front door) · [[arch-firm-desk-user]] · [[arch-tag-permissions]]
- [[arch-event-sourcing]] · [[arch-validator]] · [[arch-order-staged]] · [[arch-router-layer]]
