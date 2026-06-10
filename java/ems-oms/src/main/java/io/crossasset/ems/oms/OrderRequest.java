/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Staging input for a new order. Side and Tif use FIX tag 54 / 59 integer codes. */
public record OrderRequest(
    String requestId,
    long sessionId,
    String clOrdId,
    String figi,
    int side,
    long qty,
    @Nullable Long price,
    String account,
    int tif) {
  public OrderRequest {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(clOrdId, "clOrdId");
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(account, "account");
  }
}
