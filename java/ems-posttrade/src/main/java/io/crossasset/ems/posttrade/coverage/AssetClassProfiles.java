/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.coverage;

import io.crossasset.ems.posttrade.confirmation.MatchTolerance;
import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.stp.StageProfile;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The cross-asset post-trade profile registry (Phase 16). One {@link AssetClassProfile} per {@link
 * Coverage} label, capturing how each asset class is allocated, confirmed, settled, and reported.
 * Lot sizes and tolerances follow market convention (whole shares/contracts, $1000 bond
 * denomination, 10k FX minimum, half-tick bond / pip FX confirmation windows).
 */
public final class AssetClassProfiles {

  private static final long WHOLE_UNIT = 1L;
  private static final long BOND_DENOMINATION = 1_000L;
  private static final long FX_MIN_NOTIONAL = 10_000L;

  private static final Map<Coverage, AssetClassProfile> REGISTRY = new EnumMap<>(Coverage.class);

  static {
    register(
        new AssetClassProfile(
            Coverage.US_IG_CORP,
            StageProfile.corpBond(),
            MatchTolerance.corpBond(1, 2),
            BOND_DENOMINATION,
            List.of(Regulator.TRACE)));
    register(
        new AssetClassProfile(
            Coverage.TREASURY,
            StageProfile.treasury(),
            MatchTolerance.corpBond(1, 2),
            BOND_DENOMINATION,
            List.of(Regulator.TRACE)));
    register(
        new AssetClassProfile(
            Coverage.US_EQUITY,
            StageProfile.usEquity(),
            MatchTolerance.exact(),
            WHOLE_UNIT,
            List.of(Regulator.FINRA_CAT)));
    register(
        new AssetClassProfile(
            Coverage.PREFERRED,
            StageProfile.preferred(),
            MatchTolerance.exact(),
            WHOLE_UNIT,
            List.of(Regulator.FINRA_CAT)));
    register(
        new AssetClassProfile(
            Coverage.LISTED_FUT_OPT,
            StageProfile.listedDerivative(),
            MatchTolerance.exact(),
            WHOLE_UNIT,
            List.of(Regulator.CFTC_SDR)));
    register(
        new AssetClassProfile(
            Coverage.FX_SPOT,
            StageProfile.fxSpot(),
            MatchTolerance.fx(2),
            FX_MIN_NOTIONAL,
            List.of()));
    register(
        new AssetClassProfile(
            Coverage.FX_FORWARD,
            StageProfile.fxForward(),
            MatchTolerance.fx(2),
            FX_MIN_NOTIONAL,
            List.of(Regulator.CFTC_SDR)));
  }

  private AssetClassProfiles() {}

  private static void register(AssetClassProfile profile) {
    REGISTRY.put(profile.coverage(), profile);
  }

  /** The profile for a coverage label. */
  public static AssetClassProfile of(Coverage coverage) {
    AssetClassProfile profile = REGISTRY.get(coverage);
    if (profile == null) {
      throw new IllegalArgumentException("No post-trade profile for coverage " + coverage);
    }
    return profile;
  }

  /** All registered profiles. */
  public static List<AssetClassProfile> all() {
    return List.copyOf(REGISTRY.values());
  }
}
