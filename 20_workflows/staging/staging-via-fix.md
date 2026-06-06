---
type: workflow
category: staging
applies_to: ["fx", "equity", "fixed_income"]
status: draft
tags: [workflow/staging, workflow/fix]
---

# Staging via FIX — Nuances

Inbound FIX is the predominant institutional entry point for buy-side staging. Sell-side TSOX users, FXEM clients, and many EMSX equity flows route through a FIX session before any human in the EMS sees the order. This note captures how the FIX wire decodes into the cross-asset API, and what the FIX-paired-with-API rule (see [[arch-fix-api-bridge]]) implies for handling.

## Purpose

Translate FIX `NewOrderSingle` / `NewOrderList` / `NewOrderMultileg` (and their amend/cancel cousins) into the canonical `stage_orders` API call without forking validator or audit logic, and mirror downstream state back to the FIX client as `ExecutionReport` / `BusinessMessageReject`.

## Trigger / Entry Point

A FIX session at the edge ([[arch-fix-api-bridge]]) receives one of:

| Inbound | Becomes |
|---|---|
| `D` NewOrderSingle | `stage_orders([order])` — batch size 1 |
| `E` NewOrderList | `stage_orders([order, order, ...])` |
| `AB` NewOrderMultileg | `stage_orders` with multileg envelope (see planned `arch-multileg`) |
| `G` OrderCancelReplace | `amend_orders([{order_id, fields}])` |
| `F` OrderCancelRequest | `cancel_orders([order_id])` |

The bridge resolves `11`/`41` (`ClOrdID` / `OrigClOrdID`) against per-session ID maps to produce the EMS-internal `order_id`.

## Actors

- Buy-side OMS / TSOX-equivalent — sender.
- Sales-trader / execution trader at the receiving firm — operator.
- The [[arch-validator|validator]] — gatekeeper.
- The [[arch-order-staged|staged order manager]] — owner of state.
- Any [[arch-automation-layer|automation rules]] subscribed to `OrderStaged`.

## Steps (canonical flow)

1. **Session intake.** Edge session has already authenticated and is sequence-tracked ([[arch-sequence-recovery]]). Each inbound has `34` MsgSeqNum and is checked for gaps.
2. **Decode.** FIX-to-API translation per the [[arch-fix-api-bridge|tag-mapping table]]. Asset-class-specific extension fields are read from FIXML extensions or convention-bound custom tags.
3. **Symbology resolve.** Tags `55` Symbol / `48` SecurityID / `22` SecurityIDSource are resolved to a FIGI via [[arch-symbology-figi]]. If the client sent CUSIP/SEDOL/ISIN, license metering fires.
4. **Build SBE envelope.** Compose `stage_orders` request with `source = FIX`, `request_id` = UUID derived from `(SenderCompID, ClOrdID)`, `client_seq` from session.
5. **Validate.** Layered validation per [[arch-validator]] — session → identity → reference → permission → asset-class → limits → market → route-readiness.
6. **Persist.** On `ACCEPTED`, the order enters `STAGED` state — see [[arch-order-staged]]. Events `OrderStaged` / `ValidationPassed` are appended to [[arch-event-sourcing|the log]].
7. **Acknowledge to FIX client.** `ExecutionReport` with `150=A` (Pending New) and `39=A`, followed by `150=0` (New) on the second event.
8. **Subsequent edits.** Per the [[arch-fix-api-bridge|mixed-client rule]], if the same client has an API session attached to the same `SenderCompID`, the FIX client may **not** amend or cancel via FIX — but human edits over API propagate back to the FIX client as `ExecutionReport`.

## Inputs (key FIX tags)

| Tag | Field | Maps to |
|---|---|---|
| 11 | ClOrdID | `request_id` derivation |
| 21 | HandlInst | Suggestion only; firm policy may override |
| 38 | OrderQty | `quantity` |
| 40 | OrdType | `ord_type` (MARKET, LIMIT, …) |
| 44 | Price | `limit_price` |
| 54 | Side | `side` |
| 55/48/22 | Symbol / SecurityID / IDSource | `instrument` via [[arch-symbology-figi]] |
| 59 | TimeInForce | `tif` |
| 60 | TransactTime | recorded; `recorded_at` from [[arch-time-replay-server]] |
| 432 | ExpireDate | `expiry_date` |
| 65 | SymbolSfx | preferred-share subclass handling |
| 167 | SecurityType | drives extension dispatch (FXFWD, FUT, OPT, REPO, …) |
| 263/264/265 | Subscription/MDReqID (only for market data sessions) | n/a here |
| 78/79/80/467 | NoAllocs / AllocAccount / AllocQty / IndividualAllocID | `allocation_template` |
| Custom (firm-defined) | BatchName, GroupID | distinct staging metadata, see [[batchname-column]] and [[group-id]] |

