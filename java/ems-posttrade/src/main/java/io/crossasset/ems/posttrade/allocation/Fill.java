/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import java.util.Objects;

/**
 * A route-level fill to be allocated. {@code price} is in integer price ticks (same convention as
 * the order layer). The {@code fillId} is the stable key the allocation events trace back to for
 * reversal on a bust/correct.
 */
public record Fill(String fillId, String orderId, String routeId, long qty, long price) {
  public Fill {
    Objects.requireNonNull(fillId, "fillId");
    Objects.requireNonNull(orderId, "orderId");
    if (qty <= 0) {
      throw new IllegalArgumentException("fill qty must be > 0");
    }
  }
}
