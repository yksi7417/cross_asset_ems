/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs one or more {@link Projection}s over a bounded slice of the event log, enforcing the core
 * replay determinism guarantee: given the same log and the same {@link ReplaySlice}, the output is
 * always identical.
 *
 * <h3>Determinism contract</h3>
 *
 * <ul>
 *   <li>Records are presented to runners in {@code globalSeq} order (log file order).
 *   <li>Only records whose {@code globalSeq} falls within the slice are presented.
 *   <li>No wall-clock access, no non-deterministic identifiers, no external calls.
 *   <li>{@link Projection} reducers must be pure functions — same input → same output.
 * </ul>
 *
 * <h3>Multi-projection efficiency</h3>
 *
 * Use {@link #replay(List, ReplaySlice)} to feed multiple runners through the same slice in a
 * single log read. Each runner's state is independent; the result is identical to separate {@link
 * #replay(Projection, ReplaySlice)} calls.
 *
 * <p>Task 3.5 — Replay engine (log slice → re-derive state).
 */
public class ReplayEngine {

  private final Path logFile;

  public ReplayEngine(Path logFile) {
    this.logFile = logFile;
  }

  /**
   * Runs a single projection over the given slice and returns the typed result.
   *
   * <p>A fresh {@link ProjectionRunner} is created, starting from {@link
   * Projection#initialState()}.
   *
   * @param projection the projection to run
   * @param slice bounded log range (inclusive on both ends)
   * @return result containing final state, events-processed count, and last consumed globalSeq
   * @throws IOException on I/O failure reading the log
   */
  public <S> ReplayResult<S> replay(Projection<S> projection, ReplaySlice slice)
      throws IOException {
    ProjectionRunner<S> runner = new ProjectionRunner<>(projection);
    long eventsProcessed = feed(List.of(runner), slice);
    return new ReplayResult<>(runner.state(), eventsProcessed, runner.lastConsumedGlobalSeq());
  }

  /**
   * Feeds all runners through the same slice in a single log pass.
   *
   * <p>Runners are <em>not</em> reset — callers should pass freshly created runners to replay from
   * scratch. Each record in the slice is presented to every runner in order, so each runner
   * advances independently. Equivalent to separate {@link #replay} calls but performs one log read.
   *
   * @param runners projection runners to advance
   * @param slice bounded log range (inclusive on both ends)
   * @throws IOException on I/O failure reading the log
   */
  public void replay(List<? extends ProjectionRunner<?>> runners, ReplaySlice slice)
      throws IOException {
    feed(runners, slice);
  }

  private long feed(List<? extends ProjectionRunner<?>> runners, ReplaySlice slice)
      throws IOException {
    long eventsProcessed = 0;
    try (EventLogReader reader = new EventLogReader(logFile)) {
      for (LogRecord record : reader.readFrom(slice.fromGlobalSeq())) {
        if (record.globalSeq() > slice.toGlobalSeq()) {
          break;
        }
        for (ProjectionRunner<?> runner : runners) {
          runner.processRecord(record);
        }
        eventsProcessed++;
      }
    }
    return eventsProcessed;
  }
}
