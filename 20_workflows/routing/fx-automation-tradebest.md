---
type: workflow
category: routing
applies_to: ["fx"]
status: draft
tags: [workflow/routing, workflow/automation, workflow/algo]
---

# FX Automation — TradeBest

TradeBest is a class of FX execution-automation rules that **continuously evaluate quotes** and elect the best executable response (often an RFQ winner or a resting-order reprice) without trader intervention. Several vendor and firm-internal implementations of this pattern exist; the shape captured here is generic.

## Purpose

For high-frequency or numerous-small FX orders, manually electing each RFQ winner is impractical. TradeBest binds the rule into the [[arch-automation-layer]]: when a price meeting the order's policy is observed (RFQ response, resting fill opportunity, or quote-server tick), the rule auto-elects.

## Trigger / Entry Point

- An order staged with `automation_mode = TRADEBEST` (or implicitly via desk-policy auto-binding on stage).
- Triggers: `QuoteResponseReceived` (RFQ response on an outstanding TradeBest route) or `QuoteIncrement` from [[arch-quote-server]] (for resting / reprice scenarios).

## Actors

- Trader / sales who binds the rule (often pre-bound at desk level).
- [[arch-automation-layer]] — rule evaluation.
- [[arch-quote-server]] — trigger source.
- [[arch-router-layer]] — receives the resulting `execute_quote` / `replace_routes` calls.

## Steps

```mermaid
sequenceDiagram
  participant T as Trader / Desk Policy
  participant O as Order Layer
  participant A as Automation (TradeBest rule)
  participant Q as Quote Server
  participant R as Router

  T->>O: stage_orders(automation_mode=TRADEBEST, target_price, max_slippage)
  O->>R: route_orders(mode=MULTI_RFQ or RESTING)
  R-->>Q: subscribe to per-route quote topic
  loop while route alive
    Q-->>A: QuoteResponseReceived / QuoteIncrement
    A->>A: evaluate against target_price + slippage
    alt within tolerance
      A->>R: execute_quote OR replace_routes (reprice closer)
      R-->>O: RouteFilled / RouteReplaced
    else outside tolerance
      A->>A: hold; log Suppressed
    end
  end
```

1. Order staged with TradeBest metadata: `target_price`, `max_slippage_bps`, `aggressiveness_curve`, `expire_at`.
2. Order auto-routed (typically via [[multi-route-rfq]] or [[route-to-resting]]).
3. Rule subscribes to the per-route quote topic via [[arch-quote-server]].
4. Each incoming quote evaluated:
   - If executable price ≤ `target + slippage` → elect / fill.
   - If close but not within tolerance → optionally reprice the resting route towards the market (chase).
   - If far → log suppression, wait for next.
5. On `expire_at` without success → escalate to trader.

## Inputs

- Order's TradeBest parameters: `target_price`, `max_slippage_bps`, `aggressiveness_curve` (how fast to chase), `expire_at`, `escalation_action`.
- Live quotes from [[arch-quote-server]].

## Outputs / Side Effects

- `RuleFired` per election / reprice.
- `RuleSuppressed` per non-actionable quote (useful for post-trade analytics).
- Underlying `RouteFilled` / `RouteReplaced` events.
- On expiry without fill: `OrderEscalated` (e.g. trader notification).

## Aggressiveness curve

A function `f(t)` mapping elapsed time → slippage tolerance:

| Profile | Behaviour |
|---|---|
| `FLAT` | Constant tolerance for the order's life. |
| `LINEAR_RAMP` | Tolerance widens linearly from start to `expire_at`. |
| `STEP` | Discrete steps at e.g. 25/50/75% of window. |
| `MARKET_AWARE` | Tolerance widens faster as spread widens (uses [[arch-quote-server]]). |

The curve is the dominant tuning knob and is captured in the order's TradeBest extension block.

## Edge Cases & Nuances

- **Best quote arrives but venue race.** Two RFQ responses tie. Rule must deterministically tiebreak (first-arrival per stream_seq from [[arch-event-sourcing]]).
- **Crossing target during reprice.** A resting route repriced to chase ends up crossing the spread (becomes aggressive). Policy: continue, or revert to passive — controlled by `crossing_allowed` flag.
- **Stale quotes.** Quote older than `staleness_threshold` not actionable. `EMS-RTE-3003 stale_quote` if elected.
- **Burst suppression storm.** Rapid quote updates trigger many "evaluate but suppress" firings; logged at TRACE level only (per [[arch-jmx-introspection]]) to avoid log spam.
- **Conflict with manual.** Trader can override TradeBest by issuing manual `execute_quote` or cancelling the route. Rule observes the cancel and self-disables for that order.
- **Permission re-eval at firing.** If the rule-binder loses `#tradebest` between trigger and action, the firing is suppressed at the validator — see [[arch-tag-permissions]].
- **Replay determinism.** All decisions are functions of (order params, quote events, clock). With [[arch-time-replay-server|simulated clock]] in replay, decisions reproduce byte-identically.

## API mapping

```
operation: stage_orders
items: [{
  ...,
  extension: {
    automation: {
      mode: TRADEBEST,
      target_price, max_slippage_bps,
      aggressiveness_curve: { kind: LINEAR_RAMP, end: "{expire_at}" },
      expire_at,
      escalation: { kind: NOTIFY_TRADER, audience: "desk_chat" }
    }
  }
}]

# Rule pre-bound at desk level:
operation: bind_rule
items: [{
  rule_id: "tradebest-fx-spot",
  scope: DESK,
  trigger: { event: "QuoteResponseReceived | QuoteIncrement",
             filter: "order.extension.automation.mode == 'TRADEBEST'" },
  condition: "executable_price <= target + slippage(elapsed)",
  action: { op: "execute_quote" or "replace_routes" }
}]
```

## Validator codes touched

`EMS-AUT-2001` (actor lacks `#tradebest`), `EMS-AUT-3001` (rule references unknown target), `EMS-RTE-3003` (stale quote), `EMS-RTE-3008` (replace throttled), `EMS-ORD-3010` (TradeBest window already closed).

## Permissions

- `#tradebest` (3-layer per [[arch-tag-permissions]]).
- `#auto-route-binder` for desk-level pre-binding.
- `#crossing-allowed` if the curve permits crossing.

## Related

- [[arch-automation-layer]] · [[arch-quote-server]] · [[arch-router-layer]] · [[arch-validator]]
- [[route-to-rfq]] · [[multi-route-rfq]] · [[route-to-resting]] · [[auto-route]]
- [[fx-automation-rbld]] · [[auto-route-fixing-aim]]