## Outputs / Side Effects

- `OrderStaged` event ([[arch-event-sourcing]]).
- `ExecutionReport` `150=A` then `150=0` to FIX client.
- Possible `RuleFired` events if [[arch-automation-layer]] subscribers match.
- If `auto_route_to_rfq` is enabled at the desk level, a downstream `route_orders` is dispatched without human action — see [[auto-route]].

## Edge Cases & Nuances

- **Mixed FIX+API client.** Only **stage** via FIX. Any FIX `G`/`F` from such a client returns `BusinessMessageReject` (`35=j`) with `EMS-PRM-1101 amend_via_fix_not_allowed_for_mixed_client`. See [[arch-fix-api-bridge]].
- **Custom tags vs FIXML.** Asset-class extensions arrive as custom tags (e.g. `9300+` range) or `XML` blob in `213`. Either is decoded; the bridge cannot rely on both being available.
- **Sequence gap on session.** A gap pauses inbound processing for that session until the resend completes — see [[arch-sequence-recovery]]. Orders inside the gap are not silently dropped; they replay on resend.
- **Duplicate `ClOrdID`.** Same `(SenderCompID, ClOrdID)` within the per-day window → request_id collision → idempotent replay of the prior response. Cross-day reuse is an `EMS-SES-1004 duplicate_request_id` reject.
- **Multi-leg legs reordering.** Some sender OMSs reorder legs across resends; the bridge must hash legs canonically before idempotency check.
- **TIF + asset class.** FIX TIF values map ambiguously to FX (where `value_date` interacts with TIF). Asset-class-specific defaults documented per-instrument in the extension block.
- **Allocation drift.** `AllocAccount` IDs may not match the EMS's internal account table; on mismatch the order enters `STAGED` with `pending_actions: [NeedAllocationMapping]` and a clear admin hint for the desk.
- **Net-by-Excel semantics in FIX.** A `35=E` with `58=Netted` arriving from an Excel-driven staging tool ([[staging-via-excel]]) is a hint, not a binding instruction; netting is re-evaluated by [[netting-swap-net]] under firm policy.

## API mapping

```
operation: stage_orders
items: [{
  origin: FIX,
  fix_session_id, fix_seq_num, fix_clord_id,
  instrument: { figi, share_class_figi?, sedol/cusip/isin? (licensed) },
  side, quantity, tif, limit_price?, effective_date?, expiry_date?,
  account, allocation_template?,
  extension: { asset_class_specific_block }
}]
```

## Validator codes touched

`EMS-SES-1004` (duplicate seq/id), `EMS-REF-1001` (license_denied), `EMS-REF-2001` (unknown FIGI), `EMS-PRM-1001..1003` (3-layer tag), `EMS-PRM-1101` (amend_via_fix_not_allowed), `EMS-ORD-1014` (missing limit on LIMIT order), `EMS-ORD-2003` (qty exceeds desk cap).

## Permissions

- Sender's `SenderCompID` must map to a known `(firm, desk)` registration.
- The mapped identity must hold `#trade-{asset_class}` evaluated through the [[arch-tag-permissions|3-layer AND-gate]].
- Allocation template usage requires the per-template tag (e.g. `#alloc-prime-broker-{pb_id}`).

## Related

- [[arch-fix-api-bridge]] · [[arch-api-first]] · [[arch-sequence-recovery]] · [[arch-order-staged]] · [[arch-validator]] · [[arch-symbology-figi]]
- [[staging-via-ticket]] · [[staging-via-excel]] · [[amend-order]] · [[allocation-prime-broker]] · [[batchname-column]] · [[group-id]] · [[netting-auto-via-excel]]
- [[auto-route]] · [[two-step-approval]]
