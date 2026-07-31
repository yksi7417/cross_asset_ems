# component-05-order-fsm

**Covers:** every transition in `schemas/fsm/order.fsm.yaml` — **31 of 31**, verified mechanically
by [`fsm_coverage.py`](../../harness/fsm_coverage.py) rather than claimed here.

**Why it exists:** a state machine is the shape where the untested transition is the one that
breaks. Each transition is a separate branch, and no amount of exercising its neighbours covers it.
Thirteen states and thirty-one transitions is more than a reviewer can hold in their head, which is
why coverage is checked by a script and not by reading this file.

## How coverage is measured

From **output**, not input. The runner emits an `FsmTransition` event for every transition it takes:

```json
{"fields":{"applied":"true","clOrdId":"C-A","event":"ReplaceAccepted",
 "from":"PENDING_REPLACE","fsm":"order","to":"REPLACED"},"seq":12,"type":"FsmTransition"}
```

Only `applied=true` counts. A recorded **no**-transition proves the machine *declined* the event,
which is the opposite of coverage — and inferring coverage from the input would credit a case for an
event the machine ignored.

## The twelve orders, and what each walks

| Order | Path |
|---|---|
| `C-A` | replace round trip → replace rejected back to `REPLACED` → cancel rejected → expiry |
| `C-B` | replace rejected back to `NEW` → full fill → **trade correction** |
| `C-C` | two partial fills (incl. `PARTIALLY_FILLED` → itself) → cancel rejected back to `PARTIALLY_FILLED` → done for day |
| `C-D` | fills arriving while a replace *and* a cancel are pending |
| `C-E` | full fill straight out of `NEW` |
| `C-F` | full fill while a replace is pending |
| `C-G` | cancel rejected back to `NEW` → done for day |
| `C-H` | replaced → partial fill → expiry out of `PARTIALLY_FILLED` |
| `C-I` | replaced → full fill |
| `C-J` | cancel accepted — terminal |
| `C-K` | fill → **trade bust** |
| `C-L` | unknown instrument → `ValidationFailed` → `REJECTED` |
| `C-M` | expiry straight out of `NEW` |

Plus two negatives at the end: an event for an order that does not exist, and an event name the
schema does not define. Both are recorded as `OrderEventIgnored` — **an order book fed by a venue
must survive anything the venue sends**, so neither is fatal.

## Two properties this case pins that are easy to lose

**A rejected order consumes no order id.** `C-L` fails validation and still takes the real
`PENDING_NEW → REJECTED` transition — but no `ORD-…` is allocated to it. The FSM keys on **ClOrdID**,
the client's own identifier, which exists before the order reaches us; `OrderID` is ours and is
assigned on acceptance. That is the FIX convention, and it is what lets both properties hold at once.

This was caught the hard way: the first wiring took the order id before validating, which regressed
`SliceMainTest.aRejectedOrderDoesNotConsumeAnIdentifier`. The test failed, which is what it is for.

**Guard state is set by the machine, not by the case.** `pre_replace_status` and `pre_cancel_status`
are written by `update_context` effects on the transitions *into* the pending states, and read by
the guards on the way out. So `C-A` reaching `REPLACED` via `ReplaceRejected` and `C-B` reaching
`NEW` via the same event is the FSM distinguishing them from context the corpus never sets directly.
Three implementations agreeing on that is a stronger claim than three agreeing on a straight-line
path.

**Seed:** 0.

**Reviewed against:** `schemas/fsm/order.fsm.yaml`. Every `from`/`event`/`to` in `expected.jsonl`
appears in that file's `transitions` list — `fsm_coverage.py` asserts the converse (every schema
transition appears here), and the two together mean the corpus and the schema describe the same
machine.

**Scope, stated plainly:** the order FSM only. `route`, `sor`, `multileg` and `venue_session` are
generated in all three languages but nothing in the slice sends them events yet, so
`fsm_coverage.py` lists them as out of scope with a reason rather than silently skipping them.
There is still no routing, no venue edge and no allocation — a filled order stops at `FILLED`.
