---
type: architecture
layer: foundation
status: draft
tags: [architecture/foundation, architecture/fix]
---

# Shared FIX-Compliant FSM — Design

The order/route/venue lifecycles live in many components — Order layer, Router, Venue adapters, Automation, FIX bridge — and those components communicate **only via the network** (SBE over Aeron). For them to agree on what an order is doing at any moment, they must share **one canonical Finite State Machine definition**: same states, same events, same transition rules, same guards, same emitted events. Anything else creates impedance mismatches and silent divergence.

This note proposes the design of that shared FSM — the **core** of the EMS.

> Reads: [[arch-order-route-lifecycle]] (the canonical FIX state machines), [[arch-fix-appendix-d]] (race conditions), [[arch-fix-api-bridge]] (wire-level translation), [[arch-event-sourcing]] (the persistence model).

## What this FSM is — and isn't

| Is | Is not |
|---|---|
| Pure transition function `(state, event, context) -> (state', effects[], events[])` | A general-purpose workflow / orchestration engine |
| FIX-protocol authoritative (states map 1:1 to `OrdStatus`/`ExecType`) | An attempt to "improve on" FIX's vocabulary |
| Shared by every component that touches order/route lifecycle | Owned by a single component |
| Declarative — defined in a versioned schema, codegen'd | Hand-rolled per language |
| Composable — Order FSM, Route FSM, multileg-parent FSM share building blocks | A single monolithic state machine |
| Single-writer per entity at the runtime layer | Lock-free distributed consensus |

## Why this matters

- **Cross-component agreement.** The Order layer and the Router and the venue adapter all "know" what `PendingReplace` means with no translation. Same event types flow over [[arch-sbe-aeron-transport|SBE/Aeron]] from one component to the next.
- **Replay determinism.** [[arch-time-replay-server|Replay]] requires that the same event sequence produces byte-identical state. A pure FSM is the only way.
- **Audit trust.** [[arch-event-sourcing|Event sourcing]] needs the projection function (events → state) to be a pure, versioned, testable artifact.
- **Race-condition correctness.** Appendix D scenarios ([[arch-fix-appendix-d]]) require **explicit modelling of in-flight states** (`PendingReplaceAtVenue`, etc.). An ad-hoc FSM gets this wrong; a declared one is verifiably right.
- **Extensibility.** New asset classes (e.g. options spreads, IRS) add transition variants without forking the core.

## Design principles

### 1. Sealed vocabulary

States, events, ExecTypes, OrdStatuses, and CxlRejReasons are **closed sets** declared in one place (the FSM definition file). New entries require a definition-version bump. No component invents a state at runtime.

### 2. FIX-first naming

Every state has a canonical FIX `OrdStatus` (39) it projects to. Every emitted event has a FIX `ExecType` (150) and `OrdStatus`. Internal names (e.g. `Working`, `PendingReplaceAtVenue`) are clearly mapped — see [[arch-order-route-lifecycle]].

### 3. Pure transition function

The core of the FSM is one function:

```
transition(
  state:    State,
  event:    Event,
  context:  EntityContext      // immutable: entity_id, identity, instrument, etc.
) -> TransitionResult {
  new_state:        State,
  emit_events:      [Event],        // events to append to the entity's stream
  side_effects:     [Effect],       // outbound actions (e.g. send 35=G, fire rule, notify subscribers)
  reject_reason?:   ValidatorCode   // if the transition is illegal under guards
}
```

Properties:

- **Deterministic.** Same `(state, event, context)` always yields the same `TransitionResult`. No clock reads, no random, no I/O.
- **Total.** Defined for every `(state, event)` pair — either a transition, an explicit no-op, or a guard rejection. Never throws unexpectedly.
- **Idempotent at the runtime layer.** Identified by `(entity_id, event.event_id)`; re-applying the same event is a no-op (the runtime checks if `event_id` is already in the stream).

### 4. Effects are external

The FSM does not perform I/O. It returns `Effect` descriptors that the runtime executes:

```
Effect = SendWire(adapter, payload)
       | EmitMetric(name, value)
       | ScheduleTimer(at, event)
       | FireRule(rule_ref, event)
       | NotifySubscribers(topic, payload)
```

The runtime is the only thing that talks to the outside world. The FSM is testable without a network.

### 5. Composition over inheritance

Different entity kinds (Order, Route, MultilegParent, VenueSession) each have their own **FSM definition**, but they share a **library** of building blocks: the State / Event / ExecType vocabulary, the guard expression language, the codegen pipeline, the test harness.

A `MultilegParent` FSM aggregates child Route/Order FSM states via a declared rule (e.g. `ALL_OR_NONE` legs → parent transitions to `Filled` only when all legs are `Filled`).

### 6. Versioned, declarative definitions

FSMs live in **definition files** (one per entity kind), checked in alongside SBE schemas. They are:

- **Versioned** — every event carries the FSM version that produced it.
- **Pinned for replay** — replays load the historical version, not "current".
- **Code-generated** — same definition produces SBE schemas, transition code, validators, diagrams (Mermaid + Graphviz), and the test harness.

