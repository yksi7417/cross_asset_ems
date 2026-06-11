/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.position;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One (account, instrument) position projection (task 10.7, arch-position-service.md). Quantities
 * are derived from the net under the weighted-average-cost model: positive net = long, negative =
 * short. {@code avgCost} and prices are OMS fixed-point longs; {@code realizedPnl} accumulates in
 * price-units × qty. {@code unrealizedPnl} is computed at read time from the supplied mark (null
 * when no mark was available — never silently zero).
 */
public record Position(
    String account,
    String figi,
    long longQty,
    long shortQty,
    long netQty,
    long avgCost,
    long realizedPnl,
    @Nullable Long unrealizedPnl,
    long lastFillEventId) {

  public Position {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(figi, "figi");
  }

  /** A flat, never-traded position. */
  public static Position flat(String account, String figi) {
    return new Position(account, figi, 0, 0, 0, 0, 0, null, 0);
  }
}
