---
type: workflow
category: staging
applies_to: ["fx"]
status: draft
tags: [workflow/staging, workflow/fx]
---

# Spot Limit Price

For FX orders (especially swaps and forwards), the **spot limit price** is the executable price on the spot leg, distinct from forward points and overall package price. Orders carry it as a typed field on the FX extension of the staged envelope, and it interacts tightly with value-date conventions and [[spot-first]] sequencing.

## Purpose

Capture an explicit price ceiling/floor on the spot leg in a structured way so the validator, the router, and downstream automation can reason about it cleanly. Avoids the ambiguity of "limit price" on a multi-leg FX trade where overall package economics differ from the spot leg's executable level.

## Trigger / Entry Point

- Set on the FX-extended order envelope during staging.
- Modifiable via [[amend-order]] subject to standard amend rules.
- Default may come from a quote-server reference plus a configured offset (e.g. "mid + 5 ticks") via [[arch-quote-server]].

## Actors

- Trader / sales-trader.
- [[arch-validator]] — checks tick alignment, market-vs-limit sanity, and asset-class-specific bounds.
- [[arch-router-layer]] — propagates the limit to the routed leg(s).
- [[arch-quote-server]] — provides market context for the validator's sanity check.

## Steps (set + validate + propagate)

```mermaid
flowchart LR
  S[Stage FX order with spot_limit_price] --> Q[Validator queries Quote Server for current spot reference]
  Q --> C{Within sanity band<br/>(configurable bps)?}
  C -- no --> R1[EMS-ORD-1040 spot_limit_unrealistic]
  C -- yes --> T[Tick-align check]
  T -- aligned --> P[Persist]
  T -- not aligned --> R2[EMS-ORD-1041 tick_misaligned]
  P --> RT[Route propagates limit to spot leg]
```

## Inputs

- `spot_limit_price: decimal` on the FX extension block of the order envelope.
- Optional `spot_limit_side_offset`: explicit ticks above/below a quote-server reference (resolved at stage time).
- `tick_alignment_policy`: `STRICT` reject or `CLAMP` to nearest tick (firm-policy).

## Outputs / Side Effects

- Persisted on order extension.
- Propagated to per-leg routes when the order is routed.
- For [[arch-multileg|multileg]] swap orders: the spot leg's `limit_price` is set from this; the forward leg's price is derived (spot + forward points).

## Edge Cases & Nuances

- **Tick alignment per pair.** Each currency pair has a tick size (EUR/USD: 0.00001; USD/JPY: 0.001). Misalignment → `EMS-ORD-1041`. `CLAMP` policy quietly aligns; `STRICT` rejects with the suggested aligned value.
- **Sanity band.** Validator rejects limits outside a configurable band vs current market (e.g. ±100 bps). Prevents "fat finger" stops; some firms widen the band for certain asset classes / order kinds.
- **Pair direction.** `spot_limit_price` for buying EUR/USD is the maximum you'll pay (upper bound); for selling, it's the minimum you'll accept (lower bound). Side determines interpretation.
- **Quote reference staleness.** If the sanity check needs a market reference but the quote is stale (> threshold), the validator defers to a fallback reference (last published, EOD reference) and notes the staleness in the persisted order.
- **NDF (non-deliverable forwards).** Spot limit applies to the fixing pre-image, not the settle leg; semantics differ from deliverable forwards.
- **Multi-leg interactions:**
  - **Swap**: spot_limit_price = limit on spot leg. Forward leg's price is `spot_limit + forward_points`, where forward points may be set independently or derived.
  - **NDF**: spot limit governs the reference fixing comparison; settlement is in a different currency.
  - **Outright forward**: there is no "spot leg" in the FX-swap sense; the field is the forward price itself if set on an outright.
- **Validator caches.** Spot reference fetches are cached per (pair, sub-second bucket) to avoid hammering [[arch-quote-server]] on bulk uploads.

## API mapping

Field on FX extension:

```
order.extension.fx.spot_limit_price: decimal
order.extension.fx.spot_limit_side_offset?: int      # ticks vs quote reference
order.extension.fx.tick_alignment_policy?: STRICT | CLAMP
```

## Validator codes touched

`EMS-ORD-1040` (limit outside sanity band), `EMS-ORD-1041` (tick misaligned), `EMS-ORD-1042` (pair tick size unknown), `EMS-REF-3001` (no quote reference available + STRICT policy), `EMS-ORD-1043` (NDF spot semantics — wrong field).

## Permissions

- `#fx-trade` (3-layer per [[arch-tag-permissions]]).
- `#wide-sanity-band` if the user wants to set a wider band override.

## Related

- [[arch-order-staged]] · [[arch-validator]] · [[arch-quote-server]] · [[arch-multileg]] · [[arch-fx-netting]]
- [[effective-date]] · [[expiry-type]] · [[what-are-swaps]] · [[spot-first]]
- [[staging-via-ticket]] · [[staging-via-fix]]
