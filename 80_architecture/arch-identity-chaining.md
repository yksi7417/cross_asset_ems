---
type: architecture
layer: order_model
status: draft
tags: [architecture/order_model, architecture/observability]
---

# Identity Chaining — Order, Route, Fill, Trace

A **disciplined naming convention** that keeps every event in an order's lifetime — across cancel/replace ClOrdID transitions, post-fill busts/corrects, multi-leg expansion, aggregation, allocation, and downstream STP — chainable by a small set of stable identifiers. Combined with the W3C `trace_id` from [[arch-observability]], this gives one end-to-end query for "everything that happened for this order, including the fill that arrived two hours later."

## The problem

FIX cancel/replace (`35=G`) **mints a new `ClOrdID`** per the [[arch-order-route-lifecycle]] standard. After a few replaces, the same logical order has half a dozen ClOrdIDs in audit:

```
T+0    NewOrderSingle    ClOrdID = CL-001
T+5s   Replace           NewClOrdID = CL-002, OrigClOrdID = CL-001
T+11s  Replace           NewClOrdID = CL-003, OrigClOrdID = CL-002
T+30s  Fill              ExecRpt references ClOrdID = CL-003
T+45s  Replace           NewClOrdID = CL-004, OrigClOrdID = CL-003
T+90s  TradeBust         ExecRefID references the T+30s fill (ClOrdID = CL-003)
```

Reconstructing the chain by walking `OrigClOrdID` backwards is correct but **expensive** in query — every reconstruction does a recursive lookup. And in production triage you can't afford "scan the audit log six times" per stuck order.

The fix is a small set of **stable chain identifiers stamped on every event**.

## Identifier vocabulary

```
order envelope and every event {
  // Stable across the entire chain — never change
  initial_order_id     UUID     # EMS-internal, minted at the very first OrderAccepted
  initial_cl_ord_id    string   # the first FIX ClOrdID we ever saw (or assigned)
  chain_id             UUID     # synonym/alias of initial_order_id; used in indices and logs

  // Mutate on every replace
  cl_ord_id            string   # the current FIX ClOrdID
  prev_cl_ord_id       string?  # the immediately-prior ClOrdID (= OrigClOrdID on a 35=G)

  // Stable per replace generation
  order_version        int      # increments on every replace; 1 at NewOrderSingle
}

route envelope and every event {
  initial_route_id     UUID     # EMS-internal, minted at first RouteSent
  initial_cl_ord_id    string   # first ClOrdID we sent on this route
  route_chain_id       UUID     # synonym of initial_route_id

  cl_ord_id            string   # current ClOrdID at the venue
  prev_cl_ord_id       string?
  route_version        int
}

fill envelope (ExecutionReport with ExecType=F) {
  exec_id              string   # venue's tag 17 ExecID
  cl_ord_id_at_fill    string   # ClOrdID active at the moment of fill (tag 11 on the ExecRpt)
  initial_cl_ord_id    string   # the chain's original ClOrdID
  route_id             UUID
  initial_route_id     UUID
  order_id             UUID
  initial_order_id     UUID
  trace_id             bytes[16]  # see [[arch-observability]]
}

bust / correct envelope (ExecType=H / G) {
  exec_ref_id          string   # references the busted/corrected ExecID
  initial_route_id     UUID     # same as the original fill's
  initial_order_id     UUID     # same as the original fill's
  ...
}
```

The discipline: **every event on the bus carries `initial_order_id` and `initial_route_id`** (UUIDs that never change). One key, one lookup, every event for that chain.

## Mapping to FIX wire

FIX has three relevant tags:

| FIX tag | Field | EMS-internal field |
|---|---|---|
| `11` | `ClOrdID` | `cl_ord_id` (current) |
| `41` | `OrigClOrdID` | `prev_cl_ord_id` |
| `526` | `SecondaryClOrdID` | optional; used by some venues for stable chain id |
| `583` | `ClOrdLinkID` | the FIX-standard chain ID for linking related orders within a single execution — closest to our `chain_id` |

For chain identity on the FIX wire, [[arch-fix-api-bridge|the bridge]] uses **`SecondaryClOrdID (526)`** to carry our `initial_cl_ord_id` on every outbound message (where the venue tolerates it). For venues that don't, the bridge keeps a local mapping from `cl_ord_id → initial_cl_ord_id` keyed on the active session — replace-history is always reconstructable.

Inbound from upstream OMSs: if the upstream sends `526` or `583`, we adopt it as `initial_cl_ord_id` / `chain_id`. If not, we mint one at first stage.

## Lifetime guarantees

| Event | initial_order_id | initial_cl_ord_id | initial_route_id |
|---|---|---|---|
| `OrderAccepted` (first stage) | minted | minted/adopted | n/a |
| `OrderReplaceRequested` / `OrderReplaced` | unchanged | unchanged | n/a |
| `OrderCancelRequested` / `OrderCanceled` | unchanged | unchanged | n/a |
| `RouteSent` (first time) | unchanged | unchanged | minted |
| Subsequent `RouteSent` (a *new* route for the same order) | unchanged | unchanged | **new** route's initial_route_id |
| `RouteReplaced` | unchanged | unchanged | unchanged |
| `RoutePartiallyFilled` / `RouteFilled` | unchanged | unchanged | unchanged |
| `TradeBust` / `TradeCorrect` | unchanged | unchanged | unchanged |
| `AllocationApplied` | unchanged | unchanged | unchanged (per-fill) |
| `RegReportSubmitted` | unchanged | unchanged | unchanged |
| `MultilegLegSent` (child leg routes) | unchanged | unchanged | leg's initial_route_id (the parent's is also recorded as `parent_initial_route_id`) |

