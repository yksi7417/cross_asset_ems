---
type: architecture
layer: oms
status: draft
tags: [architecture/oms]
---

# Automation Layer

Between the [[arch-order-staged|staged order]] and the [[arch-router-layer|router]] sits an optional **automation layer**. It evaluates rules and, when conditions match, calls the **same API operations** a human would. The downstream router cannot tell automation traffic apart at the schema level — only via the `source` metadata in the [[arch-sbe-aeron-transport|envelope]].

## Path shapes

Both shapes are valid and the protocol is identical:

```
[A]  Order ──► Automation (no-op / bypass) ──► Router
[B]  Order ────────────────────────────────► Router        (direct)
```

`[A]` with a no-op rule is equivalent to `[B]`. The decision is **per-order or per-firm policy**, not a code path. Adding or removing automation does not require changing the schema, the validator, or the router.

## Rule model

```
Rule {
  rule_id        UUID
  scope          FIRM | DESK | USER | TAG    // see [[arch-firm-desk-user]] and [[arch-tag-permissions]]
  scope_ref      string                       // e.g. firm_id, desk_id, user_id, tag
  trigger        EventPattern                 // event stream + filter
  condition      Expression                   // boolean over event + context
  action         ActionTemplate               // operation + parameter binding
  priority       int
  enabled        bool
  active_window  TimeWindow?                  // e.g. only RTH, see [[arch-time-replay-server]]
}
```

### Triggers (examples)

- `OrderAccepted` (a.k.a. `OrderStaged`) where `asset_class = FX` and `value_date = next_fixing()` → auto-route fixing orders. See [[auto-route-fixing-aim]].
- `OrderAccepted` where `tags ∋ #rebalance` → run [[fx-automation-rbld|RBLD]].
- `OrderReplaced` where `prior.tif = DAY` and `new.tif = GTC` → cancel auto-route policy. (FIX-equivalent: `35=8 ExecType=5 Replaced`. See [[arch-order-route-lifecycle]].)
- `QuoteIncrement` where `spread <= floor` and any matching staged order exists → fire [[fx-automation-tradebest|TradeBest]].

### Actions (examples)

- `route_orders([{order_id, venue: BBG_RFQ, ...}])`
- `set_pending_action_done([order_id, action])`
- `bind_rule([...])` (rule that binds a child rule on the fly)
- `cancel_routes([route_id])`

## Determinism and audit

Every firing emits an event into [[arch-event-sourcing]]:

```
RuleFired {
  rule_id, order_id, triggered_by_event_id,
  decision: ACTION_EXECUTED | SUPPRESSED | DEFERRED,
  reason: string,                  // if suppressed
  resulting_request_id: UUID       // ties to the API call the action issued
}
```

A replay of the same event sequence with the same rule definitions must produce the same firings. Non-deterministic dependencies (clock, random) go through the [[arch-time-replay-server|clock interface]].

## Suppression and conflict

Multiple rules can match. Resolution is **priority-ordered**, with explicit `suppresses` declarations between rules:

```
TradeBest priority 100 suppresses RBLD priority 50 when scope = same order
```

Suppressions are themselves events — `RuleSuppressed { rule_id, suppressed_by, order_id }`.

## Permissions

A rule firing inherits an `actor` equal to the rule owner (the user or service who bound it). The downstream [[arch-validator|validator]] evaluates [[arch-tag-permissions]] using this actor — so a rule bound by a user without the required tag is **suppressed at evaluation**, with a clear reason emitted to the event log:

> `Rule {id} suppressed: actor lacks tag #algo-trader (admin: jane.doe). RuleFired event recorded with status=SUPPRESSED.`

## Example automations (workflow links)

- [[fx-automation-tradebest]] — best-quote chasing
- [[fx-automation-rbld]] — rebalancing routing
- [[auto-route]] — generic auto-routing to RFQ / FXOM / CNF
- [[auto-route-fixing-aim]] — fixing-orders auto-route from AIM
- [[spot-first]] — route spot leg before forward leg

## Out of scope

- The rule **authoring** UI/DSL. Persisted as `Rule` envelopes via the API.
- Backtesting of rules. Done by [[arch-time-replay-server|replay]] over historical event logs.
- Multi-step ML decisions. The action template can call into a scoring service, but the scoring service decision is a **dependency** the rule references, not part of the rule.

## See also

- [[arch-order-staged]]
- [[arch-router-layer]]
- [[arch-validator]]
- [[arch-tag-permissions]]
- [[arch-event-sourcing]]
