/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Objects;

/**
 * The three currency roles an instrument can play (18.30, see the vault note
 * [[currency-in-execution]]). For most instruments all roles collapse to {@link
 * InstrumentCore#currency()} — {@link #defaults} builds that case — but the distinctions are the
 * product for: FX pairs (price is quote-per-base, qty is base notional, settlement moves both
 * legs), samurai/eurobonds (denomination independent of issuer), ADRs (receipt trades in a
 * different currency than the underlying), and dual listings in minor units (GBp is not GBP).
 *
 * @param tradingCurrency what the PRICE is quoted in
 * @param tradingMinorUnit true when prices quote in the minor unit (GBp pence, ZAc cents) — a
 *     1,250 GBp print is 12.50 GBP; validation bands and notional math must divide by 100
 * @param settlementCurrency what cash actually moves (FX settles BOTH legs; this is the
 *     quote-leg convention currency)
 * @param baseCurrency FX only (nullable): the unit currency being bought/sold (EUR in EUR/USD), else null
 * @param quoteCurrency FX only (nullable): the currency the price is denominated in (USD in EUR/USD), else
 *     null
 */
public record CurrencyProfile(
    CurrencyCode tradingCurrency,
    boolean tradingMinorUnit,
    CurrencyCode settlementCurrency,
    CurrencyCode baseCurrency,
    CurrencyCode quoteCurrency) {

  public CurrencyProfile {
    Objects.requireNonNull(tradingCurrency, "tradingCurrency");
    Objects.requireNonNull(settlementCurrency, "settlementCurrency");
    if ((baseCurrency == null) != (quoteCurrency == null)) {
      throw new IllegalArgumentException("baseCurrency and quoteCurrency come as a pair (FX)");
    }
  }

  /** The collapsed case: trading = settlement = the core's currency, no FX pair roles. */
  public static CurrencyProfile defaults(InstrumentCore core) {
    return new CurrencyProfile(core.currency(), false, core.currency(), null, null);
  }

  /** FX pair: price is quote-per-base, qty is base notional, settles per convention. */
  public static CurrencyProfile fxPair(CurrencyCode base, CurrencyCode quote) {
    return new CurrencyProfile(quote, false, quote, base, quote);
  }

  public boolean isFxPair() {
    return baseCurrency != null;
  }
}