### 7. Guards live in the definition

A transition is allowed only if its guard expression evaluates true:

```
transition:
  from: Working
  on:   ReplaceRequestedFromClient
  guard: requested_new_qty > cum_qty       # the FIX rule
  to:   PendingReplaceAtVenue
  emit:
    - ExecType: "E", OrdStatus: "E"        # Pending Replace
  effects:
    - SendWire(venue, fix("G", new_cl_ord_id, orig_cl_ord_id, fields))

transition:
  from: Working
  on:   ReplaceRequestedFromClient
  guard: requested_new_qty <= cum_qty      # guard fails
  to:   Working                            # state unchanged
  reject:
    code: EMS-RTE-2030
    fix_cxl_rej_reason: 0                  # too late / cum exceeds
```

Guards are pure expressions over `(state, event, context)`. No I/O. Permissions and business rules are **not** in guards — those live in [[arch-validator]] which runs before events even reach the FSM.

---

## The definition format (concrete)

A single YAML file per entity kind, kept under version control alongside the SBE schemas:

```yaml
# fsm/route.fsm.yaml
fsm:
  name: Route
  version: 3

# Canonical FIX vocabulary used by transitions
ord_statuses: [A, 0, 1, 2, 3, 4, 5, 6, 8, C, E]    # FIX tag 39
exec_types:   [0, 1, 2, 3, 4, 5, 6, 7, 8, A, B, C, D, E, F, G, H, I]  # tag 150
cxl_rej_reasons: [0, 1, 2, 3, 4, 5, 6, 99]         # tag 102

states:
  - name: Pending
    description: "EMS prepared to send; not dispatched"
    fix_ord_status: null                              # not yet at venue
    terminal: false

  - name: Sent
    description: "35=D dispatched; awaiting venue ack"
    fix_ord_status: A                                 # Pending New
    terminal: false

  - name: Working
    description: "Venue acknowledged; live in market"
    fix_ord_status: "0"                               # New
    terminal: false

  - name: PartiallyFilled
    fix_ord_status: "1"
    terminal: false

  - name: Filled
    fix_ord_status: "2"
    terminal: true

  - name: PendingReplaceAtVenue
    fix_ord_status: E                                 # Pending Replace
    description: "35=G in flight; prior parameters still workable"
    terminal: false

  - name: PendingCancelAtVenue
    fix_ord_status: "6"
    terminal: false

  - name: Canceled
    fix_ord_status: "4"
    terminal: true

  - name: Rejected
    fix_ord_status: "8"
    terminal: true

  - name: Expired
    fix_ord_status: C
    terminal: true

events:
  # Inbound (driving) events
  - { name: RouteRequested,           direction: in, source: order_layer }
  - { name: VenueAckNew,               direction: in, source: venue }
  - { name: VenueExecPartialFill,      direction: in, source: venue }
  - { name: VenueExecFill,             direction: in, source: venue }
  - { name: ClientReplaceRequest,      direction: in, source: order_layer }
  - { name: VenuePendingReplace,       direction: in, source: venue }
  - { name: VenueReplaced,             direction: in, source: venue }
  - { name: VenueOrderCancelReject,    direction: in, source: venue }  # 35=9 — does NOT terminate
  - { name: ClientCancelRequest,       direction: in, source: order_layer }
  - { name: VenuePendingCancel,        direction: in, source: venue }
  - { name: VenueCanceled,             direction: in, source: venue }
  - { name: VenueRestated,             direction: in, source: venue }  # ExecType=D unsolicited
  - { name: VenueTradeBust,            direction: in, source: venue }  # ExecType=H
  - { name: VenueTradeCorrect,         direction: in, source: venue }  # ExecType=G
  - { name: VenueRejected,             direction: in, source: venue }
  - { name: ClockTifExpiry,            direction: in, source: clock }
  # Outbound (emitted) events
  - { name: RouteSent,                 direction: out }
  - { name: RouteAcknowledged,         direction: out }
  - { name: RouteWorking,              direction: out }
  - { name: RoutePartiallyFilled,      direction: out }
  - { name: RouteFilled,               direction: out }
  - { name: RouteReplaceRequested,     direction: out }
  - { name: RouteReplacePendingAtVenue,direction: out }
  - { name: RouteReplaced,             direction: out }
  - { name: RouteReplaceRejected,      direction: out }    # 35=9; not terminal
  - { name: RouteCancelRequested,      direction: out }
  - { name: RouteCancelPendingAtVenue, direction: out }
  - { name: RouteCanceled,             direction: out }
  - { name: RouteCancelRejected,       direction: out }
  - { name: RouteRejected,             direction: out }
  - { name: RouteExpired,              direction: out }
  - { name: RouteRestated,             direction: out }
  - { name: TradeCorrected,            direction: out }
  - { name: TradeCanceled,             direction: out }
  - { name: RouteAnomaly,              direction: out }

# Context — read-only inputs to guard expressions
context_schema:
  order_qty:        decimal
  cum_qty:          decimal
  leaves_qty:       decimal           # always derived; not stored independently
  cl_ord_id:        string
  venue:            VenueRef
  dialect:          enum

# Transitions — closed set
transitions:
  - { from: Pending, on: RouteRequested, to: Sent,
      emit: [RouteSent],
      effects: [SendWire(venue, build_35D())] }

  - { from: Sent, on: VenueAckNew, to: Working,
      emit: [RouteAcknowledged, RouteWorking] }

  - { from: Sent, on: VenueRejected, to: Rejected,
      emit: [RouteRejected] }

  - { from: Working, on: VenueExecPartialFill, to: PartiallyFilled,
      emit: [RoutePartiallyFilled],
      mutate: { cum_qty: cum_qty + event.last_qty, leaves_qty: order_qty - cum_qty - event.last_qty } }

  - { from: PartiallyFilled, on: VenueExecPartialFill, to: PartiallyFilled,
      emit: [RoutePartiallyFilled],
      mutate: { cum_qty: cum_qty + event.last_qty } }

  - { from: [Working, PartiallyFilled], on: VenueExecFill, to: Filled,
      emit: [RouteFilled],
      mutate: { cum_qty: order_qty, leaves_qty: 0 } }

  # ---- replace path ----
  - { from: [Working, PartiallyFilled], on: ClientReplaceRequest,
      guard: event.new_qty > cum_qty,
      to: PendingReplaceAtVenue,
      emit: [RouteReplaceRequested],
      effects: [SendWire(venue, build_35G(new_clord, orig_clord, fields))] }

  - { from: [Working, PartiallyFilled], on: ClientReplaceRequest,
      guard: event.new_qty <= cum_qty,
      to: <self>,                          # stay
      reject: { code: EMS-RTE-2030, fix_cxl_rej_reason: 0 } }

  - { from: PendingReplaceAtVenue, on: VenuePendingReplace,
      to: PendingReplaceAtVenue,
      emit: [RouteReplacePendingAtVenue] }

  - { from: PendingReplaceAtVenue, on: VenueReplaced,
      to: Working,                          # or PartiallyFilled if cum_qty > 0
      transform_to: { Working if cum_qty == 0 else PartiallyFilled },
      mutate: { order_qty: event.new_order_qty },
      emit: [RouteReplaced] }

  - { from: PendingReplaceAtVenue, on: VenueOrderCancelReject,
      to: <prior>,                          # IMPORTANT: revert to pre-replace state
      emit: [RouteReplaceRejected] }
      # Note: Appendix D — order is NOT terminated. See [[arch-fix-appendix-d]].

  # Appendix D7/D10 — fill during PendingReplace applies to PRIOR params
  - { from: PendingReplaceAtVenue, on: VenueExecPartialFill,
      to: PendingReplaceAtVenue,
      emit: [RoutePartiallyFilled],
      mutate: { cum_qty: cum_qty + event.last_qty } }

  - { from: PendingReplaceAtVenue, on: VenueExecFill,
      to: Filled,
      emit: [RouteFilled],
      mutate: { cum_qty: order_qty } }
      # If the order fully fills while a replace is in flight, the replace becomes moot.
      # The venue will subsequently 35=9 the replace (Scenario D4/D5); we record it as
      # RouteReplaceRejected on the next transition rule below.

  - { from: Filled, on: VenueOrderCancelReject,
      to: Filled,
      emit: [RouteReplaceRejected] }       # informational; state remains terminal

  # ---- cancel path ----
  - { from: [Working, PartiallyFilled], on: ClientCancelRequest,
      to: PendingCancelAtVenue,
      emit: [RouteCancelRequested],
      effects: [SendWire(venue, build_35F(orig_clord))] }

  - { from: PendingCancelAtVenue, on: VenuePendingCancel,
      to: PendingCancelAtVenue,
      emit: [RouteCancelPendingAtVenue] }

  - { from: PendingCancelAtVenue, on: VenueCanceled,
      to: Canceled,
      emit: [RouteCanceled] }

  - { from: PendingCancelAtVenue, on: VenueOrderCancelReject,
      to: <prior>,
      emit: [RouteCancelRejected] }

  # Appendix D4/D5 — fill races and wins; cancel becomes too-late
  - { from: PendingCancelAtVenue, on: VenueExecFill,
      to: Filled,
      emit: [RouteFilled],
      mutate: { cum_qty: order_qty } }
  - { from: Filled, on: VenueOrderCancelReject,
      to: Filled,
      emit: [RouteCancelRejected] }        # informational

  # ---- unsolicited from venue ----
  - { from: [Working, PartiallyFilled], on: VenueCanceled,    # no ClientCancelRequest preceded
      to: Canceled,
      emit: [RouteCanceled] }
  - { from: [Working, PartiallyFilled], on: VenueRestated,
      to: <self>,
      emit: [RouteRestated],
      mutate: { order_qty: event.new_order_qty if event.new_order_qty else order_qty,
                limit_price: event.new_price if event.new_price else limit_price } }

  # ---- post-fill bust/correct ----
  - { from: [Filled, PartiallyFilled], on: VenueTradeBust,
      transform_to: { Filled if cum_qty - event.busted_qty == order_qty
                     else PartiallyFilled if cum_qty - event.busted_qty > 0
                     else Working },
      emit: [TradeCanceled],
      mutate: { cum_qty: cum_qty - event.busted_qty } }
  - { from: [Filled, PartiallyFilled], on: VenueTradeCorrect,
      to: <self>,
      emit: [TradeCorrected],
      mutate: { /* avg_px and per-fill record updated; cum_qty unchanged */ } }

  # ---- TIF expiry ----
  - { from: [Working, PartiallyFilled], on: ClockTifExpiry,
      to: Expired,
      emit: [RouteExpired] }
```

