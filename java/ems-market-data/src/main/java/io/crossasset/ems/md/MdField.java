/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import java.util.Locale;

/**
 * Market-data fields a subscription can request (task 18.12). Price-kind fields carry fixed-point
 * longs in the instrument's price scale (same convention as the pricing service); size-kind fields
 * carry raw counts. Providers map their native field names onto these (e.g. Bloomberg {@code BID},
 * {@code ASK}, {@code LAST_PRICE} in 18.13).
 */
public enum MdField {
  BID,
  ASK,
  BID_SIZE,
  ASK_SIZE,
  LAST,
  LAST_SIZE,
  VOLUME,
  OPEN,
  HIGH,
  LOW,
  PREV_CLOSE;

  /** Stable lower-case key used in JSON row payloads (Perspective column name). */
  public String jsonKey() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Price-kind fields carry fixed-point values; size-kind fields carry raw counts. */
  public boolean isPrice() {
    return switch (this) {
      case BID, ASK, LAST, OPEN, HIGH, LOW, PREV_CLOSE -> true;
      case BID_SIZE, ASK_SIZE, LAST_SIZE, VOLUME -> false;
    };
  }
}
