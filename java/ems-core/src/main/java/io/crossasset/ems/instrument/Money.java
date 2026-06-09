/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.math.BigDecimal;

/**
 * Immutable monetary amount with ISO 4217 currency code.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public record Money(BigDecimal amount, CurrencyCode currency) {

  public Money {
    if (amount == null) throw new IllegalArgumentException("amount must not be null");
    if (currency == null) throw new IllegalArgumentException("currency must not be null");
  }

  public static Money of(BigDecimal amount, CurrencyCode currency) {
    return new Money(amount, currency);
  }
}