This is the **complete definition** for the Route entity. The Order entity has its own file (`order.fsm.yaml`), VenueSession has its own, etc.

## What gets generated

From `route.fsm.yaml`, the build pipeline produces:

| Artifact | Used by |
|---|---|
| `route_fsm.rs` / `RouteFsm.java` / etc. | The transition function in every consuming language. |
| `route_events.sbe.xml` | SBE schemas for the events; codegen → typed encoders/decoders. See [[arch-sbe-aeron-transport]]. |
| `route_fsm.mermaid` | A diagram embedded in [[arch-router-layer]] and [[arch-order-route-lifecycle]]. |
| `route_fsm.dot` | Graphviz for tooling. |
| `route_fsm_tests.rs` | Golden transition tests (every transition + every reject path). |
| `route_fsm_invariants.rs` | Property tests (reachability, terminal-state correctness, FIX `OrdStatus`/`ExecType` consistency). |
| `route_fix_mapping.md` | Human-readable mapping table for [[arch-fix-api-bridge]]. |

One source of truth → one set of guarantees.

---

## Architecture — how it composes across components

```mermaid
flowchart TB
  subgraph "Definition (versioned, codegen'd)"
    D1[order.fsm.yaml]
    D2[route.fsm.yaml]
    D3[multileg.fsm.yaml]
    D4[venue_session.fsm.yaml]
  end

  subgraph "Shared library<br/>(every component links this)"
    L1[fsm-core<br/>transition + guard interpreter]
    L2[fsm-codegen]
    L3[Generated FSMs: Order, Route, Multileg, VenueSession]
    D1 --> L3
    D2 --> L3
    D3 --> L3
    D4 --> L3
    L1 --> L3
  end

  subgraph "Component: Order layer<br/>arch-order-staged"
    O[Order FSM Runtime]
    OL[(order event log)]
    L3 --> O
    O --> OL
  end

  subgraph "Component: Router<br/>arch-router-layer"
    R[Route FSM Runtime]
    RL[(route event log)]
    L3 --> R
    R --> RL
  end

  subgraph "Component: Venue Adapter<br/>arch-venue-connectivity"
    A[Route FSM Runtime<br/>(adapter's view)]
    L3 --> A
  end

  subgraph "Component: Automation<br/>arch-automation-layer"
    AU[Read-only FSM projection]
    L3 --> AU
  end

  subgraph "SBE/Aeron bus<br/>arch-sbe-aeron-transport"
    BUS[(events)]
  end

  R -.publishes Route events.-> BUS
  O -.publishes Order events.-> BUS
  A -.publishes adapter events.-> BUS
  BUS -.consumed by.-> O
  BUS -.consumed by.-> R
  BUS -.consumed by.-> AU
```

