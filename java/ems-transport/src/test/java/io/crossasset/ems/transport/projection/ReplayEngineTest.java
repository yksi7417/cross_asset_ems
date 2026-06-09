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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the replay engine: determinism guarantee, slice boundary precision, empty slices,
 * multi-projection single-pass equality, and full-replay-equals-rebuild anchor.
 *
 * <p>Uses an order-sensitive projection ({@code List<Long>} of globalSeqs in seen order) so the
 * determinism tests exercise event ordering, not just counting. A counter would pass even if the
 * engine fed records in a non-deterministic or sorted-differently order.
 *
 * <p>Task 3.5 — Replay engine.
 */
class ReplayEngineTest {

  @TempDir Path tempDir;

  // ── helpers ─────────────────────────────────────────────────────────────────

  private static void writeRecords(EventLogWriter writer, StreamId streamId, int count)
      throws IOException {
    for (int i = 0; i < count; i++) {
      byte[] bytes = ("ev-" + i).getBytes(StandardCharsets.UTF_8);
      UnsafeBuffer buf = new UnsafeBuffer(ByteBuffer.wrap(bytes));
      writer.append(streamId, buf, 0, bytes.length);
    }
  }

  /**
   * Order-sensitive projection: accumulates a {@code List<Long>} of globalSeqs in the order they
   * were accepted. Detects ordering inversions, boundary mis-fires, and duplicate delivery that a
   * counting projection cannot catch.
   */
  private static Projection<List<Long>> sequenceProjection(
      String name, Predicate<StreamId> filter) {
    return new Projection<>() {
      public String name() {
        return name;
      }

      public int version() {
        return 1;
      }

      public List<Long> initialState() {
        return List.of();
      }

      public boolean accepts(StreamId s) {
        return filter.test(s);
      }

      public List<Long> apply(List<Long> state, LogRecord r) {
        List<Long> next = new ArrayList<>(state);
        next.add(r.globalSeq());
        return next;
      }
    };
  }

  // ── determinism ─────────────────────────────────────────────────────────────

