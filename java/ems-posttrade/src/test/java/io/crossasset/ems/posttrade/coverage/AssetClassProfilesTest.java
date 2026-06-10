/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.RegulatorDeterminer;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import io.crossasset.ems.posttrade.stp.Stage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the cross-asset post-trade profile registry (16.1). */
class AssetClassProfilesTest {

  @Test
  void everyCoverageLabelHasAProfile() {
    for (Coverage c : Coverage.values()) {
      assertThat(AssetClassProfiles.of(c)).isNotNull();
    }
    assertThat(AssetClassProfiles.all()).hasSize(Coverage.values().length);
  }

  @Test
  void lotSizesFollowMarketConvention() {
    assertThat(AssetClassProfiles.of(Coverage.US_EQUITY).allocationLotSize()).isEqualTo(1);
    assertThat(AssetClassProfiles.of(Coverage.LISTED_FUT_OPT).allocationLotSize()).isEqualTo(1);
    assertThat(AssetClassProfiles.of(Coverage.US_IG_CORP).allocationLotSize()).isEqualTo(1_000);
    assertThat(AssetClassProfiles.of(Coverage.TREASURY).allocationLotSize()).isEqualTo(1_000);
    assertThat(AssetClassProfiles.of(Coverage.FX_SPOT).allocationLotSize()).isEqualTo(10_000);
    assertThat(AssetClassProfiles.of(Coverage.FX_FORWARD).allocationLotSize()).isEqualTo(10_000);
  }

  @Test
  void coverageMapsToCanonicalAssetClass() {
    assertThat(Coverage.US_EQUITY.assetClass()).isEqualTo(AssetClass.EQUITY);
    assertThat(Coverage.PREFERRED.assetClass()).isEqualTo(AssetClass.EQUITY);
    assertThat(Coverage.TREASURY.assetClass()).isEqualTo(AssetClass.FIXED_INCOME);
    assertThat(Coverage.LISTED_FUT_OPT.assetClass()).isEqualTo(AssetClass.LISTED_DERIVATIVE);
    assertThat(Coverage.FX_SPOT.assetClass()).isEqualTo(AssetClass.FX);
  }

  @Test
  void stageProfilesReflectAssetClassFlow() {
    // FX spot has no transaction reporting; corp bond confirms and reports.
    assertThat(AssetClassProfiles.of(Coverage.FX_SPOT).stageProfile().downstreamStages())
        .doesNotContain(Stage.REGULATORY_REPORTING)
        .contains(Stage.CONFIRMATION);
    assertThat(AssetClassProfiles.of(Coverage.US_IG_CORP).stageProfile().downstreamStages())
        .contains(Stage.CONFIRMATION, Stage.REGULATORY_REPORTING);
    // Cash-style equity reports (CAT) but does not bilaterally confirm.
    assertThat(AssetClassProfiles.of(Coverage.US_EQUITY).stageProfile().downstreamStages())
        .contains(Stage.REGULATORY_REPORTING)
        .doesNotContain(Stage.CONFIRMATION);
  }

  @Test
  void profileRegulatorsMatchTheDeterminerMatrix() {
    RegulatorDeterminer determiner = RegulatorDeterminer.crossAssetUs();
    Map<Coverage, List<Regulator>> expected =
        Map.of(
            Coverage.US_IG_CORP, List.of(Regulator.TRACE),
            Coverage.TREASURY, List.of(Regulator.TRACE),
            Coverage.US_EQUITY, List.of(Regulator.FINRA_CAT),
            Coverage.PREFERRED, List.of(Regulator.FINRA_CAT),
            Coverage.LISTED_FUT_OPT, List.of(Regulator.CFTC_SDR),
            Coverage.FX_FORWARD, List.of(Regulator.CFTC_SDR),
            Coverage.FX_SPOT, List.of());

    expected.forEach(
        (coverage, regulators) -> {
          // The registry and the determiner agree.
          assertThat(AssetClassProfiles.of(coverage).regulators()).isEqualTo(regulators);
          ReportableTrade trade =
              new ReportableTrade(
                  "TR-" + coverage, coverage.name(), "US", "INST", 1, 100, 9950, Map.of());
          assertThat(determiner.applicableRegulators(trade)).isEqualTo(regulators);
        });
  }
}
