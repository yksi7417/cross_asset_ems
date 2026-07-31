# component-07-route-lifecycle

**Covers:** every transition in `schemas/fsm/route.fsm.yaml` — **29 of 29**, verified mechanically by
[`fsm_coverage.py`](../../harness/fsm_coverage.py) — plus the **cross-FSM cascade**, which is the
first place in the slice where one machine drives another.

**Why it exists:** component 6a could create a route and nothing else, so 28 of the route machine's
29 transitions were unreachable and `route` sat out of scope in `fsm-coverage` with a stated reason.
This case is what lets that scope entry flip. It is also the first case where an *event the slice
never received* changes state: a venue fill on a route emits an order fill, and the order machine
moves because of it.

## The cascade is read, not written

`route.fsm.yaml` declares the mapping as `emit_event` effects:

```yaml
- from: PENDING_CANCEL_AT_VENUE
  event: RouteCancelRejected
  to: PARTIALLY_FILLED
  guard: "context.pre_cancel_status == '1'"
  effects:
    - kind: emit_event
      args: { target_fsm: "OrderFsm", event: "CancelRejected" }
```

All three runners read that off the transition result — the effect tables generated in component
6b-i. **None of them contains a route-event-to-order-event table.** Hand-writing one would mean
three languages each holding an opinion about what the YAML says, which is the failure the generator
exists to prevent.

The journal shows the ordering, and the ordering is part of the contract:

```json
{"fields":{"applied":"true","event":"RouteFilled","from":"WORKING","fsm":"route",
 "routeId":"RTE-0000000008","to":"FILLED"},"seq":74,"type":"FsmTransition"}
{"fields":{"applied":"true","clOrdId":"C-08","event":"FullFill","from":"NEW","fsm":"order",
 "to":"FILLED"},"seq":75,"type":"FsmTransition"}
```

Route transition first, then each cascaded order transition in the order the schema declares the
effects. Two languages emitting these in a different order would fail the byte comparison, which is
how we know they agree rather than merely both working.

## Eighteen routes, one per path

One order per route, deliberately: a cascade that moves the parent order cannot then interfere with
an unrelated route, so each path reads independently.

| Route | Path |
|---|---|
| 1 | ack via `PENDING_NEW_AT_VENUE`, then a full replace round trip incl. the `PENDING_REPLACE` self-loop |
| 2 | replace rejected returns the route to `WORKING` |
| 3 | the venue refuses the route outright → `REJECTED` |
| 4 | cancel requested, cancel accepted → `CANCELED` |
| 5 | two fills, then a cancel rejected back to **`PARTIALLY_FILLED`** |
| 6 | cancel rejected back to **`WORKING`** — same event, different arm |
| 7 | a fill lands while a replace is in flight |
| 8 | filled straight out of `WORKING` |
| 9 | partially filled, then filled |
| 10 | a fill lands while a cancel is in flight, then completes it |
| 11 | a full fill wins over an in-flight replace |
| 12–13 | expired out of `WORKING`, and out of `PARTIALLY_FILLED` |
| 14–15 | superseded out of `WORKING`, and out of a pending replace |
| 16–18 | anomaly out of `WORKING`, a pending replace, and a pending cancel |

**Routes 5 and 6 are the pair worth reading.** Both send `RouteCancelRejected` from
`PENDING_CANCEL_AT_VENUE`; one lands in `PARTIALLY_FILLED` and the other in `WORKING`. Nothing in
the corpus says which — `pre_cancel_status` is written by the transition *into* the pending state and
read by the guard on the way out. Three implementations agreeing on that is a stronger claim than
three agreeing on a straight-line path.

## Three negatives

- a `RouteFilled` for a route id that does not exist → `RouteEventIgnored`
- an event name the schema does not define → `RouteEventIgnored`
- `RouteAcknowledged` against route 3, which is already `REJECTED` → recorded as an
  `FsmTransition` with **`applied=false`**, not ignored

That last distinction matters. The route *exists*, so the slice has something to say about it: the
machine declined the event. An ignored event and a declined event are different facts and the journal
keeps them apart.

## What this case proves that no unit test does

**A declined event cascades nothing.** The generated effects are empty on a no-transition, so a route
that refuses an event cannot move the order machine. If effects leaked on a no-transition, route 3's
final `RouteAcknowledged` would emit `ValidationPassed` to order `C-03` — and the order would move on
a venue event the route explicitly refused.

## T-7 closed: a dead route releases its quantity

The last request in the journal re-routes `C-03` for its **full** 1000 shares, and it is accepted:

```json
{"fields":{"clOrdId":"C-03","orderId":"ORD-0000000003","qty":"1000","routeClOrdId":"C-03-2",
 "routeId":"RTE-0000000019","venueMic":"XNYS"},"seq":166,"type":"RouteAccepted"}
```

C-03's first route was rejected by the venue, so its 400 shares are committed nowhere and are
routable again. Under component 6a this was refused with `EMS-RTE-4003` — `routedQty` counted every
route including dead ones, and an order whose only route the venue refused could never be re-routed.
`FILLED` is deliberately **not** in the releasing set: a filled route consumed its quantity, and
forgetting that would let an order be over-filled.

**Seed:** 0.

**Reviewed against:** `schemas/fsm/route.fsm.yaml`. Every `from`/`event`/`to` in `expected.jsonl`
appears in that file's `transitions` list, and `fsm_coverage.py` asserts the converse.

**Scope, stated plainly:** there is still no venue *edge* — nothing speaks FIX, and these
`RouteEvent`s arrive as journal entries rather than from a session. That is component 7. Allocation
is component 8.
