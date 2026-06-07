# Knowledge Base Guide

The design vault — the *what* and *why* of the cross-asset EMS. The companion to [`DEVELOPMENT.md`](DEVELOPMENT.md), which covers the *how* of the running code.

## What's in the knowledge base

A cross-referenced wiki of how institutional execution management works, written as **atomic notes** (one concept per file) linked by Obsidian-compatible `[[wikilinks]]`. Frontmatter and tags make it queryable; markdown means it renders in any viewer.

| Folder | What |
|---|---|
| [`00_index/`](00_index/) | HOME, architecture index, asset-class matrix, workflow index, venue index, glossary index, runbook |
| [`10_asset_classes/`](10_asset_classes/) | One note per sub-asset class (25+ classes) with venues, RFQ flow, netting, regulatory reporting, clearing, documentation, market notes, counterparties, fungibility characterisation |
| [`20_workflows/`](20_workflows/) | Staging, pre-trade, routing, corporate-treasury, others — every workflow as an atomic note |
| [`30_venues/`](30_venues/) | Categorized by asset class: `fixed_income/`, `fx/`, `equity/`, `brokers/`, `multi_asset/`. Each note covers asset classes, workflow mechanisms, connectivity, key facts. **Brokers** has a dual-role disclaimer — the same firms are FI dealers and FX LPs |
| [`40_regulatory/`](40_regulatory/) | TRACE, MSRB RTRS, CFTC SDR, FINRA, FICC, SEF reporting, Fed reporting, FDIC/OCC, DTCC SDR — scope, fields, timing, EMS touchpoints |
| [`50_clearing_settlement/`](50_clearing_settlement/) | DTC, Fedwire, Euroclear, Clearstream, LCH, CME, ICE, FICC, MarkitSERV, tri-party — settlement cycles, membership, EMS integration |
| [`60_documentation/`](60_documentation/) | ISDA Master, CSA, GMRA, DVP conventions, PSA, SIFMA TBA Good Delivery, CDS Annex, loan agreements, convertible indentures |
| [`70_concepts/`](70_concepts/) | Glossary (atomic concept notes) — see below |
| [`80_architecture/`](80_architecture/) | The system architecture — 50+ notes spanning every layer |

## Where to start reading

For different audiences:

### "I want the 30-second pitch"
Read [`README.md`](README.md). Vision, current state, get-started recipe.

### "I'm a finance person new to EMS engineering"
1. [`00_index/HOME.md`](00_index/HOME.md) — entry point.
2. [`70_concepts/glossary-index.md`](70_concepts/glossary-index.md) — translate any term you don't recognise.
3. [`10_asset_classes/`](10_asset_classes/) — one note per asset class.
4. [`30_venues/_venue-index.md`](30_venues/_venue-index.md) — where execution happens.
5. [`20_workflows/`](20_workflows/) — how a trade flows through the system.

### "I'm an engineer new to capital markets"
1. [`70_concepts/glossary-index.md`](70_concepts/glossary-index.md) — read the glossary first.
2. [`00_index/asset-class-matrix.md`](00_index/asset-class-matrix.md) — cross-asset comparison in one table.
3. [`00_index/architecture-index.md`](00_index/architecture-index.md) — system design entry point.
4. [`80_architecture/arch-api-first.md`](80_architecture/arch-api-first.md) — the API surface.
5. [`80_architecture/arch-sbe-aeron-transport.md`](80_architecture/arch-sbe-aeron-transport.md) — wire protocol.
6. [`80_architecture/arch-fix-fsm-design.md`](80_architecture/arch-fix-fsm-design.md) — the FSM that ties it together.

### "I want to understand a specific concept"
- **A specific term** → search `70_concepts/glossary/` or hover any wikilink in Obsidian.
- **A specific asset class** → `10_asset_classes/<class>/<sub-class>.md`.
- **A specific venue** → `30_venues/<category>/<venue>.md`.
- **A specific architecture decision** → search `80_architecture/`.

## Glossary highlights

50+ industry-jargon entries split into seven domains:

- **Fixed income** — on-the-run vs off-the-run, BWIC/OWIC, TBA vs specified pool, dollar roll, WAC/WAM/WALA, axe, composite price (CBBT/BVAL), clean vs dirty price, accrued interest, DV01 and duration, two-way vs one-way markets, portfolio trading.
- **FX** — spot date / value date, forward points / swap points, FX fixing, last-look.
- **Equity** — NBBO/EBBO, Reg NMS, lit vs dark, ATS/ECN/MTF, systematic internaliser, midpoint cross, closing auction, capital commitment, central risk book, authorized participant, iNAV, VWAP/TWAP/POV/IS.
- **Derivatives** — CCP vs bilateral, novation, FCM, MAT (Made-Available-to-Trade), RFQ-to-3, give-up, USI/UTI.
- **Execution lifecycle** — fungible vs non-fungible, FIX protocol basics, ClOrdID/OrigClOrdID, OrdStatus/ExecType, Pending Replace/Pending Cancel, CLOB vs RFQ, RFQ, RFQ/RFS/RFM, IOI vs RFQ, all-to-all, agency vs principal, netting.
- **Regulatory** — TRACE, MSRB RTRS, CFTC SDR, FINRA CAT, RTS 22/27/28, MAR/STOR, EMIR/SFTR/CSDR, LEI.
- **Settlement** — T+1/T+2, DVP/RVP/FOP, allocation/affirmation/confirmation, tri-party vs bilateral repo, GCF repo.

