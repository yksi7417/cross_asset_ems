---
type: architecture
layer: reference_data
status: draft
tags: [architecture/reference_data, architecture/data_model]
---

# Security Master — Data Model for Cross-Asset Instruments

The Security Master Service answers one question: **what is this instrument?** It is the canonical, replay-deterministic source of truth for everything an EMS needs to know about an instrument *before* deciding to trade it: identifiers, lifecycle status, settlement conventions, structural attributes, and per-asset-class economics.

This note covers the **data model and SBE schema shape**. The licensing / identifier-metering discipline is in [[arch-symbology-figi]]; the broader reference data registry pattern is in [[arch-reference-data-service]]; corporate-action-driven lifecycle is in [[arch-corporate-actions]].

## What's IN security master, what's NOT

**IN:** what the instrument *is*.

- Identifiers (FIGI, licensed secondaries, internal-allocated for OTC).
- Lifecycle status, effective-date ranges, supersession history.
- Structural attributes (coupon, maturity, strike, conversion ratio, etc.).
- Settlement / market microstructure conventions (T+N, tick size, day count).
- References to related entities (LEI of issuer, underlying instrument, reference index).

**NOT IN:** what's *happening* to/with the instrument.

| Concern | Lives in |
|---|---|
| Current price / mid / iNAV | [[arch-pricing-service]] / [[arch-quote-server]] |
| Position holdings | [[arch-position-service]] |
| Curves / vol surfaces (values) | [[arch-pricing-service]] |
| Orders / IOIs / quotes | their respective services |
| Borrow / locate status | [[arch-borrow-service]] |
| Risk metrics, sensitivities | [[arch-risk-engine]] |
| Trade fills | [[arch-event-sourcing|event log]] |

The scope boundary is **enforced**: security master records do not contain prices, positions, or any transactional state. The service refuses writes that try to put them there. This prevents the master from drifting into a god-object.

## The core constraint

A cross-asset EMS must:

1. **Treat instruments generically** for routing, validation, audit, [[arch-identity-chaining|identity chaining]], FIX bridge translation — these never care that an instrument is an IRS vs equity.
2. **Treat instruments specifically** for pricing, risk, settlement instructions, regulatory reporting, compliance — these absolutely care, and the per-class details determine correctness.
3. **Replay deterministically** — every event in the [[arch-event-sourcing|log]] resolves the instrument-as-it-was-at-event-time, even if the instrument has since been split, renamed, or matured.

The data model needs all three properties simultaneously. The pattern that delivers them is **one SBE template per asset class with a shared core block**, plus **package as a separate entity**, plus **composition by ID only**.

## Layered identifier model

The instrument's primary key is the **FIGI** ([[arch-symbology-figi|see symbology note]] for the why). Three FIGI layers track the hierarchy:

- `composite_figi` — country-level composite (e.g. one per Apple regardless of listing).
- `share_class_figi` — share class across venues (one per Apple common stock).
- `figi` — instrument on a specific venue (one per Apple on NYSE, one per Apple on FRA, etc.).

Plus **licensed secondary identifiers** with access metering:

- `isin`, `cusip`, `sedol`, `ric`, `bbg_ticker`, exchange-local codes.
- Per-identifier license check + meter at access time ([[arch-symbology-figi]]).

Plus **internal-allocated identifiers** for instruments without a public FIGI:

- OTC bilateral derivatives (bespoke IRS, single-name CDS variants, exotic options).
- Internally-issued ID space: `ems_iid:{firm}:{counter}` — unique within the firm; never collides with FIGI; carries the same lifecycle and supersession semantics.

The internal-allocated namespace is necessary for the OTC / bespoke universe. Without it, the master can't represent every instrument a buy-side firm will trade.

## SBE schema shape — one template per asset class

**The fundamental schema decision: one SBE `template_id` per asset class**, each carrying a common `InstrumentCore` block at a fixed offset, then asset-class-specific fields.

