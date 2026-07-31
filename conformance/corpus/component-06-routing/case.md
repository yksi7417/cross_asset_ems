# component-06-routing

**Covers:** route creation — the point where an accepted order becomes outbound quantity aimed at a
named venue. One route FSM transition (`PENDING -RouteSent-> SENT`), four reject codes, and the
quantity arithmetic that decides which of the two happens.

**Why it exists:** routing is the first place the slice makes a decision that *spends* something.
Every earlier component either accepted an order or refused it; this one commits quantity, and
quantity that leaks — routed twice, or routed and then unroutable — is the kind of bug that shows up
as a position nobody can explain. Six of the fourteen requests here are refusals, because the
refusals are the part worth pinning.

## The two orders, and the fourteen requests against them

`C-A` is a 1000-share buy, `C-B` a 500-share sell, and `C-Z` is refused by the REFERENCE layer
before it ever becomes an order.

| # | Request | Outcome |
|---|---|---|
| 1 | `C-A` 400 @ 15000 to XNAS | 🟢 `RTE-0000000001`, ClOrdID `C-A-1` |
| 2 | `C-A` 600 to XNYS, no price | 🟢 `RTE-0000000002`, `C-A-2` — a market route, and it takes the exact remainder |
| 3 | `C-A` 100 to XLON | 🔴 `EMS-RTE-4003` — 0 remaining |
| 4 | `C-B` 0 to XNAS | 🔴 `EMS-RTE-4003` — zero is not a route |
| 5 | `C-NOPE` 100 | 🔴 `EMS-RTE-4001` — no such order |
| 6 | `C-Z` 100 | 🔴 `EMS-RTE-4002` — the order exists, in `REJECTED` |
| 7 | `C-B` 200, ClOrdID `C-A-1` | 🔴 `EMS-RTE-2005` — that ClOrdID belongs to route 1 |
| 8 | `C-B` 200 to XNAS | 🟢 `RTE-0000000003`, `C-B-1` |
| — | `C-B` full fill | order reaches `FILLED` |
| 9 | `C-B` 100 to XNAS | 🔴 `EMS-RTE-4002` — `order is FILLED` |

## Three properties this case pins

**A refused route consumes no identifier.** Six requests are refused and the route ids still run
`RTE-0000000001`, `-2`, `-3` with no gaps. This is the same property component 3 established for
orders (`aRejectedOrderDoesNotConsumeAnIdentifier`) and it matters for the same reason: if refusals
consumed ids, every identifier in a journal would depend on how many requests failed upstream, and
every case downstream of a refusal would shift. It is also why the ClOrdID collision check runs
**before** an id is drawn — see request 7, which is refused without touching the counter.

**Route ClOrdIDs are numbered per order, not per run.** Request 8 is the ninth route request in the
journal and the first on `C-B`, so it is `C-B-1`, not `C-B-9`. `C-A-2` likewise. The counter is
"routes already hung off this order", which is what a venue reconciling a ClOrdID chain expects.

**An order can leave the routable set.** Request 9 is byte-identical to request 8 except that a fill
has landed in between, and it is refused with `order is FILLED`. Routability is an allowlist —
`NEW`, `REPLACED`, `PARTIALLY_FILLED` — rather than a denylist of terminal states, so a state added
to `order.fsm.yaml` later is un-routable until someone decides otherwise. For a check that governs
whether quantity reaches a venue, that is the safe default.

## Why a refused route emits no FsmTransition

`OrderNew` records its rejection as a real transition (`PENDING_NEW -> REJECTED`). `RouteNew` does
not, and the asymmetry is the schema's rather than a shortcut: `route.fsm.yaml` has exactly one
transition out of `PENDING`, and it is `RouteSent`. There is no `PENDING -> REJECTED`. Emitting one
would mean inventing a transition the schema does not define — and `fsm_coverage.py`, which reads
these events, would then be counting coverage of something that does not exist.

The consequence is worth stating plainly: **a refused route leaves no FSM trace at all**, only a
`RouteRejected`. Both are in this case's `expected.jsonl`, so the difference is visible rather than
inferred.

**Seed:** 0.

**Reviewed against:** `schemas/fsm/route.fsm.yaml`. The single transition asserted here —
`PENDING -RouteSent-> SENT` — is the schema's first, and it is the only one the slice can drive
today.

**Scope, stated plainly:** creation only. Nothing acknowledges, fills, cancels or rejects a route, so
every route in this case ends the run in `SENT` and the other 28 transitions in `route.fsm.yaml` are
unreachable. That is why `fsm_coverage.py` still lists `route` as out of scope with a reason instead
of requiring it — component 6b drives the venue lifecycle, and flipping the machine in-scope while
28/29 of it is unreachable would mean either a failing gate or a coverage exemption that never gets
removed. Quantity on a route the venue later rejects was also still counted as committed at 6a — component
6b fixed that, and [`component-07-route-lifecycle`](../component-07-route-lifecycle/case.md)
re-routes an order whose first route the venue refused.
