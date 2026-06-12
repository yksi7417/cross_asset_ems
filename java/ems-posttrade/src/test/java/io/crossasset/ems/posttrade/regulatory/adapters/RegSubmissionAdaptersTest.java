/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.crossasset.ems.posttrade.confirmation.ConfirmationNetwork;
import io.crossasset.ems.posttrade.confirmation.MarkitServNetwork;
import io.crossasset.ems.posttrade.confirmation.TradeRecord;
import io.crossasset.ems.posttrade.regulatory.InMemoryRegulatoryReportingService;
import io.crossasset.ems.posttrade.regulatory.JurisdictionRouter;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent;
import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.RegulatorDeterminer;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import io.crossasset.ems.posttrade.regulatory.ReportingProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 12.4 + 12.7 + 12.8 + 12.9 + 12.11: each submission dialect's distinguishing fields, UTI
 * determinism, jurisdiction routing incl. dual-homed firms, and the MarkitServ UTI-keyed
 * confirmation flow.
 */
class RegSubmissionAdaptersTest {

  private static ReportableTrade trade(
      String assetClass, String jurisdiction, Map<String, String> fields) {
    return new ReportableTrade(
        "TRD-1", assetClass, jurisdiction, "BBG00DEMO0001", 1, 500_000L, 98_7500L, fields);
  }

  @Test
  void msrb_reportsParAndMuniFields_throughThePipeline() {
    MsrbRtrsAdapter adapter = MsrbRtrsAdapter.acking();
    ReportableTrade muni =
        trade(
            "MUNI",
            "US",
            Map.of(
                "cusip",
                "64966LMN8",
                "coupon",
                "425",
                "yield",
                "388",
                "capacity",
                "A",
                "side",
                "1",
                "qty",
                "500000",
                "price",
                "987500"));
    String payload = adapter.buildPayload(muni);
    assertThat(payload).startsWith("RTRS|ref=TRD-1|cusip=64966LMN8");
    assertThat(payload).contains("|par=500000").contains("capacity=A").contains("yield=388");
    assertThat(payload).isEqualTo(adapter.buildPayload(muni)); // deterministic

    InMemoryRegulatoryReportingService service =
        new InMemoryRegulatoryReportingService(RegulatorDeterminer.crossAssetUs());
    service.registerProfile(ReportingProfile.mock(Regulator.MSRB_RTRS));
    service.registerAdapter(adapter);
    List<RegReportEvent> events = service.report(muni, "fill");
    assertThat(events.get(events.size() - 1)).isInstanceOf(RegReportEvent.RegReportAcked.class);
  }

  @Test
  void sdr_utiIsDeterministic_suppliedWins_derivedNeverRandom() {
    ReportableTrade supplied =
        trade("IRS", "US", Map.of("uti", "LEI123-SWAP9", "reportingLei", "LEI123"));
    assertThat(CftcSdrAdapter.uti(supplied)).isEqualTo("LEI123-SWAP9");

    ReportableTrade derived = trade("IRS", "US", Map.of("reportingLei", "LEI123"));
    String uti = CftcSdrAdapter.uti(derived);
    assertThat(uti).startsWith("LEI123-");
    assertThat(uti).isEqualTo(CftcSdrAdapter.uti(derived)); // replay rebuilds the identical UTI

    String payload = CftcSdrAdapter.acking().buildPayload(derived);
    assertThat(payload).startsWith("SDR|uti=" + uti);
    assertThat(payload).contains("|assetClass=IRS").contains("|notional=500000");
  }