```
EquityInstrument         template_id=0x2001
BondInstrument           template_id=0x2002
ConvertibleBond          template_id=0x2003
LoanInstrument           template_id=0x2004
TbaMbsInstrument         template_id=0x2010
SpecifiedPoolInstrument  template_id=0x2011
AbsInstrument            template_id=0x2012
EtfInstrument            template_id=0x2020
ListedOptionInstrument   template_id=0x2030
ListedFutureInstrument   template_id=0x2031
FxSpotInstrument         template_id=0x2040
FxForwardInstrument      template_id=0x2041
FxSwapInstrument         template_id=0x2042
FxNdfInstrument          template_id=0x2043
FxOptionInstrument       template_id=0x2044
IrsInstrument            template_id=0x2050
CdsInstrument            template_id=0x2051
StructuredProductInstrument template_id=0x2060
CommodityFutureInstrument   template_id=0x2070
CommodityPhysicalInstrument template_id=0x2071
CryptoFungibleInstrument    template_id=0x2080
NftInstrument               template_id=0x2081
EventContractInstrument     template_id=0x2090
```

Each template starts with the same `InstrumentCore` block.

### InstrumentCore (fixed at offset 0 of every Instrument template)

```
InstrumentCore {
  figi               char[12]      // primary key; empty if internal-allocated
  internal_iid       char[20]      // populated for OTC; empty if FIGI-only
  composite_figi     char[12]?
  share_class_figi   char[12]?
  asset_class        AssetClass enum
  instrument_type    InstrumentType enum  // sub-category within asset_class

  display_name       char[64]
  legal_name         char[128]
  issuer_lei         char[20]?     // see [[lei]]

  currency           Currency enum
  country_of_issue   ISO3166
  country_of_listing ISO3166?      // empty for OTC

  fungibility        Fungibility enum  // FUNGIBLE | NON_FUNGIBLE | TBA_LIKE
  settlement_convention SettlementConv enum  // T+0 | T+1 | T+2 | TBA_MONTHLY | PER_CCP | ...
  tick_size_regime_ref  uint16    // index into RefData tick-size table

  lifecycle_status   LifecycleStatus enum  // ACTIVE | SUSPENDED | EXPIRED | MATURED | DEFAULTED
  effective_from     timestamp     // see Supersession section
  effective_to       timestamp     // OPEN if currently active
  version_seq        uint32        // bumped on every supersession
  superseded_by      char[12]?     // FIGI of the next version, if any

  created_at         timestamp
  last_amended_at    timestamp
}
```

Any consumer that needs only "what is this thing, in what currency, what's its lifecycle status, is it tradeable today" reads the core block at fixed offset and **never looks at the template_id**.

Consumers that need specialized behaviour dispatch on `template_id`, decode the rest of the message per that template's schema, and proceed.

### Per-asset-class trailer

Each `template_id` defines its own trailer beyond the core. Examples (illustrative — full schemas live in the SBE XML, not in markdown):

```
EquityInstrument {
  ...InstrumentCore...
  listing_venue_mic     char[4]
  share_class_letter    char[2]
  shares_outstanding    int64
  dividend_calendar_ref uint16
  is_etf                bool          // composes with EtfInstrument otherwise
}

BondInstrument {
  ...InstrumentCore...
  coupon_bps            uint32        // 0 for zero coupon
  coupon_frequency      enum          // ANNUAL | SEMI | QUARTERLY | ...
  day_count_ref         uint16        // index into RefData day-count table
  business_day_conv     enum          // FOLLOWING | MOD_FOLLOWING | ...
  issue_date            date
  maturity_date         date
  callable              bool
  putable               bool
  call_schedule_ref     uint32?       // group-id into call schedule table
  put_schedule_ref      uint32?
  ranking               enum          // SENIOR_SECURED | SENIOR_UNSECURED | SUBORDINATED | ...
  is_perpetual          bool
}

IrsInstrument {
  ...InstrumentCore...
  trade_currency        Currency enum
  notional_currency     Currency enum  // for XCCY swaps; else same as trade_currency
  effective_date        date
  termination_date      date
  fixed_leg_ref         uint32        // legs are themselves Reference data; see Composition
  float_leg_ref         uint32
  business_day_conv     enum
  date_roll_convention  enum
  is_cleared            bool
  ccp_ref               uint16        // index into RefData CCP table
  is_mat                bool          // see [[mat]]
}

FxSpotInstrument {
  ...InstrumentCore...
  base_currency         Currency enum
  quote_currency        Currency enum
  value_date_offset     uint8         // T+N for this pair
}

FxNdfInstrument {
  ...InstrumentCore...
  base_currency         Currency enum
  quote_currency        Currency enum   // typically USD
  fixing_source         enum            // PBOC_MID | RBI | EMTA_FIX | ...
  fixing_offset_days    uint8           // fixing happens fixing_offset_days before value
}

ListedOptionInstrument {
  ...InstrumentCore...
  underlying_figi       char[12]        // composition by ID
  strike_price          decimal64
  expiry_date           date
  exercise_style        enum            // AMERICAN | EUROPEAN | BERMUDAN
  call_or_put           enum
  contract_multiplier   uint32
  settlement_style      enum            // CASH | PHYSICAL | AUTO_EXERCISE
}

TbaMbsInstrument {
  ...InstrumentCore...
  agency                enum            // FN | FH | GN
  coupon_bps            uint32
  term_years            enum            // 30 | 20 | 15 | ARM
  settlement_class      enum            // A | B | C | D
  settlement_month      year_month
}

SpecifiedPoolInstrument {
  ...InstrumentCore...
  agency                enum            // same as TBA
  pool_number           char[8]
  current_factor        decimal64       // updated monthly via corporate-action event
  current_balance       int64
  wac_bps               uint32          // see [[wac-wam-wala]]
  wam_months            uint16
  wala_months           uint16
  stipulation_ref       uint32?         // bucketing for low-loan-balance, NY-conc, etc.
}
```

