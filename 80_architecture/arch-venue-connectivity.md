---
type: architecture
layer: connectivity
status: draft
tags: [architecture/connectivity]
---

# Venue Connectivity Layer

The outbound boundary between the [[arch-router-layer|router]] and the outside world. One **adapter** per venue, isolating per-venue dialects (FIX, binary, REST) behind a single internal protocol.

## Adapter responsibilities

- Translate router-emitted `Route` envelopes (SBE) to the venue's wire format.
- Maintain the venue session (logon, heartbeat, sequence reset) at the venue's protocol level — independent of the [[arch-sequence-recovery|internal session layer]].
- Surface venue-side state changes (acks, fills, rejects) back to the router as SBE events.
- Enforce venue-specific [[arch-validator|validator rules]] (e.g. tick size, lot, min notional) at the boundary.
- Expose health via [[arch-jmx-introspection]].

## Adapter types

| Type | Examples |
|---|---|
| **FIX** | MarketAxess (corp bonds), FXConnect, BBG ALLQ over FIX, ICE Credit, some SEFs |
| **Binary / proprietary** | EBS, FX Spot+, exchange native binary feeds, BrokerTec |
| **REST** | Some venue's RFQ submission, OpenFIGI resolution, TreasuryDirect issuance, triparty management portals |
| **Bloomberg API** | Routes via BBG screens (BWIC, OWIC, FIT, SWPM, CDSW, TBA) — backed by BLPAPI |

References (public): [Bloomberg Electronic Venues](https://professional.bloomberg.com/products/trading/electronic-markets/venues/), [FXConnect](https://www.fxconnect.com/), [MarketAxess](https://www.marketaxess.com/).

## Adapter envelope (router-facing)

```
VenueAdapter {
  venue_ref:     VenueRef                 // unique per venue connection
  dialect:       FIX|BIN|REST|BLPAPI
  capabilities:  set<Capability>          // e.g. SUPPORTS_RFQ, SUPPORTS_LIMIT, SUPPORTS_HIDDEN
  state:         CONNECTED|RECONNECTING|DISABLED
  in_flight:     count<route_id>
}
```

The router selects an adapter based on `(figi, asset_class, instruction)` and the firm's enabled-venue list (which is a [[arch-firm-desk-user|firm-level]] setting, refinable at desk).

## Shadow mode

For replay and integration testing, adapters run in **shadow mode**:

- All outbound traffic is discarded or sent to a sandbox.
- Inbound is taken from the replay log instead of the venue.
- The router cannot tell the difference at the schema level.

This makes [[arch-time-replay-server|replay]] deterministic and prevents accidental routing during regression runs.

## Per-venue idiosyncrasies (cataloguing notes)

Per-venue notes live in `30_venues/` and are linked from each adapter. Examples of fields a venue note should capture:

- ClOrdID rules (per-day reset? per-session reset?).
- Replace semantics (in-flight quantity behavior).
- TIF dialect (does `GTD` need a venue-local timezone?).
- Tick rules (price increments, conditional ticks).
- Reject code mapping into the EMS [[arch-validator|validator code namespace]].
- Allocation methods supported.

## Capability negotiation

Adapters publish a `Capability` set at startup. The router consults this when deciding whether to allow `route_orders` for a (venue, instrument, instruction) tuple. Mismatch → `EMS-RTE-1003 capability_unsupported`.

## Failure modes

- **Venue disconnect** — adapter goes `RECONNECTING`. In-flight routes are marked `UNCERTAIN` (not auto-cancelled). Recovery flow reconciles state on reconnect.
- **Adapter crash** — supervisor restarts. State is rebuilt from the [[arch-event-sourcing|event log]].
- **Venue-side reject with unmappable code** — translated to `EMS-RTE-9999 venue_unmapped_reject` with the original code preserved in metadata. Ops triage and add a mapping.

## SOR as a virtual venue

[[arch-smart-order-router|Smart Order Routing]] is implemented as a special venue adapter from the router's perspective. SOR receives a parent route and **internally fans out to N child routes** to real venues, each managed via its own concrete adapter from this layer. This keeps the router-side surface uniform: route-to-MarketAxess and route-to-SOR_EQ_US use the same envelope. SOR's plug-and-play property is the entire reason it's a venue, not a router modification.

## See also

- [[arch-router-layer]] · [[arch-smart-order-router]]
- [[arch-order-route-lifecycle]] · [[arch-fix-appendix-d]] · [[arch-fix-fsm-design]]
- [[arch-validator]] · [[arch-sbe-aeron-transport]] · [[arch-event-sourcing]] · [[arch-time-replay-server]] · [[arch-jmx-introspection]]
