/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.transport.log.EventLogWriter;
import io.crossasset.ems.transport.log.StreamId;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the projection framework: EventLogReader round-trip, ProjectionRunner rebuild,
 * idempotency (incremental duplicate), and stream-filter isolation.
 *
 * <p>Task 3.4 — Projection framework.
 */
class ProjectionFrameworkTest {

  @TempDir Path tempDir;

  // ── helpers ─────────────────────────────────────────────────────────────────

  /** Write {@code count} events to {@code streamId}; payload is "ev-<i>". */
  private static void writeRecords(EventLogWriter writer, StreamId streamId, int count)
      throws IOException {
    for (int i = 0; i < count; i++) {
      byte[] bytes = ("ev-" + i).getBytes(StandardCharsets.UTF_8);
      // Must wrap via ByteBuffer so UnsafeBuffer.byteBuffer() is non-null (EventLogWriter calls it)
      UnsafeBuffer buf = new UnsafeBuffer(ByteBuffer.wrap(bytes));
      writer.append(streamId, buf, 0, bytes.length);
    }
  }

  /** Counting projection: increments an Integer counter for every accepted event. */
  private static Projection<Integer> counter(String name, Predicate<StreamId> filter) {
    return new Projection<>() {
      public String name() {
        return name;
      }

      public int version() {
        return 1;
      }

      public Integer initialState() {
        return 0;
      }

      public boolean accepts(StreamId s) {
        return filter.test(s);
      }

      public Integer apply(Integer state, LogRecord r) {
        return state + 1;
      }
    };
  }

  // ── round-trip ──────────────────────────────────────────────────────────────