These are sketches. The full SBE XML carries every field. The note's job is to make the **pattern** clear: every template starts with `InstrumentCore`, then carries exactly the asset-class data the system needs.

## Package vs Instrument — a structural split

A **Package** is what gets *traded* and *routed*. An **Instrument** is what *exists* in the master. They are separate entities with separate lifecycles, separate identities, and separate streams.

Examples:

- An FX swap is one **Package** with two **Instrument** references (a spot leg + a forward leg, both `FxSpotInstrument` / `FxForwardInstrument`).
- An IRS is one **Package** with one **Instrument** (`IrsInstrument` itself defines the two legs internally — the legs are not separate Instruments because they aren't independently tradable).
- An options spread is one **Package** with N **Instrument** references (each is a `ListedOptionInstrument`).
- A single-stock trade is one **Package** with one **Instrument** (`EquityInstrument`).
- A US Treasury cash trade with a paired repo financing is one **Package** with two **Instruments** (a `BondInstrument` + a `RepoInstrument` — added when introduced).

```
Package {
  package_id         UUID            // wire identity
  package_type       enum            // SINGLE | MULTI_LEG | PAIRED | BWIC_LIST | ETF_RFQ_BLOCK
  leg_count          uint8
  legs:              group of Leg
}

Leg {
  leg_seq            uint8
  instrument_figi    char[12]
  instrument_version uint32          // see Supersession
  side               Side enum
  ratio_or_quantity  decimal64       // depends on package_type
  optional_overrides RawBytes?       // package-specific overrides, e.g. delta-hedge ratio
}
```

Why this matters:

- **FIX bridge** ([[arch-fix-api-bridge]]) routes packages — one `35=AB MultilegOrderSingle` maps to one Package with multiple legs.
- **Best-ex audit** ([[arch-best-execution]]) evaluates per-package.
- **Risk** ([[arch-risk-engine]]) aggregates per-instrument, not per-package — the instrument-level identity is what nets.
- **Regulatory reporting** ([[arch-regulatory-reporting-service]]) splits packages back into per-instrument leg reports for jurisdictional submission.

Conflating the two — e.g. trying to model multi-leg as a field of `Instrument` — is the classical mistake. The split here prevents a costly rewrite later.

## Composition by ID — never embedded subtree

A single rule: **an instrument record never contains another instrument record**.

| Composition | Mechanism |
|---|---|
| Option's underlying | `ListedOptionInstrument.underlying_figi` |
| ETF's basket | `EtfInstrument.basket_ref` → index into basket-definition table |
| Convertible's underlying equity | `ConvertibleBond.underlying_figi` |
| CDS's reference entity | `CdsInstrument.reference_entity_lei` |
| CDS's reference obligation | `CdsInstrument.reference_obligation_figi` |
| Floating-leg IRS's rate index | `IrsInstrument.float_leg_ref` → `RateIndex.index_id` |
| Structured product's underlying basket | `StructuredProductInstrument.basket_ref` |

Why:

- Embedded subtrees make the security record recursive — every consumer must walk the tree. Composition by ID lets every consumer **decide whether to dereference** based on what it needs.
- Lifecycle independence — the underlying equity supersedes (Apple corporate action); the option's record doesn't need to be rewritten.
- Hot-path zero-copy — the option record is fixed-size; dereference is a separate lookup if needed.
- Schema simplicity — `Instrument` SBE templates don't need to include every other template inline.

The dereferenced lookup happens against the **same local snapshot** (see Hot-Path Access below).

## Effective-dated supersession — driven by corporate actions

Security records are **immutable**. A change creates a **new version** that supersedes the prior version, with explicit `effective_from` / `effective_to` ranges.

This applies the same discipline as [[arch-configuration-service]], but the **reason** is different. Config changes because ops decides; instruments change because of **external events** — corporate actions:

- Stock splits / reverse splits (equity).
- Spin-offs (equity → two equities).
- Name changes / ticker changes (everything).
- M&A — Apple acquires NewCo, NewCo equity matures into Apple cash + Apple equity (per terms).
- CDS successions — Reference Entity merges into another; CDS successor mechanics apply.
- Bond amendments — covenant changes, indenture supplements.
- Coupon resets (floating-rate notes).
- Pool factor updates (MBS specified pools — monthly).
- Maturity events (everything that matures).
- Default events (CDS reference entity default; bond payment default).

The integration: **[[arch-corporate-actions]] is the driver**. Corporate-action events trigger security-master supersession. The new version's `effective_from` equals the corporate-action effective date. Replay against an event with `occurred_at = T0` resolves the security record at `T0` (whatever was effective at that moment).

```
InstrumentVersioned {
  ...InstrumentCore...                  // including version_seq, effective_from, effective_to
  caused_by_corporate_action_event_id   UUID?
}
```

The `caused_by_corporate_action_event_id` is non-null for any supersession driven by an external event. Manual amendments (rare, audit-heavy) use the same field with a synthetic admin event ID.

Lifecycle terminals (`MATURED`, `EXPIRED`, `DEFAULTED`) are reached via supersession to a final record with `effective_to = effective_from` and `lifecycle_status` set.

## Hot-path access pattern

Same discipline as [[arch-configuration-service]]:

1. The Security Master Service publishes the canonical record stream on an Aeron channel.
2. Each box runs a **local cache agent** that subscribes and maintains an immutable in-memory snapshot of every active instrument.
3. Hot-path consumers (router, validator, FSM dispatcher) read instrument data as **field access on a code-generated snapshot struct** — never a network call, never a map lookup.
4. Snapshot updates from supersession events arrive on the bus; the consumer's working thread **atomically swaps its snapshot pointer at the next message boundary** (between `handleMessage()` calls).
5. **One message → one security view**. All decisions inside a single message resolve against the same snapshot.

```
fn route_order(order: NewOrder) {
    let sec_snap = self.security_master.load();   // atomic load of Arc<SecurityMasterSnapshot>
    let inst = sec_snap.lookup(order.figi, order.instrument_version);
    if inst.lifecycle_status != ACTIVE { return reject(...); }
    if inst.currency != order.currency { return reject(...); }
    // dispatch on template_id for specialised checks
    match inst.template_id {
        BondInstrument => check_bond_specifics(inst, order),
        IrsInstrument => check_irs_specifics(inst, order),
        // ...
    }
}
```

The order message carries `(figi, instrument_version)`, not the full security record. Replay resolves the same pair against the snapshot effective at the replayed event's `occurred_at`.

## Polymorphic processing — generic core, specialised dispatch

The pattern lets the system **treat instruments generically** when it should, and **specialise** when it must:

**Generic operations** (read InstrumentCore only):

- Order routing decisions about asset-class permissions, currency, lifecycle.
- Validator checks: currency match, lifecycle ACTIVE, ID format.
- Identity chaining ([[arch-identity-chaining]]).
- FIX-bridge translation (the FIX side carries `Symbol` / `SecurityID` mapped to FIGI).
- Audit, observability, logging.

**Specialised operations** (dispatch on template_id, decode trailer):

- Pricing ([[arch-pricing-service]]) — needs coupon for accrual, strike for option valuation, fixing source for NDF.
- Risk ([[arch-risk-engine]]) — needs day count for DV01 on IRS, multiplier for futures.
- Settlement instructions ([[arch-stp-pipeline]]) — needs CSD per asset class, tri-party schedule for repo.
- Regulatory reporting ([[arch-regulatory-reporting-service]]) — needs IRS clearing flag, MAT flag, USI/UTI fields.
- Compliance fat-finger ([[arch-compliance]]) — needs benchmark price source per instrument type.

This is the architectural payoff. Code that doesn't need asset-class specifics doesn't pay for them; code that does has a typed path to exactly the fields it needs.

## Worked examples

### Equity (Apple on NYSE)

```
template_id:        EquityInstrument
figi:               BBG000B9XRY4
composite_figi:     BBG000B9Y5X2
share_class_figi:   BBG001S5N8V8
asset_class:        EQUITY
instrument_type:    COMMON_STOCK
display_name:       AAPL
issuer_lei:         HWUPKR0MPOU8FGXBT394
currency:           USD
country_of_issue:   US
country_of_listing: US
fungibility:        FUNGIBLE
settlement_convention: T_PLUS_1
lifecycle_status:   ACTIVE
listing_venue_mic:  XNYS
share_class_letter: ""
shares_outstanding: 15_634_232_000
dividend_calendar_ref: 421
is_etf:             false
```

Splits create a supersession event with `caused_by_corporate_action_event_id` pointing at the corporate-action record.

### Bond (a USD IG corporate)

```
template_id:        BondInstrument
figi:               BBG00XJ9KZ81
isin:               US037833DT06
cusip:              037833DT0
asset_class:        FIXED_INCOME
instrument_type:    CORP_IG
display_name:       AAPL 4.5 02/27/2030
issuer_lei:         HWUPKR0MPOU8FGXBT394
currency:           USD
fungibility:        FUNGIBLE
settlement_convention: T_PLUS_2
coupon_bps:         450
coupon_frequency:   SEMI
day_count_ref:      30_360
business_day_conv:  FOLLOWING
issue_date:         2020-02-27
maturity_date:      2030-02-27
ranking:            SENIOR_UNSECURED
```

### IRS (10y USD vanilla)

```
template_id:        IrsInstrument
figi:               (internal-allocated: ems_iid:acme:irs:78912)
asset_class:        RATES_DERIV
instrument_type:    VANILLA_IRS
display_name:       USD 10y 4.25 vs SOFR
currency:           USD
fungibility:        FUNGIBLE                       # cleared standardised IRS
settlement_convention: PER_CCP
effective_date:     2026-06-12
termination_date:   2036-06-12
trade_currency:     USD
notional_currency:  USD
fixed_leg_ref:      87234                          # → FixedLeg defn (4.25%, semi, 30/360)
float_leg_ref:      87235                          # → FloatLeg defn (SOFR, qtrly, ACT/360)
is_cleared:         true
ccp_ref:            LCH_SWAPCLEAR_idx
is_mat:             true
```

### TBA-MBS

```
template_id:        TbaMbsInstrument
figi:               BBG00JS49WB2
asset_class:        FIXED_INCOME
instrument_type:    TBA_MBS
display_name:       FNCL 5.5 JUL
currency:           USD
fungibility:        TBA_LIKE                       # fungible at trade level, non-fungible underlying
settlement_convention: TBA_MONTHLY
agency:             FN
coupon_bps:         550
term_years:         T_30Y
settlement_class:   A
settlement_month:   2026-07
```

The corresponding specified pools have `fungibility: NON_FUNGIBLE` and `template_id: SpecifiedPoolInstrument`.

### FX NDF (USD/CNY 1m)

```
template_id:        FxNdfInstrument
figi:               (composite per pair+tenor where Bloomberg assigns one)
asset_class:        FX
instrument_type:    NDF
display_name:       USD/CNY 1M NDF
currency:           USD
fungibility:        FUNGIBLE                       # per pair + fixing date + fixing source
settlement_convention: T_PLUS_2_AFTER_FIXING
base_currency:      USD
quote_currency:     CNY
fixing_source:      PBOC_MID
fixing_offset_days: 2
```

### Listed Option (AAPL 200 strike, 2026-07-17 call)

```
template_id:        ListedOptionInstrument
figi:               BBG011D0L3M3
asset_class:        EQUITY
instrument_type:    LISTED_OPTION_CALL
display_name:       AAPL 200 17JUL26 C
currency:           USD
fungibility:        FUNGIBLE
underlying_figi:    BBG000B9XRY4                   # ← composition by ID
strike_price:       200.00
expiry_date:        2026-07-17
exercise_style:     AMERICAN
call_or_put:        CALL
contract_multiplier: 100
settlement_style:   AUTO_EXERCISE
```

## Gnarly cases — called out

A few cases that trip up naive designs:

### NDF fixing source as part of identity

Two USD/CNY NDFs with the same value date but **different fixing sources** are **different instruments**. The `fixing_source` is part of identity, not metadata. FIGI sometimes captures this; when it doesn't, the internal-allocated namespace carries the discriminator. The fungibility line in [[fx-ndf]] reflects this.

### TBA fungibility encoded in the core

`fungibility: TBA_LIKE` is a separate core enum value because downstream code needs to know that the *trade* is fungible (router treats as one liquid market) while the *deliverable* is not (allocation, settlement, and TCA need pool-level data). Not buried in the extension — the core surfaces it.

### OTC bilateral derivatives without public FIGI

`internal_iid` populated; `figi` empty. The internal namespace is `ems_iid:{firm_id}:{instrument_class}:{counter}`. Cross-firm trades still need a USI/UTI ([[usi-uti]]) for regulatory reporting; the EMS's internal_iid is the join key, USI/UTI is the reporting key. Both stored on the instrument record.

### Pool factors (MBS specified pools)

The `current_factor` and `current_balance` change monthly. Treated as a **monthly supersession** — each month's factor publication causes a new version of the SpecifiedPoolInstrument. Historical pools resolve to their then-current factor on replay.

### Bonds with embedded options (callable / putable)

Schedules can be long. Stored as **referenced group data** — `call_schedule_ref` indexes into a separate stream of `CallScheduleEntry` records. Avoids inflating the BondInstrument schema. Pricing dereferences when needed.

### ETF basket composition

The basket itself is a separate entity: `BasketDefinition { basket_id, components: [(figi, weight), ...] }`. `EtfInstrument.basket_ref` references it. Basket components supersede when constituents change (rebalance events). Same supersession discipline.

### Multi-listed equity

Apple on NYSE vs Apple on FRA: two `EquityInstrument` records with the same `composite_figi` and `share_class_figi` but different `figi` and `listing_venue_mic`. The composite FIGI is the "Apple stock" abstraction; the specific FIGIs are the venue-specific instruments.

## Anti-patterns

- **Putting prices in the master.** Prices live in [[arch-pricing-service]]. The master is "what" not "how much."
- **Modeling multi-leg as a field on Instrument.** Multi-leg is a Package property.
- **Embedding underlying instruments inline.** Always compose by ID.
- **Mutable security records.** Replay determinism requires immutability + supersession.
- **One template with all fields nullable.** SBE works best with one template per asset class; trying to make a single mega-template defeats the purpose.
- **Vendor-specific identifiers as primary key.** Use FIGI (or internal-allocated). Vendor IDs are licensed secondaries.
- **Allowing the security master to know about orders.** Strict directional dependency: orders reference instruments, never the other way.

## API surface

```
operation: lookup_instrument
items: [{ figi | internal_iid, as_of: timestamp? }]
returns: InstrumentRecord at the requested point in time (effective_from <= as_of < effective_to)

operation: list_instruments
items: [{ filter: {asset_class?, instrument_type?, lifecycle_status?}, page }]

operation: publish_supersession
items: [{ prior_version_ref, new_version_record, caused_by_event_id, effective_from }]
# Audited; requires #security-master-author tag; mostly driven by [[arch-corporate-actions]]
```

## See also

- [[arch-symbology-figi]] (identifier licensing and metering)
- [[arch-reference-data-service]] (the broader registry pattern; security master is one tenant)
- [[arch-corporate-actions]] (drives supersession)
- [[arch-event-sourcing]] (supersession events on the admin stream)
- [[arch-configuration-service]] (the hot-path snapshot pattern, applied here)
- [[arch-sbe-aeron-transport]] (transport for the supersession stream)
- [[arch-multileg]] (Package mechanics for multi-leg orders)
- [[arch-pricing-service]] · [[arch-risk-engine]] · [[arch-position-service]] (specialised consumers)
- [[arch-fix-api-bridge]] (translates FIX Symbol/SecurityID into FIGI lookups)
- [[fungible-vs-non-fungible]] (glossary — the dimension encoded in `core.fungibility`)
- [[usi-uti]] · [[lei]] (glossary — identifiers carried on the record)
