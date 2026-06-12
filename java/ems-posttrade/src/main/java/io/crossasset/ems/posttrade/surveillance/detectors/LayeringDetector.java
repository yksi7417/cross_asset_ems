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
 * Spoofing / layering (12.15 baseline, [[arch-surveillance]] § signal catalogue): cancel-heavy
 * activity on ONE side of an instrument with executions on the OTHER side, by the same actor,
 * inside the window. The canceled orders were never meant to trade — they were pressure.
 *
 * <p>Thresholds: ≥ {@code minCancels} cancels on the pressure side and ≥ 1 execution on the
 * opposite side. Severity scales with cancel count (≥ 3× threshold ⇒ HIGH).
 */
public final class LayeringDetector implements Detector {

  private final long windowMicros;
  private final int minCancels;

  public LayeringDetector(long windowMicros, int minCancels) {
    this.windowMicros = windowMicros;
    this.minCancels = minCancels;
  }

  @Override
  public String id() {
    return "layering";
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
    // Group by (actor, instrument) preserving event order — alert ids must be deterministic.
    Map<String, List<SurveillanceEvent>> groups = new LinkedHashMap<>();
    for (SurveillanceEvent event : window) {
      groups.computeIfAbsent(event.actor() + "|" + event.instrumentId(), k -> new ArrayList<>())
          .add(event);
    }
    List<Alert> alerts = new ArrayList<>();
    for (List<SurveillanceEvent> group : groups.values()) {
      for (int pressureSide : new int[] {1, 2}) {
        int oppositeSide = pressureSide == 1 ? 2 : 1;
        List<SurveillanceEvent> cancels =
            group.stream()
                .filter(e -> e.type() == SurveillanceEvent.Type.CANCEL && e.side() == pressureSide)
                .toList();
        List<SurveillanceEvent> oppositeExecs =
            group.stream()
                .filter(
                    e -> e.type() == SurveillanceEvent.Type.EXECUTION && e.side() == oppositeSide)
                .toList();
        if (cancels.size() >= minCancels && !oppositeExecs.isEmpty()) {
          List<String> evidence = new ArrayList<>();
          cancels.forEach(e -> evidence.add(e.eventId()));
          oppositeExecs.forEach(e -> evidence.add(e.eventId()));
          SurveillanceEvent first = group.get(0);
          alerts.add(
              Alert.of(
                  id(),
                  version(),
                  cancels.size() >= 3L * minCancels ? Alert.Severity.HIGH : Alert.Severity.MEDIUM,
                  first.actor(),
                  first.instrumentId(),
                  evidence,
                  window.get(0).tsMicros(),
                  window.get(window.size() - 1).tsMicros(),
                  cancels.size()
                      + " cancels side "
                      + pressureSide
                      + " vs "
                      + oppositeExecs.size()
                      + " executions side "
                      + oppositeSide));
        }
      }
    }
    return alerts;
  }
}