  @Test
  void rts22_identifiesByIsinAndBothLegs_offVenueDefaultsXoff() {
    Rts22Adapter adapter = Rts22Adapter.acking();
    ReportableTrade onVenue =
        trade(
            "EU_EQUITY",
            "EU",
            Map.of(
                "isin",
                "NL0010273215",
                "venueMic",
                "XAMS",
                "buyerLei",
                "LEI-B",
                "sellerLei",
                "LEI-S",
                "capacity",
                "DEAL"));
    String payload = adapter.buildPayload(onVenue);
    assertThat(payload).startsWith("RTS22|ref=TRD-1|isin=NL0010273215|venue=XAMS");
    assertThat(payload)
        .contains("buyerLei=LEI-B")
        .contains("sellerLei=LEI-S")
        .contains("capacity=DEAL");

    ReportableTrade offVenue = trade("EU_EQUITY", "EU", Map.of("isin", "NL0010273215"));
    assertThat(adapter.buildPayload(offVenue)).contains("|venue=XOFF");
  }

  @Test
  void jurisdictionRouting_usFirm_euFirm_dualHomed_unhomedFails() {
    JurisdictionRouter router =
        new JurisdictionRouter()
            .home("us-bd", RegulatorDeterminer.crossAssetUs())
            .home("eu-firm", RegulatorDeterminer.crossAssetEu())
            .home("global-firm", RegulatorDeterminer.crossAssetUs())
            .home("global-firm", RegulatorDeterminer.crossAssetEu()); // dual-homed

    ReportableTrade usMuni = trade("MUNI", "US", Map.of());
    ReportableTrade euEquity = trade("EU_EQUITY", "EU", Map.of());

    assertThat(router.applicableRegulators("us-bd", usMuni)).containsExactly(Regulator.MSRB_RTRS);
    assertThat(router.applicableRegulators("us-bd", euEquity)).isEmpty(); // not its matrix
    assertThat(router.applicableRegulators("eu-firm", euEquity))
        .containsExactly(Regulator.MIFIR_RTS22);

    // Dual-homed: the union — US munis AND EU equities both report.
    assertThat(router.applicableRegulators("global-firm", usMuni))
        .containsExactly(Regulator.MSRB_RTRS);
    assertThat(router.applicableRegulators("global-firm", euEquity))
        .containsExactly(Regulator.MIFIR_RTS22);

    // Unhomed firms fail LOUDLY — silently unreported trades are how reg findings happen.
    assertThatThrownBy(() -> router.applicableRegulators("ghost-firm", usMuni))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not homed");
  }

  @Test
  void usMatrix_nowRoutesMunisAndIrs() {
    RegulatorDeterminer us = RegulatorDeterminer.crossAssetUs();
    assertThat(us.applicableRegulators(trade("MUNI", "US", Map.of())))
        .containsExactly(Regulator.MSRB_RTRS);
    assertThat(us.applicableRegulators(trade("IRS", "US", Map.of())))
        .containsExactly(Regulator.CFTC_SDR);
  }

  @Test
  void markitServ_utiKeyedAllege_pair_affirm() {
    // The dealer alleges THEIR side of the swap under the UTI; we pair our trade ref to it.
    TradeRecord alleged =
        new TradeRecord(
            "DEALER-REF-9",
            "BBG00DEMOIR5",
            2,
            1_000_000L,
            4_0250L,
            0L,
            "2026-06-12",
            "2026-06-16",
            "DEALER-X");
    MarkitServNetwork network =
        new MarkitServNetwork()
            .withAllegedTrade("LEI123-SWAP9", alleged)
            .withUtiMapping("TRD-1", "LEI123-SWAP9");

    assertThat(network.name()).isEqualTo("MARKITSERV");
    assertThat(network.counterpartyRecord("TRD-1")).contains(alleged); // paired via UTI
    assertThat(network.counterpartyRecord("TRD-UNPAIRED")).isEmpty();
    assertThat(network.allegedTrade("LEI123-SWAP9")).contains(alleged);
    assertThat(network.affirm("ALLOC-1").affirmed()).isTrue();

    network.withAffirmation(
        "ALLOC-2", ConfirmationNetwork.AffirmationResponse.rejected("economics dispute"));
    assertThat(network.affirm("ALLOC-2").affirmed()).isFalse();
    assertThat(network.affirm("ALLOC-2").reason()).isEqualTo("economics dispute");
  }
}