The invariant: **once stamped, never overwritten**. An event's `initial_order_id` is always the same value it had on the first `OrderAccepted`.

## Multi-leg, aggregation, and net-parent chains

| Composition | Chain identity rule |
|---|---|
| Multi-leg parent ([[arch-multileg]]) | parent has its own `initial_order_id`; each leg has its own `initial_order_id`; the leg's envelope additionally records `parent_initial_order_id` for upward traversal |
| Aggregated parent ([[arch-aggregation]]) | aggregated parent has its own `initial_order_id`; child orders preserve their own `initial_order_id`; events on the aggregated parent additionally carry `child_initial_order_ids: [UUID]` for downward traversal |
| Netted parent ([[arch-fx-netting]]) | same pattern as aggregation — netted parent has its own chain; children preserve theirs |
| SOR ([[arch-smart-order-router]]) child routes | SOR child routes have their own `initial_route_id`; events carry `parent_initial_route_id` (the route the SOR received from the router) |

A `parent → child` traversal is one indexed query in the [[arch-event-sourcing|event store]]; the reverse direction (child → parent) is another. Both are constant-time, not recursive.

## How the lifecycle FSM stamps these

[[arch-fix-fsm-design|The shared FSM]] stamps `initial_order_id` / `initial_cl_ord_id` / `chain_id` on every event it emits. The pure transition function reads them from the parent state (which holds the original values), copies them into the new event envelope. No transition rule mutates them. This is enforced by the codegen test harness — any transition that tries to overwrite an `initial_*` field fails the property test.

## Query patterns

Concrete uses these IDs enable:

### "Find every event for this order from the start"

```
event_log.query(initial_order_id = X)
```

One indexed query. Returns every event across order, route, fill, bust, allocation, regulatory submission, observability log, trace span.

### "Reconstruct the FIX ClOrdID chain for audit"

```
events = event_log.query(initial_order_id = X)
chain = sorted(events, by=stream_seq).map(e => e.cl_ord_id).distinct()
```

Walk-free reconstruction; no recursion.

### "Find the original order from a fill"

```
fill_event.initial_order_id
```

Direct lookup. Useful for trade-bust cascade and STP audit.

### "Find the trace for an order"

```
spans = jaeger.query(ems.initial_order_id = X)
# also: logs = es.query("ems.initial_order_id": X)
# also: metrics exemplars correlate similarly
```

[[arch-observability|Observability]] uses these IDs as span attributes; same query language across logs, traces, metrics.

### "Audit every action on a customer's portfolio program"

```
event_log.query(group_id = "program-42")
  + event_log.query(initial_order_id ∈ [ids in program-42])
```

Combines the staging-time `group_id` ([[group-id]]) with the chain ID.

## SBE envelope addition

The [[arch-sbe-aeron-transport|SBE envelope]] gains fixed-position fields for chain identity:

```
SessionHeader (extended) {
  ... existing fields ...
  initial_order_id   bytes[16]?       // UUID, optional (zero if not order-related event)
  initial_route_id   bytes[16]?       // UUID
  trace_id           bytes[16]
  parent_span_id     bytes[8]
}
```

Stored as fixed-length UUID bytes (16 bytes each) — cheap. Three identifiers = 48 extra bytes per envelope, acceptable cost for the audit + replay value.

## Best-practices

- **`initial_*` is a write-once contract.** No code, no migration, no admin operation overwrites them. The shared FSM enforces it; tests verify it.
- **Always log + trace by `initial_*`**, not by the current `cl_ord_id`. The latter changes; the former doesn't.
- **Index `initial_order_id` and `initial_route_id`** in the event log, Elasticsearch indices, and metric exemplars. These are the hot query keys.
- **On import from upstream OMS**, adopt their `initial_cl_ord_id` if they supply one (`526` SecondaryClOrdID or `583` ClOrdLinkID). Don't double-mint.
- **On replay**, the `initial_*` IDs are preserved exactly — replay determinism depends on it. See [[arch-time-replay-server]].

## Anti-patterns

- **"We'll just walk OrigClOrdID backwards each time."** Works but expensive. With this pattern every reconstruction is a tree-walk; under load it doesn't scale, and live debugging becomes painful.
- **Adopting the FIX `ClOrdID` as the chain identity.** It changes — by design. Never use it as a stable key.
- **Letting a corner-case codepath mint a new `initial_order_id` mid-lifecycle.** Worst possible failure: silently breaks all later joins. Property-test against it.
- **Per-component chain semantics.** If the order layer's `chain_id` means one thing and the router's means another, joins break. One vocabulary.

## See also

- [[arch-order-route-lifecycle]] (state machines) · [[arch-fix-appendix-d]] (race conditions; chain survives all of them)
- [[arch-fix-fsm-design]] (where stamping is enforced) · [[arch-fix-api-bridge]] (FIX tag mapping)
- [[arch-event-sourcing]] (event log + indices) · [[arch-sbe-aeron-transport]] (envelope extension)
- [[arch-observability]] (trace_id + chain_id correlation) · [[entry-point-aaa]] (trace origin)
- [[arch-multileg]] · [[arch-aggregation]] · [[arch-fx-netting]] · [[arch-smart-order-router]]
- [[arch-allocation-service]] · [[arch-stp-pipeline]] · [[arch-best-execution]]
