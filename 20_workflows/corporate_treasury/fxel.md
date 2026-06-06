---
type: workflow
category: corporate_treasury
applies_to: ["fx"]
status: draft
tags: [workflow/corporate_treasury]
---

# FXEL<GO>

FXEL — the corporate-treasury FX entry surface — sits on top of the same EMS API but with a **restricted operation set, sales-trader pairing model, and markup pipeline**. This note captures the EMS architecture's view of the corporate-treasury FX workflow.

## Purpose

Corporate treasurers don't need full trader functionality — they need: enter trade, get a quote, accept or decline, get post-trade confirmation. The sales-trader on the bank side acts as the counterparty, applies a markup, and books the trade. The EMS must support both sides cleanly under one architecture.

## Trigger / Entry Point

- Corporate treasury user enters an order via the FXEL surface (UI screen or API equivalent).
- Sales-trader at the dealer firm sees the order, prices it (often with markup — see [[markup]]), and offers back.
- Treasury accepts → trade booked.

## Actors

- Corporate treasury user (client-side).
- Sales-trader at the dealer (firm-side).
- [[arch-validator]] — enforces the restricted operation set + markup rules.
- [[arch-order-staged|order layer]] — special treasury sub-state machine.

## Restricted operation set

The FXEL surface exposes only a subset of [[arch-api-first|API]] operations:

| Operation | Treasury can do? |
|---|---|
| `stage_orders` (one at a time, simple shape) | Yes |
| `accept_quote` | Yes |
| `reject_quote` | Yes |
| `cancel_orders` (own, before quote) | Yes |
| `amend_orders` | Limited (notes / value_date only; not qty/side) |
| `route_orders` | No (sales-trader drives routing) |
| `bulk_*` | No |
| `bind_rule` (automation) | No |

This is enforced at the validator: tag `#corp-treasury-restricted` carries a deny-by-default for everything not in the list above.

## Steps

```mermaid
sequenceDiagram
  participant CT as Corporate Treasury
  participant API as FXEL Surface (API)
  participant V as Validator
  participant O as Order Layer
  participant ST as Sales-Trader
  participant M as Markup Service
  participant R as Router

  CT->>API: stage_orders(simple FX order)
  API->>V: validate (restricted op set)
  V-->>API: pass
  API->>O: persist (state=AWAITING_QUOTE)
  ST->>API: view order; price internally
  ST->>M: apply_markup(order, bid_price)
  M-->>ST: client_price (= bid + markup)
  ST->>API: offer_quote(order_id, client_price, expire)
  API->>CT: QuoteOffered (push)
  alt accept
    CT->>API: accept_quote(order_id, quote_id)
    API->>O: order state=ACCEPTED
    API->>R: route internally (sales-trader's side hedges)
    R-->>O: fills
    O-->>CT: trade confirmation
  else reject
    CT->>API: reject_quote(order_id, quote_id)
    O-->>API: state=REJECTED_BY_CLIENT
  end
```

## Inputs

- Treasury: simple FX envelope (`ccy_pair`, `side`, `qty`, `value_date`).
- Sales-trader: internal `bid_price` + `markup_bps`.
- Optional: `pre_authorized_counterparties` if treasury wants to restrict to specific dealers.

## Outputs / Side Effects

- Order events as usual plus FXEL-specific `QuoteOffered`, `QuoteAccepted`, `QuoteRejected`.
- Sales-trader's hedge order on the other side (typically internal cross or external route).
- Trade confirmation back to treasury.

## Edge Cases & Nuances

- **Treasury client tries an out-of-scope op.** Validator returns `EMS-PRM-1001 user_missing_tag` (the deny-by-default applies). Treasury UI hides the op anyway.
- **Sales-trader pricing latency.** If pricing takes too long, treasury may cancel. Sales-trader handle `OrderCancelledByClient` event and abort their pricing pipeline.
- **Two-step approval on treasury side.** Treasury firm may require internal approval before sending an order; [[two-step-approval]] applies on treasury side independently.
- **Markup compliance.** Markup must respect firm/region rules (e.g. Dodd-Frank for swap dealers); see [[markup]] for detail.
- **Mid-life amend.** Treasury can edit notes after quote-offered without invalidating the quote; but qty/value_date changes invalidate the offer (sales-trader re-prices).
- **Cross-time-zone close.** Treasury operates US-EU-AS hours; the sales-trader desk may not be open. Orders queue until sales-trader becomes available.
- **Buy-side OMS integration.** Large corporates frequently use a treasury OMS to manage cash-flow and FX hedging order flow; this EMS provides the dealer-side entry surface. See [[buy-side-oms-integration]].

## API mapping

```
operation: stage_orders                 # restricted shape for treasury
operation: offer_quote                  # sales-trader → treasury
items: [{ order_id, price, expire_in }]

operation: accept_quote
items: [{ order_id, quote_id }]

operation: reject_quote
items: [{ order_id, quote_id, reason? }]
```

## Validator codes touched

`EMS-PRM-1001` (op outside restricted set), `EMS-ORD-2501` (cannot amend qty/side after quote), `EMS-ORD-2502` (quote expired), `EMS-RTE-9001` (markup outside permitted band — see [[markup]]).

## Permissions

- `#corp-treasury-client` (3-layer) for treasury users (restricts).
- `#fxel-sales-trader` (3-layer) for the dealer-side sales.
- `#markup-author` (with band limits) for the markup step.

## Related

- [[arch-api-first]] · [[arch-validator]] · [[arch-tag-permissions]] · [[arch-order-staged]] · [[arch-router-layer]]
- [[markup]] · [[staging-on-behalf]] · [[trading-limits]] · [[basic-workflow]] · [[staging-restrictions]]
- [[two-step-approval]] · [[tsox-aim-to-fxem]] · [[allocation-prime-broker]]
