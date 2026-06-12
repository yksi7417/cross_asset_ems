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
 * Fat-finger cluster (12.15 baseline): one actor's NEW orders whose size is an outlier versus their
 * own median order size in the window ({@code ratioThreshold}× median). One outlier is a fat finger
 * the pre-trade gate should have caught (10.2 is deferred); a CLUSTER of them (≥ {@code
 * minOutliers}) means a systematic problem — broken algo, wrong-unit upload, or a compromised
 * session — and pages CRITICAL.
 */
public final class FatFingerClusterDetector implements Detector {

  private final long windowMicros;
  private final long ratioThreshold;
  private final int minOutliers;

  public FatFingerClusterDetector(long windowMicros, long ratioThreshold, int minOutliers) {
    this.windowMicros = windowMicros;
    this.ratioThreshold = ratioThreshold;
    this.minOutliers = minOutliers;
  }

  @Override
  public String id() {
    return "fat-finger-cluster";
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
    Map<String, List<SurveillanceEvent>> byActor = new LinkedHashMap<>();
    for (SurveillanceEvent event : window) {
      if (event.type() == SurveillanceEvent.Type.NEW_ORDER) {
        byActor.computeIfAbsent(event.actor(), k -> new ArrayList<>()).add(event);
      }
    }
    List<Alert> alerts = new ArrayList<>();
    for (Map.Entry<String, List<SurveillanceEvent>> entry : byActor.entrySet()) {
      List<SurveillanceEvent> orders = entry.getValue();
      if (orders.size() < 3) {
        continue; // a median over 1-2 orders is noise, not a baseline
      }
      long[] sizes = orders.stream().mapToLong(SurveillanceEvent::qty).sorted().toArray();
      long median = sizes[sizes.length / 2];
      if (median == 0) {
        continue;
      }
      List<SurveillanceEvent> outliers =
          orders.stream().filter(e -> e.qty() >= median * ratioThreshold).toList();
      if (outliers.size() >= minOutliers) {
        alerts.add(
            Alert.of(
                id(),
                version(),
                Alert.Severity.CRITICAL,
                entry.getKey(),
                outliers.get(0).instrumentId(),
                outliers.stream().map(SurveillanceEvent::eventId).toList(),
                window.get(0).tsMicros(),
                window.get(window.size() - 1).tsMicros(),
                outliers.size()
                    + " orders ≥ "
                    + ratioThreshold
                    + "× the actor's median size "
                    + median));
      }
    }
    return alerts;
  }
}
