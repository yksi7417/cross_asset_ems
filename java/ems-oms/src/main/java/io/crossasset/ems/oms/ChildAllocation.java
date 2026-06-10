/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Objects;

/**
 * One child's share of a block fill. {@code px} is the fill price (PRO_RATA / SEQUENCED) or the
 * running average price (AVG_PRICE), in the same scaled-long units as route fills.
 */
public record ChildAllocation(String childOrderId, long qty, long px) {
  public ChildAllocation {
    Objects.requireNonNull(childOrderId, "childOrderId");
  }
}
