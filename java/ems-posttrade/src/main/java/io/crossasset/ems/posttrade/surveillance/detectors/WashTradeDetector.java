/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance.detectors;

import io.crossasset.ems.posttrade.surveillance.Alert;
import io.crossasset.ems.posttrade.surveillance.Detector;
import io.crossasset.ems.posttrade.surveillance.SurveillanceEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wash trades (12.15 baseline): the same actor EXECUTES on both sides of the same instrument
 * within the window at overlapping prices — no change of beneficial ownership, prints volume.
 * Price overlap tolerance is {@code priceToleranceFp} (fixed-point 1e4); HIGH severity — wash
 * trading is rarely accidental.
 */
public final class WashTradeDetector implements Detector {

  private final long windowMicros;
  private final long priceToleranceFp;

  public WashTradeDetector(long windowMicros, long priceToleranceFp) {
    this.windowMicros = windowMicros;
    this.priceToleranceFp = priceToleranceFp;
  }

  @Override
  public String id() {
    return "wash-trade";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public long windowMicros() {
    return windowMicros;
  }

  @Override
  public List<Alert> evaluate(List<SurveillanceEvent> window) {
    Map<String, List<SurveillanceEvent>> groups = new LinkedHashMap<>();
    for (SurveillanceEvent event : window) {
      if (event.type() == SurveillanceEvent.Type.EXECUTION) {
        groups.computeIfAbsent(event.actor() + "|" + event.instrumentId(), k -> new ArrayList<>())
            .add(event);
      }
    }
    List<Alert> alerts = new ArrayList<>();
    for (List<SurveillanceEvent> execs : groups.values()) {
      List<SurveillanceEvent> buys = execs.stream().filter(e -> e.side() == 1).toList();
      List<SurveillanceEvent> sells = execs.stream().filter(e -> e.side() != 1).toList();
      List<String> evidence = new ArrayList<>();
      for (SurveillanceEvent buy : buys) {
        for (SurveillanceEvent sell : sells) {
          if (Math.abs(buy.price() - sell.price()) <= priceToleranceFp) {
            if (!evidence.contains(buy.eventId())) {
              evidence.add(buy.eventId());
            }
            if (!evidence.contains(sell.eventId())) {
              evidence.add(sell.eventId());
            }
          }
        }
      }
      if (!evidence.isEmpty()) {
        SurveillanceEvent first = execs.get(0);
        alerts.add(
            Alert.of(
                id(),
                version(),
                Alert.Severity.HIGH,
                first.actor(),
                first.instrumentId(),
                evidence,
                window.get(0).tsMicros(),
                window.get(window.size() - 1).tsMicros(),
                "actor executed both sides at overlapping prices ("
                    + evidence.size()
                    + " executions)"));
      }
    }
    return alerts;
  }
}
