/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.List;
import java.util.UUID;

/**
 * A Package is what gets traded and routed; an Instrument is what exists in the master.
 *
 * <p>A Package groups one or more {@link Leg}s that are submitted together as a single trade unit.
 * The {@link PackageType} determines how legs are interpreted: {@code SINGLE} has exactly one leg;
 * {@code MULTI_LEG} has two or more independently-priced legs; {@code PAIRED} is a cash + financing
 * pair; {@code BWIC_LIST} is a bid-wanted list; {@code ETF_RFQ_BLOCK} is a creation/redemption
 * basket.
 *
 * <p>Named {@code InstrumentPackage} (not {@code Package}) to avoid shadowing {@code
 * java.lang.Package}.
 *
 * <p>Task 4.18 — Package entity + Leg group schema.
 */
public record InstrumentPackage(UUID packageId, PackageType packageType, List<Leg> legs) {

  public int legCount() {
    return legs.size();
  }

  public boolean isSingle() {
    return packageType == PackageType.SINGLE;
  }

  public boolean isMultiLeg() {
    return packageType == PackageType.MULTI_LEG || packageType == PackageType.PAIRED;
  }
}