  /**
   * Golden-replay guarantee: replaying the same log slice twice with independent runners produces
   * byte-identical derived state, including event order. This is the architectural invariant that
   * makes event-sourced replay correct.
   */
  @Test
  void determinism_sameSlice_producesIdenticalOrderedState() throws IOException {
    Path log = tempDir.resolve("log.bin");
    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 5);
    }

    ReplayEngine engine = new ReplayEngine(log);
    Projection<List<Long>> seq = sequenceProjection("seq", s -> true);

    ReplayResult<List<Long>> run1 = engine.replay(seq, ReplaySlice.all());
    ReplayResult<List<Long>> run2 = engine.replay(seq, ReplaySlice.all());

    assertEquals(
        run1.finalState(),
        run2.finalState(),
        "Same log slice must produce byte-identical derived state (golden replay guarantee)");
    assertEquals(List.of(0L, 1L, 2L, 3L, 4L), run1.finalState());
  }

  // ── slice boundary ──────────────────────────────────────────────────────────

  /**
   * Both slice ends are inclusive. Asserts the exact globalSeq sequence for slice [2,4] over a
   * 6-record log, catching off-by-one errors at both the lower and upper boundary simultaneously.
   */
  @Test
  void sliceBoundary_bothEndsInclusive_exactMatch() throws IOException {
    Path log = tempDir.resolve("log.bin");
    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 6); // globalSeqs 0..5
    }

    ReplayEngine engine = new ReplayEngine(log);
    Projection<List<Long>> seq = sequenceProjection("seq", s -> true);

    ReplayResult<List<Long>> result = engine.replay(seq, ReplaySlice.between(2, 4));

    assertEquals(
        List.of(2L, 3L, 4L),
        result.finalState(),
        "Slice [2,4] must contain exactly globalSeqs 2, 3, 4 (both ends inclusive)");
    assertEquals(3, result.eventsProcessed());
    assertEquals(4L, result.lastConsumedGlobalSeq());
  }

  // ── empty slice ─────────────────────────────────────────────────────────────

  @Test
  void emptySlice_noMatchingEvents_returnsInitialState() throws IOException {
    Path log = tempDir.resolve("log.bin");
    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 5); // globalSeqs 0..4
    }

    ReplayEngine engine = new ReplayEngine(log);
    Projection<List<Long>> seq = sequenceProjection("seq", s -> true);

    ReplayResult<List<Long>> result = engine.replay(seq, ReplaySlice.between(100, 200));

    assertEquals(seq.initialState(), result.finalState());
    assertEquals(0, result.eventsProcessed());
    assertEquals(-1L, result.lastConsumedGlobalSeq(), "Nothing consumed — sentinel must remain -1");
  }

  // ── multi-projection ─────────────────────────────────────────────────────────

  /**
   * A single-pass multi-projection replay must produce identical final state to two independent
   * separate-pass replays. It is an efficiency wrapper, not a different algorithm.
   */
  @Test
  void multiProjection_singlePassMatchesSeparatePasses() throws IOException {
    Path log = tempDir.resolve("log.bin");
    StreamId orderStream = StreamId.of("order", "o1");
    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 2); // globalSeqs 0, 1
      writeRecords(writer, orderStream, 2); // globalSeqs 2, 3
      writeRecords(writer, StreamId.ADMIN, 1); // globalSeq  4
    }

    ReplayEngine engine = new ReplayEngine(log);
    Projection<List<Long>> adminSeq = sequenceProjection("admin", StreamId.ADMIN::equals);
    Projection<List<Long>> orderSeq = sequenceProjection("order", orderStream::equals);

    // Single-pass: both runners fed in one log read
    ProjectionRunner<List<Long>> adminRunner = new ProjectionRunner<>(adminSeq);
    ProjectionRunner<List<Long>> orderRunner = new ProjectionRunner<>(orderSeq);
    engine.replay(List.of(adminRunner, orderRunner), ReplaySlice.all());

    // Separate passes: each projection replayed independently
    ReplayResult<List<Long>> adminSeparate = engine.replay(adminSeq, ReplaySlice.all());
    ReplayResult<List<Long>> orderSeparate = engine.replay(orderSeq, ReplaySlice.all());

    assertEquals(
        adminSeparate.finalState(),
        adminRunner.state(),
        "Admin projection: single-pass must equal separate-pass");
    assertEquals(
        orderSeparate.finalState(),
        orderRunner.state(),
        "Order projection: single-pass must equal separate-pass");
    assertEquals(List.of(0L, 1L, 4L), adminRunner.state());
    assertEquals(List.of(2L, 3L), orderRunner.state());
  }

  // ── full replay == rebuild ───────────────────────────────────────────────────

  /**
   * Cross-task anchor: {@link ReplaySlice#all()} must produce the same ordered state as {@link
   * ProjectionRunner#rebuild(Path)}, which was validated in {@code ProjectionFrameworkTest}. If
   * this test regresses, either task 3.4 or 3.5 broke its contract.
   */
  @Test
  void fullReplay_equalsRebuildFromScratch() throws IOException {
    Path log = tempDir.resolve("log.bin");
    try (EventLogWriter writer = new EventLogWriter(log)) {
      writeRecords(writer, StreamId.ADMIN, 3);
      writeRecords(writer, StreamId.of("order", "o1"), 2);
    }

    Projection<List<Long>> seq = sequenceProjection("seq", s -> true);

    ReplayEngine engine = new ReplayEngine(log);
    ReplayResult<List<Long>> replayResult = engine.replay(seq, ReplaySlice.all());

    ProjectionRunner<List<Long>> runner = new ProjectionRunner<>(seq);
    runner.rebuild(log);

    assertEquals(
        runner.state(),
        replayResult.finalState(),
        "ReplaySlice.all() must produce the same ordered state as ProjectionRunner.rebuild()");
  }
}
