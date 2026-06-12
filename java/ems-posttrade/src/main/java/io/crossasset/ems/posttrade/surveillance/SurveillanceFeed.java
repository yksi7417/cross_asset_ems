/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The surveillance feed (task 12.15, [[arch-surveillance]]): order/exec events are exported in
 * (ingested into the stream), detectors evaluate their look-back window on each arrival, and raised
 * alerts flow to the sink — the compliance-officer review queue; CRITICAL alerts are the
 * auto-freeze tier that compliance enforces on subsequent pre-trade gates.
 *
 * <p>Detection is asynchronous-by-design (it never blocks the order path) yet DETERMINISTIC:
 * event-time windows, pure detectors, dedup by content-derived alert id — replaying the same event
 * stream re-raises the identical alerts ([[arch-time-replay-server|Replay]]).
 */
public final class SurveillanceFeed {

  private final List<Detector> detectors = new ArrayList<>();
  private final Consumer<Alert> sink;
  private final List<SurveillanceEvent> stream = new ArrayList<>();
  private final List<Alert> raised = new ArrayList<>();
  private final java.util.Set<String> seenAlertIds = new java.util.HashSet<>();

  public SurveillanceFeed(Consumer<Alert> sink) {
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  public void register(Detector detector) {
    detectors.add(Objects.requireNonNull(detector));
  }

  /**
   * Export one order/exec event into the stream and run every detector over its window ending at
   * this event (event time, not wall clock). New alerts (unseen content id) hit the sink once.
   */
  public List<Alert> ingest(SurveillanceEvent event) {
    stream.add(event);
    List<Alert> fresh = new ArrayList<>();
    for (Detector detector : detectors) {
      long from = event.tsMicros() - detector.windowMicros();
      List<SurveillanceEvent> window =
          stream.stream()
              .filter(e -> e.tsMicros() >= from && e.tsMicros() <= event.tsMicros())
              .toList();
      for (Alert alert : detector.evaluate(window)) {
        if (seenAlertIds.add(alert.alertId())) {
          raised.add(alert);
          fresh.add(alert);
          sink.accept(alert);
        }
      }
    }
    return fresh;
  }

  /** The full exported event stream, in ingest order (the regulator/vendor export surface). */
  public List<SurveillanceEvent> exportStream() {
    return Collections.unmodifiableList(stream);
  }

  /** Every alert raised, in raise order. */
  public List<Alert> alerts() {
    return Collections.unmodifiableList(raised);
  }
}
