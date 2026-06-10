/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.coverage;

import io.crossasset.ems.posttrade.confirmation.MatchTolerance;
import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.stp.StageProfile;
import java.util.List;

/**
 * The post-trade handling for one {@link Coverage} label: the STP {@link StageProfile} (which
 * downstream stages run), the confirmation {@link MatchTolerance}, the allocation lot size
 * (smallest allocatable unit, e.g. 1 share, $1000 face, 10k FX notional), and the regulators the
 * trade reports to. Reference data — one source of truth so the allocation, STP, confirmation, and
 * reporting services all agree on how an asset class is processed.
 */
public record AssetClassProfile(
    Coverage coverage,
    StageProfile stageProfile,
    MatchTolerance confirmationTolerance,
    long allocationLotSize,
    List<Regulator> regulators) {

  public AssetClassProfile {
    regulators = List.copyOf(regulators);
  }
}