Key observations:

- **Every component links the same `fsm-core` library + the same generated FSMs.** Upgrading the FSM version means redeploying every component.
- **The "authoritative" runtime for each entity kind lives in one component.** The Order FSM authority is the order layer; the Route FSM authority is the router. Other components have **read-only projections** built from the same event stream.
- **Adapters also run a Route FSM** to track the adapter's view of venue state. When the venue says `35=8 ExecType=5 Replaced`, the adapter applies the corresponding event to its local Route FSM, emits the result onto the bus, and the router's authoritative copy applies the same transition. Both arrive at the same state by construction.
- **Wire format is symmetric.** Inbound and outbound events use the SBE schemas generated from the FSM definitions. The bus carries FSM events, not application-layer JSON.

## Composed FSMs (multileg, aggregation)

Some entities derive their state from child FSMs:

```yaml
# fsm/multileg_parent.fsm.yaml — sketch
fsm:
  name: MultilegParent
  version: 1
  composes:
    - role: leg
      cardinality: N
      kind: Route
      reduce: |
        case execution_mode of
          ALL_OR_NONE:
            if all(leg.state == Filled) then parent.state = Filled
            else if any(leg.state == Rejected) then parent.state = Rejected
            else parent.state = LegsWorking
          LEGS_INDEPENDENT:
            if all(leg.state.terminal) then parent.state = derive_terminal(legs)
            else parent.state = LegsWorking
          SEQUENCED:
            # see [[spot-first]] — gate sibling leg dispatch on prior leg state
            ...
```

The composition rule is itself a pure function — testable, replayable, versioned. The runtime watches child FSMs and re-evaluates on every child transition.

---

