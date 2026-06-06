---
type: architecture
layer: reference_data
status: draft
tags: [architecture/reference_data]
---

# Symbology — FIGI-First with Metered Secondary IDs

The canonical security identifier inside the EMS is the **FIGI** (Financial Instrument Global Identifier). [SEDOL](https://en.wikipedia.org/wiki/SEDOL), [CUSIP](https://www.cusip.com/), and [ISIN](https://www.isin.org/) are stored as **licensed secondary identifiers** with access metering.

> This note covers **identifier licensing and metering discipline**. The broader instrument data model — core+extension SBE schemas per asset class, package vs instrument, composition by ID, effective-dated supersession driven by corporate actions, hot-path snapshot access — is in [[arch-security-master]].

## Why FIGI

- **Open.** [OpenFIGI](https://www.openfigi.com/) is free to redistribute, no per-call licensing.
- **Granular.** Three levels: `figi` (instrument on a venue), `shareClassFIGI` (share class across venues), `compositeFIGI` (country-level composite).
- **Stable.** FIGIs do not change on corporate actions the way tickers do.
- **Cross-asset.** Same scheme covers equities, FX, bonds, derivatives, indices.

## The licensed-identifier problem

SEDOL (LSE / FTSE Russell), CUSIP (CUSIP Global Services / S&P), and ISIN (national numbering agencies) are commercial datasets. The EMS must:

1. **Authorize** — check the firm's license per identifier type before returning.
2. **Meter** — count distinct accesses for per-seat/per-call licensing.
3. **Audit** — every access tied to [[arch-firm-desk-user|identity]] and request, retained.

## Reference-data model

```
Instrument {
  figi:               string           // primary key
  composite_figi:     string?
  share_class_figi:   string?
  name:               string
  asset_class:        enum             // equity | fi | fx | rates_credit_deriv | commodity
  micCode:            string?          // primary venue MIC
  currency:           ccy
  // ... asset-class-specific block (e.g. maturity_date for FI, ccy_pair for FX)
  identifiers: {
    sedol: { value, license: SEDOL, last_accessed_at, access_count_30d }
    cusip: { value, license: CUSIP, ... }
    isin:  { value, license: ISIN,  ... }
    ticker:{ value, mic }
  }
}
```

`identifiers.*` reads are routed through a `MeteredIdentifierAccess` event (see [[arch-event-sourcing]]). Reads without the necessary license return `EMS-REF-1001 license_denied`.

## API surface

Mirrors the [OpenFIGI mapping endpoint](https://www.openfigi.com/api/openapi-spec):

```
POST /v3/resolve
Request {
  request_id:   UUID
  client_seq:   uint64
  identity:     Identity
  items: [
    { id_type: ID_BB_GLOBAL | ID_ISIN | ID_CUSIP | ID_SEDOL | ID_TICKER, id_value, exch_code?, mic_code?, currency? }
  ]
}
Response { results: [ { figi, name, ticker, exch_code, ... } | { error_code, error_message } ] }
```

Batch is the default — see [[arch-api-first]].

## Where FIGI lives in the stack

- **Order layer.** [[arch-order-staged]] stores `instrument_figi` not ticker. Display strings are resolved on render.
- **Router layer.** [[arch-router-layer]] uses `figi` + target `mic_code` to choose the right [[arch-venue-connectivity|venue adapter]].
- **Quote server.** [[arch-quote-server]] topics are keyed by `figi` or `composite_figi` depending on subscription.
- **Validator.** [[arch-validator]] enforces "tradable instrument" rules against the FIGI's asset class and venue support.

## Asset-class extensions (sketch)

| Asset class | Extra fields |
|---|---|
| FX | `ccy_pair`, `tenor`, `value_date_rule`, `is_ndf`, `settlement_currency` |
| FI | `cusip`, `coupon`, `maturity_date`, `cpn_freq`, `day_count`, `is_callable` |
| Equity | `shares_outstanding`, `lot_size`, `borrow_difficulty` |
| Listed deriv | `underlying_figi`, `expiry`, `strike`, `multiplier`, `option_style` |
| OTC IRS / CDS | `notional_ccy`, `fixed_leg`, `float_leg`, `index`, `tenor` |

These live in an instrument-typed union; the canonical envelope stays generic.

## Licensing policy enforcement

- **Per-firm license registry.** A firm subscribes to `{ ISIN, CUSIP, SEDOL }` independently.
- **Access counts** rolled up daily into invoice reports.
- **Outbound projection scrubbing.** Identifier values are stripped from outbound messages where the receiving counterparty's license does not cover them.

## See also

- [[arch-api-first]]
- [[arch-order-staged]]
- [[arch-router-layer]]
- [[arch-validator]]
- [[arch-event-sourcing]]
