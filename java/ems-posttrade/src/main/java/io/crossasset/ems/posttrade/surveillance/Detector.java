/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import java.util.List;

/**
 * A surveillance detector (task 12.15, [[arch-surveillance]] § Detector model): a PURE projection
 * over the events in its window — no clock reads, no I/O, no state outside the arguments — so the
 * same detector version over the same window reproduces identical alerts under replay. Detectors
 * are versioned like FSMs; bump {@link #version()} on any behavior change.
 */
public interface Detector {

  String id();

  int version();

  /** The look-back window this detector evaluates over. */
  long windowMicros();

  /**
   * Evaluate one window of events (already filtered to the window, in event-time order) and return
   * the alerts it raises — possibly none.
   */
  List<Alert> evaluate(List<SurveillanceEvent> window);
}