## Lifecycle Chaining & Cascading Cancellations

This is the design's hardest problem. **An order cannot transition to `Canceled` until every route it owns reaches a terminal state at its venue.** Otherwise the order sits in `PendingCancel` indefinitely, the FIX-paired client never sees the final state, the trader's blotter is stuck, and ops gets paged. The same cascade applies to multileg parents, aggregation parents, and any other composed entity.

The FSM design has to model this **as first-class state** — not as runtime hand-waving. This section is the design.

### The core problem (concrete example)

An order has 3 routes:

```
Order #42        OrderQty=100  CumQty=30  LeavesQty=70
  ├── Route A    @ MarketAxess   Working   qty=40 (filled 20)
  ├── Route B    @ BBG ALLQ      Working   qty=40 (filled 10)
  └── Route C    @ FXConnect     Working   qty=20 (filled 0)
```

Trader calls `cancel_orders(#42)`. What should happen?

Naive answer: "Order → Canceled". **Wrong.** Routes A, B, C are still working at venues. Until each venue confirms cancel (or sneaks in a fill), the order is **not** canceled.

Correct answer:

1. Order enters `PendingCancel` (FIX `OrdStatus=6`, visible to client).
2. **Cascade**: emit `RouteCancelRequested` for each route. Each route enters its own `PendingCancelAtVenue` (per [[arch-router-layer]]).
3. Wait for **every route** to reach terminal — `Canceled`, `Filled`, `Rejected`, or `Expired`.
4. Once all routes terminal, **resolve** the order based on the outcome distribution:
   - All routes `Canceled`, no new fills → order `Canceled`.
   - One route `Filled` during cancel (Appendix D4/D5) → order may end up `PartiallyFilled` or `Filled`.
   - Mixed terminal states → resolution rule applies (see below).

### Composition model

The FSM library adds **parent–child observation** to the composition pattern:

```yaml
# fsm/order.fsm.yaml — sketch (relevant transitions)
fsm:
  name: Order
  version: 4
  composes:
    - role: route
      cardinality: 0..N
      kind: Route
      observe: [state_changed, fill_received]   # subscribe to these child events

transitions:
  - { from: [Working, PartiallyFilled], on: ClientCancelRequest,
      to: PendingCancel,
      emit: [OrderCancelRequested, OrderPendingCancel],
      effects:
        - for_each_child(route in self.routes where !route.state.terminal):
            DispatchEvent(child=route, event=ClientCancelRequest)
        - StartTimer(timeout=order.cancel_timeout, on_fire=CancelCascadeTimeout) }

  # Observed: a child route's state changed; re-evaluate parent
  - { from: PendingCancel, on: ChildStateChanged,
      guard: not all_children_terminal(),
      to: PendingCancel,
      emit: [] }                          # stay; just absorb the child update

  - { from: PendingCancel, on: ChildStateChanged,
      guard: all_children_terminal(),
      transform_to: resolve_cascade(),    # see below
      emit: [resolved_event()],
      effects: [CancelTimer(cancel_cascade)] }

  - { from: PendingCancel, on: CancelCascadeTimeout,
      to: CancelCascadeAnomaly,           # explicit, not terminal — escalate to ops
      emit: [OrderCancelCascadeStuck],
      effects: [NotifyOps(reason="routes_not_terminal", stuck_route_ids=...)] }
```

The `resolve_cascade()` pseudo-function is a pure aggregation over child states:

```
fn resolve_cascade(children: [Route]) -> State {
  let final_cum_qty = sum(child.cum_qty for child in children)
  if final_cum_qty == 0:
    return Canceled           // clean cancel
  if final_cum_qty < order_qty:
    return PartiallyFilledCanceled    // some fills snuck in; rest canceled
  if final_cum_qty == order_qty:
    return Filled              // Appendix D4/D5 at scale — fills won across the cascade
}
```

`PartiallyFilledCanceled` is **a distinct FIX state** — `OrdStatus = 4 (Canceled)` with `CumQty < OrderQty` and `LeavesQty = 0`. FIX permits this: an order can be canceled with a non-zero `CumQty`.

### Cascading replace

Same pattern for `35=G` at the order level when routes are working:

```
Order qty=100 → user requests qty=70
  Route A (working 40) → keep
  Route B (working 40) → replace to 20 (reduce)
  Route C (working 20) → cancel entirely (no room)
```

Or, depending on the firm's route-allocation policy, *all* routes are cancel-replaced proportionally. The order's FSM emits a **cascade plan**:

```yaml
  - { from: [Working, PartiallyFilled], on: ClientReplaceRequest,
      to: PendingReplaceCascade,
      effects: [build_cascade_plan(new_order_qty)] }
```

Each child route receives its own `ClientReplaceRequest` or `ClientCancelRequest` from the cascade plan. The parent waits in `PendingReplaceCascade` until the plan resolves. Same timeout + anomaly handling.

### Sequenced multileg cascade

When `multileg.execution_mode = SEQUENCED` (e.g. [[spot-first]]):

