/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

/**
 * An immutable point in time, measured in milliseconds since the Unix epoch (UTC).
 *
 * <p>All {@link Clock} implementations use this unit. When {@code occurred_at} lands in the event
 * envelope it must also use epoch-milliseconds so that simulated-clock advancement can be wired
 * directly to event time without a unit conversion.
 *
 * <p>Task 3.6 — sim-clock interface.
 */
public record Timestamp(long epochMillis) implements Comparable<Timestamp> {

  public static Timestamp ofEpochMillis(long millis) {
    return new Timestamp(millis);
  }

  public boolean isBefore(Timestamp other) {
    return epochMillis < other.epochMillis;
  }

  public boolean isAfter(Timestamp other) {
    return epochMillis > other.epochMillis;
  }

  public Timestamp plusMillis(long millis) {
    return new Timestamp(epochMillis + millis);
  }

  @Override
  public int compareTo(Timestamp other) {
    return Long.compare(this.epochMillis, other.epochMillis);
  }

  @Override
  public String toString() {
    return "Timestamp(" + epochMillis + "ms)";
  }
}
