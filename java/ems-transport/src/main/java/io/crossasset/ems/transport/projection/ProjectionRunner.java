/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Runs a single {@link Projection} against an event log, maintaining state and enforcing
 * idempotency.
 *
 * <h3>Idempotency contract</h3>
 *
 * Records are accepted in monotonically increasing {@code globalSeq} order. {@link
 * #processRecord(LogRecord)} is a no-op when {@code record.globalSeq() <= lastConsumedGlobalSeq}.
 * This prevents double-application on the incremental path. The sentinel value {@code -1} means
 * "nothing consumed yet"; the first valid globalSeq from {@link
 * io.crossasset.ems.transport.log.EventLogWriter} is {@code 0}, so {@code 0 > -1} correctly
 * applies.
 *
 * <p>The guard advances {@code lastConsumedGlobalSeq} even for records that are not accepted by
 * {@link Projection#accepts}, ensuring that filtered-out events are not re-applied if re-delivered.
 *
 * <h3>Rebuild</h3>
 *
 * {@link #rebuild(Path)} resets state to {@link Projection#initialState()} and {@code
 * lastConsumedGlobalSeq} to {@code -1}, then reads the entire log and calls {@link #processRecord}
 * for each record. Calling rebuild twice from the same log produces the same state (determinism);
 * calling it after incremental processing produces the same final state (rebuild-equals-incremental
 * correctness).
 *
 * <p>Task 3.4 — Projection framework.
 *
 * @param <S> state type managed by the wrapped projection
 */
public class ProjectionRunner<S> {

  private final Projection<S> projection;
  private S state;
  private long lastConsumedGlobalSeq = -1L;

  public ProjectionRunner(Projection<S> projection) {
    this.projection = projection;
    this.state = projection.initialState();
  }

  /**
   * Processes a single record on the incremental path.
   *
   * <p>No-op if {@code record.globalSeq() <= lastConsumedGlobalSeq} (idempotency guard). Otherwise,
   * advances {@code lastConsumedGlobalSeq} and — if the record is accepted — applies the reducer.
   */
  public void processRecord(LogRecord record) {
    if (record.globalSeq() <= lastConsumedGlobalSeq) {
      return; // duplicate or out-of-order — skip
    }
    lastConsumedGlobalSeq = record.globalSeq();
    if (projection.accepts(record.streamId())) {
      state = projection.apply(state, record);
    }
  }

  /**
   * Rebuilds from scratch: resets to {@link Projection#initialState()} and reprocesses all records
   * in {@code logFile} from the beginning.
   *
   * @param logFile path to the event log written by {@link
   *     io.crossasset.ems.transport.log.EventLogWriter}
   * @throws IOException on I/O failure while reading the log
   */
  public void rebuild(Path logFile) throws IOException {
    state = projection.initialState();
    lastConsumedGlobalSeq = -1L;
    try (EventLogReader reader = new EventLogReader(logFile)) {
      for (LogRecord record : reader.readAll()) {
        processRecord(record);
      }
    }
  }

  /** The current projection state. */
  public S state() {
    return state;
  }

  /**
   * The {@code globalSeq} of the last record consumed (applied or filtered). {@code -1} if no
   * record has been processed yet.
   */
  public long lastConsumedGlobalSeq() {
    return lastConsumedGlobalSeq;
  }
}
