/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.UUID;

/**
 * A versioned instrument record: the canonical {@link InstrumentCore} plus the causal link to the
 * corporate-action event that triggered this version's creation.
 *
 * <p>{@code causedByCorporateActionEventId} is non-null for supersessions driven by external events
 * (stock splits, coupon resets, default, maturity). It is null for the initial creation record and
 * for rare manual amendments (which carry a synthetic admin event ID in the event log, not here).
 *
 * <p>Task 4.19 — Security master CRUD + supersession events.
 */
public record InstrumentVersioned(InstrumentCore core, UUID causedByCorporateActionEventId) {

  public String figi() {
    return core.figi();
  }

  public long versionSeq() {
    return core.versionSeq();
  }

  public long effectiveFrom() {
    return core.effectiveFrom();
  }

  public long effectiveTo() {
    return core.effectiveTo();
  }

  public boolean isActive() {
    return core.isActive();
  }

  public boolean isOpenEnded() {
    return core.isOpenEnded();
  }

  public AssetClass assetClass() {
    return core.assetClass();
  }

  public CurrencyCode currency() {
    return core.currency();
  }

  public LifecycleStatus lifecycleStatus() {
    return core.lifecycleStatus();
  }
}
