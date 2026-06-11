/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import java.util.Map;
import java.util.Objects;

/**
 * One market-data update for one instrument (task 18.12): the fields that changed, already filtered
 * to the subscription's requested field set. A tick is a row delta — exactly what the Perspective
 * grid consumes — not a full snapshot; consumers keyed by FIGI merge deltas into their last-value
 * image.
 *
 * @param feedId the producing feed ({@link MarketDataFeed#feedId()})
 * @param figi instrument identity (FIGI, the system-wide symbology key)
 * @param values changed fields; price fields fixed-point, size fields raw counts
 * @param atMillis provider timestamp (epoch millis)
 */
public record MdTick(String feedId, String figi, Map<MdField, Long> values, long atMillis) {

  public MdTick {
    Objects.requireNonNull(feedId, "feedId");
    Objects.requireNonNull(figi, "figi");
    values = Map.copyOf(Objects.requireNonNull(values, "values"));
  }
}
