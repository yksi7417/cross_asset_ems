/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

/**
 * Where the current time comes from.
 *
 * <p>Split out of {@link Clock} because almost every component that needs time needs only "what
 * time is it" — not scheduling. Requiring those components to depend on {@code schedule} and {@code
 * schedulePeriodic} would force every wall-clock implementation to own a timer thread, which is why
 * no live {@code Clock} existed and callers reached for {@code System.currentTimeMillis()} instead.
 *
 * <h3>Implementations</h3>
 *
 * <ul>
 *   <li>{@link SystemTimeSource} — wall clock. The production default.
 *   <li>{@link FixedTimeSource} — a stub that returns whatever it was last set to. For tests.
 *   <li>{@link SimulatedClock} — deterministic and manually advanced; also a full {@link Clock}.
 * </ul>
 *
 * <p>Business logic must <em>never</em> call {@code System.currentTimeMillis()} or {@code
 * System.nanoTime()} directly. {@code scripts/ci/checks/no_raw_clock.py} enforces that against a
 * baseline, so the rule is checked rather than merely documented.
 */
public interface TimeSource {

  /** The current time. Never returns a value less than a prior call on the same instance. */
  Timestamp now();

  /**
   * The current time in microseconds since the epoch.
   *
   * <p>On the interface rather than at each call site because most callers previously wrote {@code
   * System.currentTimeMillis() * 1_000L} by hand — a unit conversion repeated in five places is a
   * unit conversion that will eventually be wrong in one of them.
   */
  default long nowMicros() {
    return now().epochMillis() * 1_000L;
  }
}
