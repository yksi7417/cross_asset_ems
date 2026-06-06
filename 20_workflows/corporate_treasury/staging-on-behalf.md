---
type: workflow
category: corporate_treasury
applies_to: ["fx", "equity", "fixed_income"]
status: draft
tags: [workflow/corporate_treasury, workflow/sales]
---

# Staging on Behalf of

Sales-trader stages an order **on behalf of a corporate-treasury client** (or any client that doesn't have direct API access). The order's audit retains the originating client identity while the operating identity is the sales-trader.

## Purpose

Reflect real-world workflows where a client calls / IBs / emails their request and the dealer's sales-trader keys it in. The order's compliance and reporting must show the **client as the originator**, not the sales-trader, but operations and routing are driven by the sales-trader's permissions.

## Trigger / Entry Point

- Sales-trader opens "Stage on Behalf" ticket and selects the client.
- API: `stage_orders([{...}], options: { on_behalf_of: client_id })`.
- Source documentation reference: voice call ticket, IB chat ID, email message-id, written instruction — captured in `custom_notes.client_request_ref`.

## Actors

- Sales-trader (operator).
- Client (originator, referenced but not present).
- [[arch-validator]] — checks the sales-trader has on-behalf rights for the named client.
- [[arch-event-sourcing|log]] — distinguishes `staged_by` (sales-trader) from `on_behalf_of` (client).

## Steps

```mermaid
sequenceDiagram
  participant C as Client
  participant ST as Sales-Trader
  participant API as API
  participant V as Validator
  participant O as Order Layer

  C->>ST: phone / IB / email: "buy 5M EURUSD spot for me"
  ST->>API: stage_orders(on_behalf_of=C, source_ref=ChatID-abc)
  API->>V: verify ST holds #stage-on-behalf-{client_class} and client is enabled
  V-->>API: pass
  API->>O: persist OrderStaged(staged_by=ST, on_behalf_of=C, source_ref)
  O-->>ST: ack
  Note over O: blotter shows the order under client's account, ST as operator
  Note over O: regulatory reporting cites client; routing per ST permissions
```

1. Sales-trader opens ticket with `on_behalf_of=client_id` set.
2. Validator checks:
   - Sales-trader has `#stage-on-behalf-{client_class}` (3-layer).
   - Client is a recognised, enabled client of the firm.
   - Account picker scoped to the named client's allocation templates.
3. Order persists with both identities recorded.
4. Workflow proceeds normally (routing, allocation), but with the client cited in regulatory reports.

## Inputs

- `on_behalf_of: client_id`.
- `source_ref`: free text but encouraged structured: IB chat ID, voice ticket, email reference.
- Standard staged order envelope.

## Outputs / Side Effects

- `OrderStaged { staged_by, on_behalf_of, source_ref }`.
- Outbound regulatory messages cite `on_behalf_of` in the client identifier field (e.g. LEI).
- Blotter visibility under the client's account.

## Edge Cases & Nuances

- **Client without an LEI or required identifier.** Cannot stage on-behalf; `EMS-ORD-2601 client_missing_required_identifier`.
- **Client-restricted assets.** If client is not enabled for the asset class, validator rejects even though the sales-trader has the permission.
- **Allocation template scope.** The sales-trader sees only the client's allocation templates; their own templates are not pickable.
- **Two-step approval scope.** If the firm requires `two_step_approval` for client-facing trades, the approver must hold `#approve-on-behalf-{client_class}`.
- **Voice-ticket linkage.** Many firms integrate voice-recording systems; the `source_ref` may link to a recording ID for compliance retrieval.
- **Compliance MiFID-style.** Some regimes require the client to have given prior consent for OTC trades; consent metadata can be a required field for affected client classes.
- **Audit retention.** Both identities retained for the order's full retention window.

## API mapping

```
operation: stage_orders
options: {
  on_behalf_of: client_id,
  source_ref: string,                    # voice / IB / email reference
  consent_metadata?: { kind, ack_id }    # for regimes requiring it
}
items: [{ ... }]
```

## Validator codes touched

`EMS-PRM-2001` (`#stage-on-behalf-{class}` missing), `EMS-ORD-2601` (client missing required ID), `EMS-ORD-2602` (consent metadata required), `EMS-ORD-1031` (allocation account not enabled for client).

## Permissions

- `#stage-on-behalf-{client_class}` (3-layer per [[arch-tag-permissions]]).
- `#client-allocation-template-access` for the relevant client.

## Related

- [[fxel]] · [[basic-workflow]] · [[staging-restrictions]] · [[markup]] · [[trading-limits]]
- [[arch-order-staged]] · [[arch-validator]] · [[arch-firm-desk-user]] · [[arch-tag-permissions]]
- [[notes-and-custom-notes]] · [[allocation-prime-broker]] · [[two-step-approval]]
