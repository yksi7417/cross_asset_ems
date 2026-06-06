---
type: workflow
category: staging
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/staging]
---

# Expiry Type

Orders carry an **expiry policy** that determines when an unfilled order terminates: TIF (Time-In-Force) plus optional explicit `expiry_date` / `expiry_time`. Multiple expiry types exist with different venue translations and asset-class meanings.

## Purpose

Specify the lifetime of the order's executable intent. Mismatched expiry semantics (TIF vs venue-supported TIF, asset-class default vs explicit override) are a frequent source of confusion and rejection.

## Trigger / Entry Point

- Set on the staged order's envelope at staging time.
- Modifiable via [[amend-order]] until the order is fully routed (per per-field policy).
- Default may come from desk-level setting via [[arch-firm-desk-user|settings cascade]].

## Actors

- Trader / staging source.
- [[arch-validator]] — checks TIF/asset-class compatibility, venue support.
- [[arch-router-layer]] — propagates TIF to each route; venue adapter translates to dialect.
- [[arch-time-replay-server|clock]] — drives time-based expiry deterministically.

## TIF taxonomy

| TIF | Meaning | Typical asset class |
|---|---|---|
| `DAY` | Expires at trade-date close (timezone-aware per venue). | Equity, FI, FX (with caveats — see below) |
| `GTC` (Good-Till-Cancelled) | Lives until cancelled, expires, or filled. | Equity (rare for FX/FI). Subject to firm's max-GTC-days policy. |
| `GTD` (Good-Till-Date) | Lives until end of supplied date. | All. |
| `GTT` (Good-Till-Time) | Lives until supplied datetime. | All. Less common. |
| `IOC` (Immediate-Or-Cancel) | Fill what you can immediately; cancel the rest. | Equity, FX. |
| `FOK` (Fill-Or-Kill) | Fill the entire order immediately or cancel. | Equity (block trades). |
| `GFA` (Good-For-Auction) | Active only during auction window. | Equity at open/close auction. |
| `GTX` (Good-Till-Extended) | Lives through extended hours session. | Equity. |
| `OPG` (At-the-Opening) | Participate only in opening auction. | Equity. |
| `CLS` (At-Close) | Participate only in closing auction. | Equity. |
| `FOK_FX` (FX-FOK with value-date) | Asset-class-specific — fill or kill bounded by value date. | FX. |

Each is asset-class-dependent in availability and semantics — see "asset-class semantics" below.

## Steps (set + propagate)

```mermaid
flowchart LR
  S[Stage order with TIF + optional expiry_date / expiry_time]
  S --> V[Validator checks TIF allowed for asset_class<br/>and supported by intended venues]
  V --> O[Order persisted]
  O --> R[Router creates routes]
  R --> A[Adapter translates to venue TIF dialect<br/>e.g. FIX tag 59 enum]
  A --> X[Venue accepts]
  X -. on expiry .-> E[Venue or clock fires expiry]
  E --> O2[OrderExpired event<br/>via [[arch-time-replay-server]]]
```

## Inputs

- `tif`: enum from the TIF taxonomy.
- `expiry_date?`: required for `GTD`.
- `expiry_time?`: required for `GTT`; optional for `GTD` (defaults to end-of-day).
- `expiry_timezone?`: defaults to firm's primary timezone or the venue's local TZ.

## Outputs / Side Effects

- TIF stored on order envelope; copied to each route.
- `OrderExpired` event when the clock fires (no fills before deadline).
- For routes still resting at venues, expiry triggers cancel-on-venue first.

## Edge Cases & Nuances

- **TIF not supported at venue.** Validator returns `EMS-RTE-1013 tif_not_supported_at_venue` at routing time. Cannot be auto-translated (e.g. `GTC` is meaningless for some venues; a downgrade silently to `DAY` would be misleading).
- **Asset-class semantics:**
  - **FX**: `DAY` is venue-defined; an FX order may "expire at NY 5pm" not at midnight. Value-date interacts with TIF — see [[effective-date]].
  - **FI**: `GTC` is unusual; most FI orders are `DAY` or are RFQ-shaped with `expire_in` quote windows.
  - **Equity**: full TIF taxonomy available; auction-specific TIFs (`GFA`, `OPG`, `CLS`) active.
- **Cross-timezone GTD.** A `GTD 2026-06-08` order from a London desk routed to a Tokyo venue — "end of day" interpretation matters. The `expiry_timezone` resolves ambiguity; defaults are firm-policy.
- **Holiday handling.** A `GTD` falling on a venue holiday → expires at end of preceding business day. Some firms prefer the next business day; per-firm setting.
- **GTC max age policy.** Many firms cap GTC at e.g. 90 days. Orders exceeding the cap are stage-rejected (`EMS-ORD-1015 gtc_exceeds_max_age`).
- **Trade-date roll interaction.** `DAY` orders at the [[tradedate-roll|trade-date roll]] either expire and need re-staging or are auto-rolled per firm policy.
- **Clock determinism.** Expiry timing reads through the [[arch-time-replay-server|clock interface]] only; never wall-clock directly. Replay reproduces expiry events identically.
- **Auction TIF + non-auction venue.** `GFA` routed to a venue without an auction → `EMS-RTE-1013`. Some firms accept the route and let the venue downgrade; default policy is reject.

## API mapping

Field-level — no dedicated operation. Used in [[arch-order-staged|staged-order envelope]]:

```
order.tif = "DAY" | "GTC" | "GTD" | "GTT" | "IOC" | "FOK" | "GFA" | "GTX" | "OPG" | "CLS"
order.expiry_date?:     date          # required for GTD
order.expiry_time?:     time          # required for GTT
order.expiry_timezone?: tz_string     # default firm policy
```

## Validator codes touched

`EMS-ORD-1015` (GTC exceeds max age), `EMS-ORD-1016` (TIF/asset-class incompatible), `EMS-ORD-1017` (expiry_date required), `EMS-RTE-1013` (TIF not supported at venue), `EMS-ORD-1018` (expiry in the past).

## Permissions

- `#tif-gtc` for GTC use (some firms restrict).
- `#tif-extended` for extended-hours TIFs.

## Related

- [[arch-order-staged]] · [[arch-validator]] · [[arch-time-replay-server]] · [[arch-router-layer]]
- [[effective-date]] · [[tradedate-roll]] · [[amend-order]] · [[staging-via-ticket]]
- [[route-to-resting]] · [[route-single]]