- A parent-level cancel **arriving before the spot leg fills** simply removes the forward leg from the queue (it was never dispatched) and proceeds with cancelling the spot leg.
- A cancel **arriving while spot is in `PendingCancel`** waits for spot to terminal. If spot fills, the forward leg's dispatch decision is re-evaluated (firm-policy: dispatch the forward as committed, or cancel since the parent intent is cancel).
- A cancel **arriving after forward leg dispatch** is a normal multi-route cascade.

The leg dispatch gate (the spot-first rule itself) is a transition with a guard on parent state. The same FSM pattern handles all three cases.

### Aggregation cascade (three levels deep)

Aggregated parents stack the pattern:

```
AggregatedParent (1 entity, FSM = Order)
  ├── Child Order A (FSM = Order)
  │     ├── Route A1
  │     └── Route A2
  ├── Child Order B
  │     └── Route B1
  └── Child Order C
        └── Route C1
```

Cancel of the AggregatedParent cascades twice: parent → child orders → child routes. The FSM library handles arbitrary depth — `observe` declarations can chain transitively. The runtime fans out the cancel cascade in parallel; the parent waits in `PendingCancelCascade` for all leaves to reach terminal.

### Cases the FSM definition must explicitly cover

These are the cascade edge cases — all declared as transitions in the order FSM, not left for runtime to figure out:

| Case | FSM handling |
|---|---|
| All routes `Canceled` cleanly | Resolve → `Canceled`, `CumQty` unchanged. |
| All routes `Canceled` but new fill arrived during cascade (D4/D5 race) | Resolve → `Canceled` with updated `CumQty` (note: `PartiallyFilledCanceled` if `CumQty < OrderQty`). |
| One route `Filled` (cancel lost the race) | If `CumQty == OrderQty` → `Filled`. Else → `PartiallyFilledCanceled`. |
| One route's cancel rejected (`35=9 CxlRejReason=0`), stays `Working` | **Re-dispatch cancel** to that route (retry policy, configurable max). If max retries hit → escalate as `CancelCascadeAnomaly`. |
| Route's venue adapter network-partitioned during cascade | Route enters `UncertainState` at adapter level. Parent stays in `PendingCancel` until adapter reconciles. Timeout → `CancelCascadeAnomaly`. |
| Cascade arrives at terminal but FIX-paired client cancel ack was never sent (mid-cascade restart) | Replay from event log on restart; emit appropriate `35=8` ExecutionReports per surviving event sequence (per [[arch-fix-appendix-d|D31 PossResend]] semantics for outbound resends). |
| Venue cancels a route unsolicited mid-cascade | Same handling; absorbed into the cascade naturally — route reaches terminal `Canceled`, parent re-evaluates. |
| Trade bust arrives mid-cascade (post-fill cancellation) | `CumQty` decreases on the affected route; parent re-evaluates. May un-stick a `PartiallyFilledCanceled` → `Canceled`. |
| Multiple cascades in flight (replace cascade then cancel cascade) | Forbidden by single-pending-modification rule. Second request returns `EMS-RTE-2040 cascade_already_pending`. After current cascade resolves, queued request fires. |

### Timeout and anomaly

Every cascade has a configurable timeout (`cascade_timeout_ms`, defaults to 60s for orders, 30s for individual routes). On timeout:

- Parent transitions to a **non-terminal `CancelCascadeAnomaly`** state (FIX `OrdStatus=9 Suspended` projection, with explanatory text).
- `RouteAnomaly` events emitted per stuck child.
- Ops dashboard notified via [[arch-jmx-introspection]].
- FIX-paired client gets `35=8 ExecType=I OrdStatus=9 Suspended` with text describing the stuck children.

Ops triage manually decides: force-cancel-at-venue (out-of-band), accept-partial-fill, or wait. Each path is its own admin operation that resumes the cascade.

### FIX-paired client visibility through the cascade

The FIX client only knows the order, not the routes. When a cascade is in progress:

- They see `35=8 ExecType=6 OrdStatus=6 Pending Cancel` immediately on cancel request.
- They see `35=8 ExecType=F OrdStatus=1 PartiallyFilled` for any fill that races in during the cascade.
- They see the **final** `35=8 ExecType=4 Canceled` (or `Filled`) only when the cascade resolves.
- Latency from cancel request to terminal `35=8` reflects the cascade's worst-leg latency. The client must be prepared for "pending cancel" to last longer than a single round-trip.

If the cascade times out → `35=8 ExecType=I OrdStatus=9 Suspended` with text. The client must handle `9 Suspended` (rare but spec-supported).

### Why declare all this in the FSM

Hand-waving the cascade ("the order layer will figure it out at runtime") is exactly what causes stuck orders in production. Declaring the cascade as FSM transitions with:

- Explicit `PendingCancelCascade` / `PendingReplaceCascade` / `CancelCascadeAnomaly` states.
- Explicit child-observation events.
- Explicit resolution rules (`resolve_cascade()`).
- Explicit timeout transitions.
- Explicit anomaly path with FIX `OrdStatus=9` projection.

