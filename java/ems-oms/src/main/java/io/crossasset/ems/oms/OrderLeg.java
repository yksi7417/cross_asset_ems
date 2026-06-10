/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable view of one leg of a {@link MultiLegOrder}, rebuilt on every query so {@code state}
 * reflects the leg's live route FSM state. {@code childOrderId} / {@code routeId} are null until
 * the leg is dispatched.
 */
public record OrderLeg(
    String legId,
    int index,
    int legRatio,
    String figi,
    int side,
    long qty,
    @Nullable Long price,
    String venueMic,
    @Nullable String childOrderId,
    @Nullable String routeId,
    LegState state) {
  public OrderLeg {
    Objects.requireNonNull(legId, "legId");
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(venueMic, "venueMic");
    Objects.requireNonNull(state, "state");
  }
}
