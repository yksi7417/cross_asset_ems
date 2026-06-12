/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.algo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.crossasset.ems.fix.FixMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 11.16: FIXatdl ingestion, ticket-side validation, and the 847/957-group wire encoding that makes
 * a broker algo routable with custom parameters.
 */
class AlgoCatalogTest {

  private static final String FIXATDL =
      """
      <Strategies xmlns="http://www.fixprotocol.org/FIXatdl-1-1/Core">
        <Strategy name="VWAP" wireValue="GSVWAP">
          <Parameter name="StartTime" xsi:type="UTCTimestamp_t" use="required"/>
          <Parameter name="EndTime" xsi:type="UTCTimestamp_t" use="required"/>
          <Parameter name="MaxPctVolume" xsi:type="Percentage_t" use="optional"
                     minValue="1" maxValue="50" initValue="10"/>
          <Parameter name="Style" xsi:type="Char_t" use="optional">
            <EnumPair wireValue="P"/>
            <EnumPair wireValue="A"/>
          </Parameter>
        </Strategy>
        <Strategy name="TWAP" wireValue="GSTWAP">
          <Parameter name="Duration" xsi:type="Int_t" use="required" minValue="1" maxValue="480"/>
        </Strategy>
      </Strategies>
      """;

  @Test
  void fixatdlIngestion_loadsStrategiesTypesBoundsEnumsAndDefaults() {
    AlgoCatalog catalog = new AlgoCatalog();
    List<AlgoStrategy> loaded = catalog.ingestFixatdl("GS", FIXATDL);

    assertThat(loaded).hasSize(2);
    assertThat(catalog.strategies("GS"))
        .extracting(AlgoStrategy::name)
        .containsExactly("VWAP", "TWAP");

    AlgoStrategy vwap = catalog.find("GS", "GSVWAP").orElseThrow();
    assertThat(vwap.parameters()).hasSize(4);
    AlgoStrategy.Parameter maxPct = vwap.parameters().get(2);
    assertThat(maxPct.type()).isEqualTo(AlgoStrategy.ValueType.PERCENTAGE);
    assertThat(maxPct.minValue()).isEqualTo(1L);
    assertThat(maxPct.maxValue()).isEqualTo(50L);
    assertThat(maxPct.defaultValue()).isEqualTo("10"); // the ticket's prefill
    assertThat(vwap.parameters().get(3).enumValues()).containsExactly("P", "A");
  }

  @Test
  void validation_required_bounds_enums_unknowns() {
    AlgoCatalog catalog = new AlgoCatalog();
    catalog.ingestFixatdl("GS", FIXATDL);
    AlgoStrategy vwap = catalog.find("GS", "GSVWAP").orElseThrow();

    assertThat(vwap.validate(Map.of("StartTime", "t0", "EndTime", "t1"))).isEmpty();
    assertThat(vwap.validate(Map.of("StartTime", "t0"))).containsExactly("EndTime: required");
    assertThat(vwap.validate(Map.of("StartTime", "t0", "EndTime", "t1", "MaxPctVolume", "90")))
        .containsExactly("MaxPctVolume: 90 > max 50");
    assertThat(vwap.validate(Map.of("StartTime", "t0", "EndTime", "t1", "Style", "X")))
        .containsExactly("Style: X not in [P, A]");
    // Unknown params are violations: a silently-dropped parameter changes the algo's behavior.
    assertThat(vwap.validate(Map.of("StartTime", "t0", "EndTime", "t1", "Urgency", "HIGH")))
        .containsExactly("Urgency: unknown parameter for strategy VWAP");
  }

  @Test
  void wireEncoding_847PlusOrdered957Group_deterministic() {
    AlgoCatalog catalog = new AlgoCatalog();
    catalog.ingestFixatdl("GS", FIXATDL);
    AlgoStrategy vwap = catalog.find("GS", "GSVWAP").orElseThrow();
    Map<String, String> values =
        Map.of(
            "StartTime", "20260612-14:30:00", "EndTime", "20260612-21:00:00", "MaxPctVolume", "15");

    String fix =
        StrategyParameterEncoder.encode(
                FixMessage.builder().field(35, "D").field(49, "EMS").field(56, "GS"), vwap, values)
            .build();

    assertThat(fix).contains("847=GSVWAP");
    assertThat(fix).contains("957=3");
    // Group entries ride in PARAMETER-DEFINITION order regardless of map iteration order.
    int start = fix.indexOf("958=StartTime");
    int end = fix.indexOf("958=EndTime");
    int pct = fix.indexOf("958=MaxPctVolume");
    assertThat(start).isLessThan(end);
    assertThat(end).isLessThan(pct);
    assertThat(fix).contains("959=16"); // UTCTimestamp
    assertThat(fix).contains("960=15");

    // Determinism: same inputs, same bytes.
    String again =
        StrategyParameterEncoder.encode(
                FixMessage.builder().field(35, "D").field(49, "EMS").field(56, "GS"), vwap, values)
            .build();
    assertThat(again).isEqualTo(fix);
  }

  @Test
  void encoderRefusesInvalidParameters_neverShipsAMalformedAlgoOrder() {
    AlgoCatalog catalog = new AlgoCatalog();
    catalog.ingestFixatdl("GS", FIXATDL);
    AlgoStrategy twap = catalog.find("GS", "GSTWAP").orElseThrow();

    assertThatThrownBy(
            () ->
                StrategyParameterEncoder.encode(
                    FixMessage.builder().field(35, "D"), twap, Map.of("Duration", "999")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duration: 999 > max 480");
  }

  @Test
  void doctypeIsRejected_brokerFilesAreExternalInput() {
    AlgoCatalog catalog = new AlgoCatalog();
    assertThatThrownBy(
            () ->
                catalog.ingestFixatdl(
                    "EVIL",
                    "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><Strategies/>"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