…means the cascade is **testable, replayable, versioned, and reviewable**. Every cascade scenario above becomes a golden test in the FSM's test suite. The runtime can never accidentally get a cascade wrong because it doesn't *decide* the cascade — it just dispatches the events the FSM declared.

### The runtime's role in cascades

The runtime (per-entity-kind) provides:

- **Child-event subscription**: the order runtime subscribes to its routes' event topics. Each child event arrives as a `ChildStateChanged` event on the parent's queue.
- **Timer dispatch**: the FSM emits `ScheduleTimer` effects; the runtime fires `ClockTimerExpired` events back into the FSM at the deadline. Goes through the [[arch-time-replay-server|clock interface]] for replay determinism.
- **Cascade plan dispatch**: when the FSM emits `for_each_child(...)` effects, the runtime delivers events to child runtimes (possibly cross-component via the [[arch-sbe-aeron-transport|bus]]).
- **Retry policy execution**: configurable, per-firm/per-route. Bounded by the FSM's retry counter; the FSM itself is purely functional.

The runtime does **no policy decisions** — it just executes effects and feeds events back. All decisions live in the FSM definition.

---

## Race conditions & in-flight states — design-level handling

### Single-writer per entity_id

For any given `entity_id` (an order_id, route_id, session_id), exactly **one** process / thread / actor owns transitions at a time. This is the runtime's responsibility — the FSM itself doesn't need locks because the pure transition function is called sequentially.

Implementations:

- **Order layer**: hash `order_id` to a partition; one consumer per partition processes events in arrival order.
- **Router**: same pattern keyed on `route_id`.
- **Venue adapter**: per-route partitioning; the adapter session itself is single-threaded for the route.

This is what makes the [[arch-fix-appendix-d|Appendix D]] race scenarios manageable — events from the venue arrive on a queue, are processed one at a time, and the FSM evolves through its declared transitions.

### Idempotency

Each event carries a unique `event_id`. The runtime maintains a recent `event_id` cache per entity; replays of the same id return the previously emitted result without re-applying the transition.

### Effect ordering

The runtime applies the transition, persists the emitted events, then dispatches effects — in that order. Crash mid-effect is safe: on restart, projections re-derive from the event log; effects that look like they haven't fired (e.g. an outbound `35=G` that wasn't acked) are re-dispatched with `PossResend=Y` per [[arch-fix-appendix-d|D31]] semantics.

### Concurrent inbound

Two events arriving for the same entity (e.g. client `35=F` cancel and venue `35=8 ExecType=F` fill near-simultaneously) are still serialized at the single-writer queue. Order within the queue is by arrival; the FSM handles either ordering correctly because every `(state, event)` pair has a defined transition.

---

## Versioning protocol

The FSM definition has a `version` field. Every event carries the FSM version that produced it. Two rules:

### Rule 1: events are write-once with their version

Once an event is in the log with `fsm_version=N`, replays use the historical definition at version N to derive state. **Never** retroactively apply a new definition to old events — that breaks audit reproducibility.

### Rule 2: schema evolution is additive

Between versions, you may:

- **Add** states.
- **Add** events.
- **Add** transitions.
- **Add** guards (only if they would never have rejected a historical event).
- **Tighten** an existing guard only with a major version bump.

You may not:

- Rename states or events (rename only via deprecation + new name).
- Remove transitions that historical events used.
- Change the semantics of a `fix_ord_status` or `exec_type` mapping.

### Deployment

A definition version bump triggers redeployment of every component that consumes the FSM (per [[arch-jmx-introspection|component introspection]]). The deployment plan:

1. Bump definition version in repo.
2. Codegen regenerates artifacts.
3. Property tests + golden replays verify backward compatibility.
4. Deploy all components together. Mixed-version deployments are not supported because events from version N+1 may be unrecognised by version N consumers.

---

## Testing strategy

### Layer 1 — pure transition tests

For every `(state, event)` pair declared in the definition:

```
test "Working + ClientReplaceRequest (qty > cum_qty) → PendingReplaceAtVenue":
  given: Route { state: Working, order_qty: 10, cum_qty: 3 }
  when:  apply(ClientReplaceRequest { new_qty: 15 })
  then:
    state == PendingReplaceAtVenue
    emit_events == [RouteReplaceRequested]
    effects == [SendWire(... 35=G ...)]
```

Generated from the definition; one test per row in the transition table.

### Layer 2 — property tests

- Every reachable state is reachable from `Initial` via some event sequence.
- Every terminal state is unreachable from itself except via the same state (i.e. terminal stays terminal).
- Every `fix_ord_status` value on a state correctly projects to the latest emitted ExecutionReport.
- Quantity invariants hold (`OrderQty = CumQty + LeavesQty`) on every emitted event.

### Layer 3 — Appendix D scenarios

Each scenario in [[arch-fix-appendix-d]] becomes a golden test: input event sequence → expected state evolution → expected emitted events.

### Layer 4 — fuzz / model-checking

Generate arbitrary valid event sequences; verify no transition panics, all invariants hold. With small state spaces, run through TLA+ / Alloy for exhaustive checking.

### Layer 5 — golden replays

