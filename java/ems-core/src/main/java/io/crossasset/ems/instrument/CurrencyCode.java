/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Major ISO 4217 currency codes encoded as uint16 numeric values.
 *
 * <p>The {@code currency} field in {@link InstrumentCore} is stored as a raw {@code uint16}
 * matching ISO 4217 numeric codes. {@code UNKNOWN} (65535) serves as the null sentinel consistent
 * with the SBE {@code nullValue} in equity-instrument.xml.
 */
public enum CurrencyCode {
  USD(840),
  EUR(978),
  GBP(826),
  JPY(392),
  CHF(756),
  CNY(156),
  HKD(344),
  SGD(702),
  AUD(36),
  CAD(124),
  SEK(752),
  NOK(578),
  DKK(208),
  NZD(554),
  MXN(484),
  BRL(986),
  KRW(410),
  INR(356),
  ZAR(710),
  UNKNOWN(65535);

  public final int iso4217Code;

  CurrencyCode(int iso4217Code) {
    this.iso4217Code = iso4217Code;
  }

  public static Optional<CurrencyCode> fromIso4217(int code) {
    for (CurrencyCode v : values()) {
      if (v.iso4217Code == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