  @Test
  void reader_roundTrip_parsesWriterOutput() throws IOException {
    Path log = tempDir.resolve("log.bin");

    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 2);
      writeRecords(writer, StreamId.of("order", "o1"), 1);
    }

    try (EventLogReader reader = new EventLogReader(log)) {
      List<LogRecord> records = reader.readAll();
      assertEquals(3, records.size());
      assertEquals(0L, records.get(0).globalSeq());
      assertEquals(StreamId.ADMIN, records.get(0).streamId());
      assertEquals("ev-0", new String(records.get(0).payload(), StandardCharsets.UTF_8));
      assertEquals(1L, records.get(1).globalSeq());
      assertEquals(StreamId.ADMIN, records.get(1).streamId());
      assertEquals(2L, records.get(2).globalSeq());
      assertEquals(StreamId.of("order", "o1"), records.get(2).streamId());
    }
  }

  @Test
  void reader_readFrom_skipsEarlierRecords() throws IOException {
    Path log = tempDir.resolve("log.bin");

    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 5);
    }

    try (EventLogReader reader = new EventLogReader(log)) {
      List<LogRecord> tail = reader.readFrom(3L);
      assertEquals(2, tail.size());
      assertEquals(3L, tail.get(0).globalSeq());
      assertEquals(4L, tail.get(1).globalSeq());
    }
  }

  // ── rebuild ─────────────────────────────────────────────────────────────────

  @Test
  void rebuild_producesIdenticalStateToIncremental() throws IOException {
    Path log = tempDir.resolve("log.bin");

    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 3);
      writeRecords(writer, StreamId.of("order", "o1"), 2);
    }

    Projection<Integer> adminCounter = counter("admin-counter", StreamId.ADMIN::equals);

    // Incremental path: feed records one by one
    ProjectionRunner<Integer> incremental = new ProjectionRunner<>(adminCounter);
    try (EventLogReader reader = new EventLogReader(log)) {
      for (LogRecord rec : reader.readAll()) {
        incremental.processRecord(rec);
      }
    }

    // Rebuild path: reset and replay the whole log
    ProjectionRunner<Integer> rebuilt = new ProjectionRunner<>(adminCounter);
    rebuilt.rebuild(log);

    assertEquals(incremental.state(), rebuilt.state());
    assertEquals(3, rebuilt.state(), "admin counter must equal number of admin events written");
  }

  @Test
  void rebuild_twice_producesSameState() throws IOException {
    Path log = tempDir.resolve("log.bin");

    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 4);
    }

    Projection<Integer> p = counter("c", s -> true);
    ProjectionRunner<Integer> runner = new ProjectionRunner<>(p);

    runner.rebuild(log);
    Integer first = runner.state();

    runner.rebuild(log);
    assertEquals(first, runner.state(), "rebuild is deterministic: same log → same state");
  }

  @Test
  void rebuild_emptyLog_returnsInitialState() throws IOException {
    Path log = tempDir.resolve("empty.bin");
    log.toFile().createNewFile();

    Projection<Integer> p = counter("c", s -> true);
    ProjectionRunner<Integer> runner = new ProjectionRunner<>(p);
    runner.rebuild(log);

    assertEquals(p.initialState(), runner.state());
    assertEquals(-1L, runner.lastConsumedGlobalSeq());
  }

  // ── idempotency ─────────────────────────────────────────────────────────────

  /**
   * The discriminating idempotency test: re-feeding the SAME record on the incremental path must be
   * a no-op. A missing or broken dedup guard (e.g. {@code >=} vs {@code >}) would double-count and
   * fail this assertion.
   */
  @Test
  void processRecord_incrementalDuplicate_isNoOp() throws IOException {
    Path log = tempDir.resolve("log.bin");

    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 3);
    }

    Projection<Integer> p = counter("c", s -> true);
    ProjectionRunner<Integer> runner = new ProjectionRunner<>(p);

    try (EventLogReader reader = new EventLogReader(log)) {
      List<LogRecord> records = reader.readAll();

      // Apply all three records
      for (LogRecord rec : records) {
        runner.processRecord(rec);
      }
      int stateAfterThree = runner.state();
      assertEquals(3, stateAfterThree);

      // Re-apply every record — must all be no-ops
      for (LogRecord rec : records) {
        runner.processRecord(rec);
      }
      assertEquals(
          stateAfterThree,
          runner.state(),
          "Re-delivering records with already-seen globalSeq must not change state (idempotency)");
    }
  }

  // ── stream filter ────────────────────────────────────────────────────────────

  @Test
  void streamFilter_projectionsAreIsolated() throws IOException {
    Path log = tempDir.resolve("log.bin");

    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 2);
      writeRecords(writer, StreamId.of("order", "o1"), 3);
      writeRecords(writer, StreamId.ADMIN, 1); // total admin = 3
    }

    Projection<Integer> adminP = counter("admin", StreamId.ADMIN::equals);
    Projection<Integer> orderP = counter("order", s -> s.value().startsWith("order."));

    ProjectionRunner<Integer> adminRunner = new ProjectionRunner<>(adminP);
    ProjectionRunner<Integer> orderRunner = new ProjectionRunner<>(orderP);

    adminRunner.rebuild(log);
    orderRunner.rebuild(log);

    assertEquals(3, adminRunner.state(), "admin projection must count only admin events");
    assertEquals(3, orderRunner.state(), "order projection must count only order events");
  }

  @Test
  void filteredRecords_stillAdvanceLastConsumedGlobalSeq() throws IOException {
    Path log = tempDir.resolve("log.bin");

    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.of("order", "o1"), 3);
    }

    // admin-only projection — none of the 3 records are accepted
    Projection<Integer> adminP = counter("admin-only", StreamId.ADMIN::equals);
    ProjectionRunner<Integer> runner = new ProjectionRunner<>(adminP);
    runner.rebuild(log);

    assertEquals(0, runner.state(), "no accepted events → state stays at initialState");
    assertEquals(
        2L,
        runner.lastConsumedGlobalSeq(),
        "lastConsumedGlobalSeq advances even for filtered records");
  }
}
