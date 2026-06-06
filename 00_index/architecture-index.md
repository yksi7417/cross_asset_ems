# Architecture Index

The architectural spine the workflows and asset notes link back to. Captured in `80_architecture/` as atomic notes.

> Drives the entire EMS: **API-first** with FIX as a subset, batch-by-default, all internal traffic over **SBE on Aeron**, all state as an **append-only event log**, all permissions evaluated via **3-layer tag AND-gate**.

## Surface

- [[arch-api-first]] — batch-by-default API as the single operation surface
- [[arch-fix-api-bridge]] — FIX as a subset; mixed FIX+API client rule
- [[arch-sbe-aeron-transport]] — internal binary protocol on Aeron
- [[arch-sequence-recovery]] — session, sequence numbers, gap detection, heartbeats

## Persistence & Time

- [[arch-event-sourcing]] — append-only log; deterministic state derivation
- [[arch-time-replay-server]] — clock interface; deterministic replay

## OMS Core

- [[arch-order-staged]] — Staged Order Manager (Layer 1)
- [[arch-router-layer]] — Router (Layer 2)
- [[arch-automation-layer]] — rules between order and route

## Market Data

- [[arch-quote-server]] — PubSub with subscriber visibility, even over multicast

## Reference Data

- [[arch-symbology-figi]] — FIGI-first; SEDOL/CUSIP/ISIN as licensed/metered

## Validation & Identity

- [[arch-validator]] — single source of "no" with standardized reject codes
- [[arch-firm-desk-user]] — three-level hierarchy + settings cascade
- [[arch-tag-permissions]] — 3-layer AND-gated permissions

## Connectivity & Ops

- [[arch-venue-connectivity]] — outbound venue adapters (FIX / binary / REST)
- [[arch-jmx-introspection]] — per-component introspection & privileged injection

## Planned (linked but not yet written)

These wikilinks are intentional TODOs — the references show where future architecture work should land.

- `arch-multileg` — atomic multi-leg execution model
- `arch-aggregation` — order aggregation and allocation
- `arch-fx-netting` — full FX netting semantics (swap legs, value dates, prime brokers)
