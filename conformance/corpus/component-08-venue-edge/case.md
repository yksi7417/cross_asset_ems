# component-08-venue-edge

**Covers:** every transition in `schemas/fsm/venue_session.fsm.yaml` — **24 of 24**, verified by
[`fsm_coverage.py`](../../harness/fsm_coverage.py) — plus the two directions of the venue edge: an
outbound `FixOut` when a route is dispatched, and an inbound `ExecutionReport` translated into a
route event.

**Why it exists:** everything before this component assumed a venue would accept whatever it was
sent. The edge is where that assumption gets tested — a session is a state machine that spends most
of its life *not* ready, and an order sent into a half-open session is an order the venue has and
the EMS cannot account for.

## The gate: only `ACTIVE` takes orders

`RouteNew` now checks the venue session **before any order-side check**, because the answer does not
depend on them — and "your order is fine, the venue is down" is more useful to a trader than a
quantity complaint. The last two events in this case show both failure shapes:

```json
{"fields":{"clOrdId":"C-90","code":"EMS-VEN-5001","qty":"100",
 "reason":"venue session is DISCONNECTED","venueMic":"XNAS"},"seq":107,"type":"RouteRejected"}
{"fields":{"clOrdId":"C-90","code":"EMS-VEN-5001","qty":"100",
 "reason":"venue session is never connected","venueMic":"XJPX"},"seq":108,"type":"RouteRejected"}
```

**A venue with no session is not a venue with a broken session.** The gate cannot tell them apart
and should not have to — both refuse. The journal keeps them apart, because "we never connected" and
"we connected and got logged out" are different things to tell an operator. XNAS is `DISCONNECTED`
here because it logged out cleanly at the top of this same journal.

`ACTIVE` only, deliberately. `LOGON_SENT` has a TCP connection and no agreed sequence numbers;
`RESEND_IN_PROGRESS` is mid-gap-fill. Both *look* usable, which is what makes them dangerous.

## Thirteen venues, one per session path

XNAS walks the long happy path — connect, logon, heartbeat, an overdue heartbeat and its test
request, a detected gap and its resend, a sequence reset, and a clean bilateral logout. The other
twelve each exist to reach one transition the happy path cannot:

| Venue | Reaches |
|---|---|
| XLON | `CONNECTING -TcpFailed-> DISCONNECTED` |
| XPAR | `LOGON_SENT -LogonRejected-> DISCONNECTED` |
| XAMS | `TEST_REQUEST_SENT -TestRequestTimeout-> DISCONNECTED` |
| XBRU | `RESEND_IN_PROGRESS -SequenceResetReceived-> SEQUENCE_RESETTING` |
| XMIL | `ACTIVE -LogoutReceived-> LOGOUT_IN_PROGRESS` (the venue initiated it, not us) |
| XMAD, XSWX, XSTO, XHEL, XCSE, XOSL | `UnexpectedDisconnect` from each of the six states that can lose a socket |

Six venues exist only to drop the connection from six different states. That is not padding: losing
the socket mid-resend and losing it mid-logout are different recoveries, and a machine that handled
five of the six would look correct until the sixth happened.

## The inbound edge: `ExecType` is the discriminator

An explicit table, not a name-matching convention — the FIX values are one character and the schema's
event names are not, so there is no derivation to be had, and a wrong guess is a venue message
silently applied to the wrong transition.

| `ExecType` | Route event |
|---|---|
| `0` | `RouteAcknowledged` |
| `4` | `RouteCanceled` |
| `5` | `RouteReplaced` |
| `8` | `RouteRejected` |
| `A` | `RoutePendingNewAtVenue` |
| `C` | `RouteExpired` |
| `E` | `RouteReplacePendingAtVenue` |
| `F` | `RouteFilled` when `OrdStatus=2`, else `RoutePartiallyFilled` |

**`F` is the one that needs two tags.** A trade is the final fill or a partial depending on whether
anything is left, and `ExecType` alone cannot say. Getting this wrong leaves a route in
`PARTIALLY_FILLED` forever, holding quantity that will never be released.

**The report names a route by ClOrdID**, because that is what a venue knows — it has never seen our
route id. The lookup is `routeIdForClOrdId`, and an unrecognised one is `ExecutionReportIgnored`
rather than a crash.

## Translation, not duplication

An `ExecutionReport` is translated into a `RouteEvent` and handed to **the same code path component
6b built**, cascade included. So a venue fill and a hand-written `RouteEvent` cannot drift apart, and
the chain the journal shows is:

```
ExecutionReport(F, OrdStatus=2) → RouteFilled → route PARTIALLY_FILLED→FILLED
                                             → order PARTIALLY_FILLED→FILLED
```

Two FSMs move because of one inbound FIX message, and neither mapping is written in the runner: the
first comes from the `ExecType` table above, the second from the schema's `emit_event` effects.

## Three negatives

- `ExecType=Z` → `ExecutionReportIgnored`, reason `unmapped ExecType`
- a ClOrdID no route holds → `ExecutionReportIgnored`, reason `unknown route ClOrdID`
- a session event name the schema does not define → `VenueSessionEventIgnored`

A venue can send anything, and none of the three is fatal.

## What changed in the earlier cases

`component-06-routing` and `component-07-route-lifecycle` now open a venue session before routing.
Their expectations were regenerated, and the diff is additive: session transitions and `FixOut`
events appear, and every property those cases pinned still holds — six reject codes in 06, route ids
still `RTE-0000000001`…`3` with no gaps, and 29/29 route transitions in 07.

**Seed:** 0.

**Reviewed against:** `schemas/fsm/venue_session.fsm.yaml`.

**Scope, stated plainly:** `FixOut` records that a message was produced and its identifying tags —
it is not a wire-format FIX string. A byte-exact encoder is a component of its own, and recording
intent keeps the conformance gate meaningful without three languages having to agree on tag ordering
and checksums as well. Allocation is component 9.
