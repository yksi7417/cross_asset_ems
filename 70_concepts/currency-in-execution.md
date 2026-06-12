# Currency in Execution & Order Management

How currency actually behaves per security type, and what that means for an EMS. The single
`currency` field most systems start with cannot express the distinctions below; the model this
note drives is **three roles** (18.30):

| Role | Question it answers | Schema field |
|---|---|---|
| **Trading currency** | What is the *price* quoted in? | `tradingCurrency` (+ `tradingMinorUnit`) |
| **Settlement currency** | What cash actually moves? | `settlementCurrency` |
| **Base / quote currency** | For FX: which currency is the unit, which is the price? | `baseCurrency` / `quoteCurrency` |

For most instruments all three collapse to the same value — which is exactly why systems get
this wrong: the simple case hides the model until a samurai bond or an ADR shows up.

## Per security type

### FX pairs — BASE/QUOTE is the whole product

`EUR/USD @ 1.0842` means **1.0842 USD per 1 EUR**: EUR is the *base* (the unit being bought or
sold), USD is the *quote* (what the price is denominated in). Order **qty is base-currency
notional** (buy 1,000,000 EUR/USD = buy €1M, pay $1,084,200). Settlement moves **both legs** —
there is no single settlement currency; each counterparty receives one leg (CLS nets them).

- **Inverse pairs**: USD/JPY and JPY/USD are *different products* quoting the same risk. Market
  convention fixes the direction (EUR/USD never USD/EUR; USD/JPY never JPY/USD) — an EMS should
  normalize to convention and *display* the inverse rather than book it.
- **Execution implications**: a limit price is in the *quote* currency; slippage and best-ex
  comparisons are in quote-currency pips; P&L on an FX position is naturally in the quote
  currency and needs one more conversion to the firm base.

### Bonds — denomination is independent of the issuer

A **samurai bond** is JPY-denominated debt issued *in Japan by a foreign issuer* (e.g. a
Microsoft JPY 0.8% 2031): trading currency JPY, settlement JPY, issuer country US. Eurobonds
generalize this (issuer country ≠ denomination ≠ market of issue). Consequences:

- Never derive currency from the issuer (or vice versa) — they are independent axes. Group by
  *issuer* for capital-structure views ([[18.29|issuer grouping]]) and by *trading currency* for
  funding/hedging views; both must be first-class columns.
- Bond prices quote **per 100 face** in the trading currency; true cash = qty(face) × px/100 —
  notional math must use the *trading* currency even when the desk thinks in USD.

### ADRs / GDRs — the receipt trades in a different currency than the underlying

A Toyota ADR trades and settles in **USD on US venues** while the underlying line trades in
**JPY on XTKS**, linked by the depositary ratio (e.g. 1 ADR = 10 ordinary shares). The holder
has JPY economic exposure wrapped in a USD instrument:

- Trading ccy = settlement ccy = USD; the *underlying's* trading currency is JPY. Fair-value and
  premium/discount monitors must convert via the ratio **and** the live FX rate.
- Best-ex across the ADR and the local line is a cross-currency comparison — quote both in one
  currency before ranking venues.

### Dual / multi-currency listings — the same ISIN in pence and euros

One security can trade on XLON in **GBp (pence)** and on XETR in **EUR**. Two traps:

- **Minor units**: GBp is *not* GBP — a 1,250 GBp print is £12.50. The schema carries an explicit
  `tradingMinorUnit` flag; converting line-by-line ad hoc is how 100× fat-fingers happen. (Same
  for ZAc, ILa.)
- The *settlement* currency may follow the venue (GBP at CREST, EUR at Clearstream) even though
  it is one fungible line — settlement instructions are venue-scoped, not instrument-scoped.

### Derivatives — price, settlement and underlying can be three currencies

- **Listed futures/options**: price quotes in the contract's trading currency; variation margin
  settles in the *settlement* currency (usually the same, not always); the underlying may be in
  another (Nikkei futures on CME settle USD — a **quanto**: FX risk is borne by the exchange's
  fixed conversion, not the holder).
- **IRS**: the "price" is a *rate* (percent), not money; qty is notional in the swap's currency;
  cash settlement is per-leg in the leg currencies (cross-currency swaps settle two).

## What each role drives in this system

| Concern | Uses | Why |
|---|---|---|
| Limit-price validation, fat-finger bands | `tradingCurrency` + `tradingMinorUnit` | a band in the wrong unit is 100× off |
| Ticket display ([[18.2|order ticket]]) | `tradingCurrency` on the px field, `settlementCurrency` on the preview | trader sees what they pay in |
| Notional column (18.28) | `tradingCurrency` label; FX/IRS qty-is-notional rule by class | a swap's px is a rate |
| Intraday P&L (18.7) | `tradingCurrency` → FX → firm base | marks are in trading ccy |
| Best-ex / TCA venue comparison | normalize to one ccy first | XLON GBp vs XETR EUR is not comparable raw |
| Settlement instructions (50_clearing_settlement) | `settlementCurrency` per venue | cash moves there, FX legs move both |

## Demo universe coverage (DemoUniverse.java)

| Case | Instrument |
|---|---|
| FX base/quote + inverse-direction convention | EUR/USD spot (base EUR), USD/JPY fwd (base USD) |
| Samurai (foreign issuer, local denomination) | Microsoft JPY 0.8% 2031 — trading/settle JPY, issuer US |
| ADR vs underlying line | Toyota ADR (USD, XNYS) vs Toyota Motor (JPY, XTKS) |
| Same-issuer multi-currency capital structure | Microsoft: USD stock/convert/option + JPY samurai |

Related: [[currency-in-execution]] is the concept note behind PLAN task 18.30;
[[asset-class-matrix]] for the class taxonomy; `CurrencyProfile` in `ems-core` for the schema.