Plus a **Terminal screens** group ([`70_concepts/terminal_screens/`](70_concepts/terminal_screens/)) covering Bloomberg ALLQ/BTMM/FIT/CDSW/SWPM/CBND/CP-CD/TBILL/REPO — these are **monitor screens, not routable venues**. Each note says where execution actually happens.

## Architecture spine

The system architecture is split across [`80_architecture/`](80_architecture/) — index at [`00_index/architecture-index.md`](00_index/architecture-index.md). Highlights:

- **Surface**: `arch-api-first`, `arch-fix-api-bridge`, `arch-sbe-aeron-transport`, `arch-sequence-recovery`
- **Persistence + time**: `arch-event-sourcing`, `arch-time-replay-server`
- **Observability + continuity**: `arch-observability`, `arch-identity-chaining`, `arch-resilience-24x7`
- **Methodology**: `arch-ddd-tdd`, `arch-deployment`
- **OMS core**: `arch-order-staged`, `arch-router-layer`, `arch-automation-layer`
- **Order model extensions**: `arch-multileg`, `arch-aggregation`, `arch-fx-netting`, `arch-order-route-lifecycle`, `arch-fix-appendix-d`, `arch-fix-fsm-design`
- **Market data**: `arch-quote-server`
- **Reference data**: `arch-symbology-figi`, `arch-security-master`, `arch-reference-data-service`
- **Validation + identity**: `arch-validator`, `arch-firm-desk-user`, `arch-tag-permissions`
- **Pre-trade auxiliaries**: `arch-compliance`, `arch-risk-engine`, `arch-position-service`, `arch-surveillance`
- **Analytics**: `arch-realtime-analytics`, `arch-pretrade-analytics`, `arch-tca`
- **Market intelligence**: `arch-ioi`, `arch-rfq`
- **Bulk I/O**: `arch-bulk-io`
- **Post-trade pipeline**: `arch-stp-pipeline`, `arch-allocation-service`, `arch-confirmation-affirmation`, `arch-regulatory-reporting-service`
- **Compliance regimes**: `arch-jurisdictional-compliance`, `arch-best-execution`
- **Foundational services**: `arch-projection-engine`, `arch-pricing-service`, `arch-notification-service`, `arch-corporate-actions`, `arch-borrow-service`, `arch-configuration-service`
- **Connectivity + ops**: `arch-venue-connectivity`, `arch-smart-order-router`, `arch-jmx-introspection`

## Vault conventions

- **Atomic notes** — one concept per file. Don't bundle.
- **Wikilinks resolve by basename** — file names must be unique vault-wide. Prefix when needed (`bloomberg-fit.md`, not `fit.md`).
- **Frontmatter is source of truth** for `type`, `status`, `tags`. Once Dataview is installed you can query e.g. all `status: stub` workflow notes.
- **Status workflow** — `stub` → `draft` → `reviewed`. As of the current commit, all asset-class / regulatory / clearing / documentation notes are `draft`.
- **Tags are hierarchical** — `asset/fixed_income/corp_ig`, `workflow/routing`, `venue/dealer_platform`.

The full vault runbook is in [`00_index/USAGE.md`](00_index/USAGE.md).

## Opening the vault in Obsidian (recommended)

For navigation, hover-preview, and the graph view:

```bash
flatpak install --user -y flathub md.obsidian.Obsidian
flatpak run md.obsidian.Obsidian
```

Then **Open folder as vault** → point at this repository. Obsidian picks up the committed `.obsidian/` config (wikilinks on, attachments folder set, core plugins enabled).

Useful hotkeys:
- `Ctrl+O` — Quick Switcher (open any note by name).
- `Ctrl+Shift+F` — global search.
- `Ctrl+Shift+B` — backlinks panel.
- `Ctrl+G` — global graph view.
- `Ctrl+Shift+G` — local graph (this note's neighbours).

## How the design ties into the code

Every architecture note has a one-to-one or one-to-few mapping to a Java module / C++ module. See [`DEVELOPMENT.md`](DEVELOPMENT.md#where-each-part-of-the-design-lives-in-code) for the full table.

For any task in [`IMPL/PLAN.md`](IMPL/PLAN.md), the task description usually wikilinks the architecture note it implements — that note is the design spec the task delivers against.

## Adding a new note

See [`00_index/USAGE.md`](00_index/USAGE.md) §4 ("Authoring — add a new note"). Templates for every note type live under [`templates/`](templates/).

When you add a new architecture note specifically, also update [`00_index/architecture-index.md`](00_index/architecture-index.md) so the index stays current.

## See also

- [`README.md`](README.md) — project vision
- [`DEVELOPMENT.md`](DEVELOPMENT.md) — build/test/deploy
- [`00_index/HOME.md`](00_index/HOME.md) — design vault entry
- [`00_index/USAGE.md`](00_index/USAGE.md) — vault runbook (install / navigate / author)
- [`IMPL/PLAN.md`](IMPL/PLAN.md) — implementation task queue
