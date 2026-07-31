/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

/**
 * Wall-clock time. The production default.
 *
 * <p>This is the only class in the business-logic tree permitted to read the system clock. Every
 * other component takes a {@link TimeSource} and is therefore testable and replayable;
 * concentrating the read here is what makes {@code scripts/ci/checks/no_raw_clock.py} enforceable
 * rather than aspirational.
 *
 * <p>Deliberately <em>not</em> a {@link Clock}: scheduling would require a timer thread, and a
 * component that only needs to know the time should not drag one in. See {@link TimeSource}.
 */
public final class SystemTimeSource implements TimeSource {

  /** The shared instance. Stateless, so sharing is free and avoids pointless allocation. */
  public static final SystemTimeSource INSTANCE = new SystemTimeSource();

  private SystemTimeSource() {}

  @Override
  public Timestamp now() {
    return Timestamp.ofEpochMillis(System.currentTimeMillis());
  }

  @Override
  public String toString() {
    return "SystemTimeSource";
  }
}
