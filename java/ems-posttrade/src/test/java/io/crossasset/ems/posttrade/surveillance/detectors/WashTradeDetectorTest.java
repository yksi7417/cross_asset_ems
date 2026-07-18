/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance.detectors;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.surveillance.Alert;
import io.crossasset.ems.posttrade.surveillance.SurveillanceEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class WashTradeDetectorTest {

  private final WashTradeDetector detector = new WashTradeDetector(60_000_000L, 5L);

  @Test
  void buyAndSellWithinTolerance_sameActorInstrument_raisesHighAlert() {
    var buy =
        new SurveillanceEvent(
            "E1", SurveillanceEvent.Type.EXECUTION, "trader-1", "BBG1", 1, 100L, 10_000L, 1_000L);
    var sell =
        new SurveillanceEvent(
            "E2", SurveillanceEvent.Type.EXECUTION, "trader-1", "BBG1", 2, 100L, 10_003L, 2_000L);

    List<Alert> alerts = detector.evaluate(List.of(buy, sell));

    assertThat(alerts).hasSize(1);
    Alert alert = alerts.get(0);
    assertThat(alert.severity()).isEqualTo(Alert.Severity.HIGH);
    assertThat(alert.subjectActor()).isEqualTo("trader-1");
    assertThat(alert.subjectEvents()).containsExactlyInAnyOrder("E1", "E2");
  }

  @Test
  void pricesOutsideTolerance_noAlert() {
    var buy =
        new SurveillanceEvent(
            "E1", SurveillanceEvent.Type.EXECUTION, "trader-1", "BBG1", 1, 100L, 10_000L, 1_000L);
    var sell =
        new SurveillanceEvent(
            "E2", SurveillanceEvent.Type.EXECUTION, "trader-1", "BBG1", 2, 100L, 10_100L, 2_000L);

    List<Alert> alerts = detector.evaluate(List.of(buy, sell));

    assertThat(alerts).isEmpty();
  }

  @Test
  void onlyBuys_noOffsettingSell_noAlert() {
    var buy1 =
        new SurveillanceEvent(
            "E1", SurveillanceEvent.Type.EXECUTION, "trader-1", "BBG1", 1, 100L, 10_000L, 1_000L);
    var buy2 =
        new SurveillanceEvent(
            "E2", SurveillanceEvent.Type.EXECUTION, "trader-1", "BBG1", 1, 100L, 10_000L, 2_000L);

    List<Alert> alerts = detector.evaluate(List.of(buy1, buy2));

    assertThat(alerts).isEmpty();
  }

  @Test
  void differentActorsOrInstruments_noAlert() {
    var buy =
        new SurveillanceEvent(
            "E1", SurveillanceEvent.Type.EXECUTION, "trader-A", "BBG1", 1, 100L, 10_000L, 1_000L);

    var sellDifferentActor =
        new SurveillanceEvent(
            "E2", SurveillanceEvent.Type.EXECUTION, "trader-B", "BBG1", 2, 100L, 10_003L, 2_000L);
    assertThat(detector.evaluate(List.of(buy, sellDifferentActor))).isEmpty();

    var sellDifferentInstrument =
        new SurveillanceEvent(
            "E3", SurveillanceEvent.Type.EXECUTION, "trader-A", "BBG2", 2, 100L, 10_003L, 2_000L);
    assertThat(detector.evaluate(List.of(buy, sellDifferentInstrument))).isEmpty();
  }

  @Test
  void nonExecutionEvents_ignored() {
    var orderBuy =
        new SurveillanceEvent(
            "E1", SurveillanceEvent.Type.NEW_ORDER, "trader-1", "BBG1", 1, 100L, 10_000L, 1_000L);
    var orderSell =
        new SurveillanceEvent(
            "E2", SurveillanceEvent.Type.NEW_ORDER, "trader-1", "BBG1", 2, 100L, 10_003L, 2_000L);

    List<Alert> alerts = detector.evaluate(List.of(orderBuy, orderSell));

    assertThat(alerts).isEmpty();
  }

  @Test
  void emptyWindow_noAlerts() {
    List<Alert> alerts = detector.evaluate(List.of());

    assertThat(alerts).isEmpty();
  }

  @Test
  void metadata() {
    assertThat(detector.id()).isEqualTo("wash-trade");
    assertThat(detector.version()).isEqualTo(1);
  }
}
