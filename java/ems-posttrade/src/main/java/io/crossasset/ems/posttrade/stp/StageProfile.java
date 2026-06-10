/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import java.util.List;
import java.util.Objects;

/**
 * The ordered set of downstream stages an asset class runs after allocation (per
 * arch-stp-pipeline.md § Per-asset-class variants). The orchestrator selects a profile from this
 * configuration; {@link Stage#ALLOCATION} is always implied first and is not listed here.
 */
public record StageProfile(String assetClass, List<Stage> downstreamStages) {

  public StageProfile {
    Objects.requireNonNull(assetClass, "assetClass");
    downstreamStages = List.copyOf(downstreamStages);
  }

  /** US IG corp bond: allocation → confirmation → SI → TRACE → B&R (the MVP asset class). */
  public static StageProfile corpBond() {
    return new StageProfile(
        "CORP_BOND",
        List.of(
            Stage.CONFIRMATION,
            Stage.SETTLEMENT_INSTRUCTION,
            Stage.REGULATORY_REPORTING,
            Stage.BOOKS_AND_RECORDS));
  }

  /** Cash equity: allocation → SI → B&R (no confirmation, TRACE not applicable). */
  public static StageProfile cashEquity() {
    return new StageProfile(
        "CASH_EQUITY", List.of(Stage.SETTLEMENT_INSTRUCTION, Stage.BOOKS_AND_RECORDS));
  }
}
