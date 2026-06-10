/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One leg of a {@link MultiLegOrderRequest}. {@code legRatio} is signed (e.g. +1 / -1); {@code
 * side} uses the FIX tag 54 integer code. {@code venueMic} is the leg's routing target — for
 * ALL_OR_NONE packages all legs must name the same venue (EMS-ORD-4001).
 */
public record LegRequest(
    int legRatio, String figi, int side, long qty, @Nullable Long price, String venueMic) {
  public LegRequest {
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(venueMic, "venueMic");
  }
}