Real production event slices, pinned to the FSM version that produced them, must replay byte-identically. Run on every release.

---

## Tooling

### `fsm-diff`

Given two versions of a definition, output:

- Added / removed / changed states.
- Added / removed / changed transitions.
- Whether the change is backward-compatible.

### `fsm-explore`

REPL: load a definition, type an event sequence, see the resulting state and emitted events. For ops triage.

### `fsm-replay`

Given an entity's event stream, replay it through a specified FSM version. Diff against the historical projection. Surfaces non-determinism bugs immediately.

### `fsm-coverage`

After a test run, report which `(state, event)` pairs were exercised. Goal: 100%.

---

## The runtime — what wraps the FSM

```
RuntimeForEntity<K> {
  load_state(entity_id) -> (State, last_event_id)
      // re-derive from event log; cached snapshot if available
  
  apply_event(entity_id, event) -> Result
      // 1. Read current state.
      // 2. Call FSM transition.
      // 3. Persist emitted events to the entity's stream.
      // 4. Dispatch side effects.
      // 5. Update snapshot if interval reached.
      // 6. Notify subscribers on the bus.
}
```

Each component instantiates one runtime per entity kind it owns. The runtime is the only place that does I/O; the FSM itself stays pure.

Cross-component visibility: every component subscribes to the event topics of entity kinds it cares about. They run their own (read-only) projection by applying the same FSM transitions on the received events. Read-only views never persist (their event log is the upstream owner's).

---

## What this design buys us

| Concern | How the FSM design addresses it |
|---|---|
| **Cross-component agreement** | Same definition, same library, same transitions, same emitted events. No translation gaps. |
| **FIX correctness** | Definition is FIX-first. Every state has an `OrdStatus`. Every emission has an `ExecType`. [[arch-fix-appendix-d|Appendix D]] scenarios are declared transitions, not interpretation. |
| **Replay determinism** | Pure transition function; event log + FSM version → exact state. |
| **Audit** | Every transition emits one or more events; the log is the truth. |
| **Race resilience** | Single-writer per entity + explicit in-flight states (`PendingReplace`, `PendingCancel`) + declared rules for fill-during-replace and friends. |
| **Extensibility** | New asset class → add transitions to existing FSMs or define a new composed FSM (multileg variant). No core change. |
| **Tooling** | Codegen from one source: code, schemas, diagrams, tests, docs. |
| **Operability** | `fsm-explore`, `fsm-replay`, introspection per [[arch-jmx-introspection]]. |
| **Versioning** | Explicit, additive, deployment-coordinated. Old events stay valid under their definition version. |

---

## What this design intentionally does NOT include

- **Business rules** (permissions, limits, license checks). Those live in [[arch-validator]] and run **before** the FSM sees the event. The FSM assumes the validator has already passed.
- **Persistence concerns** (which database, snapshot frequency). Those are runtime / [[arch-event-sourcing|event-sourcing]] concerns.
- **Network transport** (Aeron channels, SBE codec choices). Those are [[arch-sbe-aeron-transport|transport-layer]] concerns.
- **Asset-class-specific quantity math** (decimal precision, lot sizes, tick rules). Those live in the validator and the venue-specific instrument extension.
- **A DSL beyond what's shown.** The format is YAML-with-expressions; the guard expression language is small (boolean over context + event fields). If you find yourself wanting loops or recursion, the rule belongs in the validator or as a separate FSM, not as a guard.

---

## Open design decisions to make

These are the choices to lock in before implementation:

1. **Definition language.** YAML proposed; alternatives include TOML, Protobuf-with-options, or a custom DSL. Recommend YAML for human readability and tool ecosystem.
2. **Guard expression language.** Tiny boolean expression language (suggested) vs. embedded scripting. Recommend tiny custom — easier to verify, easier to codegen across languages.
3. **Snapshot strategy.** Every-N-events vs. time-bucketed vs. lazy (only on slow projections). Recommend every-1000-events + lazy on demand.
4. **Cross-language codegen target.** Rust + Java + Python at minimum; possibly Go, TypeScript for tooling.
5. **Where the FSM registry lives.** Same repo as code (mono-repo style) vs. separate "schemas" repo with versioned releases. Recommend same repo.
6. **Distributed-runtime coordination.** Single-writer-per-partition is clear; the partitioning scheme (consistent hashing? leader election?) needs choosing. Recommend consistent hashing with Aeron-clustered raft for partition leadership.

---

## See also

- [[arch-order-route-lifecycle]] — the FIX state machines this design materialises
- [[arch-fix-appendix-d]] — the race conditions the FSM must handle
- [[arch-fix-api-bridge]] — the wire-level surface
- [[arch-sbe-aeron-transport]] — the event transport
- [[arch-event-sourcing]] — the persistence model the FSM projects over
- [[arch-time-replay-server]] — replay determinism, clock injection
- [[arch-validator]] — guards' business-rule cousin
- [[arch-order-staged]] · [[arch-router-layer]] · [[arch-venue-connectivity]] · [[arch-automation-layer]] · [[arch-multileg]]
