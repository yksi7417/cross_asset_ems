/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Immutable record representing a corporate action.
 *
 * <p>All date fields are epoch-millisecond UTC longs. Nullable fields (ratio, cashAmount,
 * newInstrument, options) use {@code null} to represent absence.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public record CorporateAction(
    UUID caId,
    CorporateActionType caType,
    CorporateActionSource source,
    String sourceRef,
    List<String> instrumentsAffected,
    long exDate,
    long recordDate,
    long payDate,
    long effectiveDate,
    BigDecimal ratio,
    Money cashAmount,
    String newInstrument,
    List<CorporateActionOption> options,
    CorporateActionState state) {

  public CorporateAction {
    if (caId == null) throw new IllegalArgumentException("caId must not be null");
    if (caType == null) throw new IllegalArgumentException("caType must not be null");
    if (source == null) throw new IllegalArgumentException("source must not be null");
    if (instrumentsAffected == null || instrumentsAffected.isEmpty())
      throw new IllegalArgumentException("instrumentsAffected must not be null or empty");
    if (state == null) throw new IllegalArgumentException("state must not be null");
    instrumentsAffected = List.copyOf(instrumentsAffected);
    options = options != null ? List.copyOf(options) : List.of();
  }

  public boolean isApplied() {
    return state == CorporateActionState.APPLIED;
  }

  public boolean isCancelled() {
    return state == CorporateActionState.CANCELLED;
  }

  /** Returns a copy of this action with the given new state. */
  public CorporateAction withState(CorporateActionState newState) {
    if (!state.canTransitionTo(newState)) {
      throw new IllegalStateException(
          "Invalid state transition: " + state + " → " + newState + " for CA " + caId);
    }
    return new CorporateAction(
        caId,
        caType,
        source,
        sourceRef,
        instrumentsAffected,
        exDate,
        recordDate,
        payDate,
        effectiveDate,
        ratio,
        cashAmount,
        newInstrument,
        options,
        newState);
  }
}
