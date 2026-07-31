/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.crossasset.ems.core.journal.JournalCodec;
import io.crossasset.ems.core.journal.JournalEvent;
import io.crossasset.ems.core.journal.MalformedJournalException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrored by {@code rust/ems-transport/src/journal_transport.rs} and {@code
 * cpp/ems-transport/test/journal_transport_test.cpp}.
 */
class JournalTransportTest {

  @TempDir Path tmp;

  private Path input() {
    return tmp.resolve("in.jsonl");
  }

  private Path output() {
    return tmp.resolve("out.jsonl");
  }

  private static JournalEvent event(long seq, String type) {
    return new JournalEvent(seq, type, new TreeMap<>());
  }

  @Test
  void drainReturnsTheInputJournalOnceAndThenEmpty() throws IOException {
    JournalCodec.write(input(), List.of(event(1, "OrderNew"), event(2, "Heartbeat")));
    JournalTransport transport = new JournalTransport(input(), output());

    assertThat(transport.drain()).hasSize(2);
    // Draining twice is a programming error; replaying would silently duplicate
    // an order rather than surfacing it.
    assertThat(transport.drain()).isEmpty();
  }

  @Test
  void publishedEventsAppearInOrderAfterFlush() throws IOException {
    JournalCodec.write(input(), List.of());
    try (JournalTransport transport = new JournalTransport(input(), output())) {
      transport.publish(event(1, "A"));
      transport.publish(event(2, "B"));
      transport.flush();
    }

    assertThat(JournalCodec.read(output()))
        .extracting(JournalEvent::type)
        .containsExactly("A", "B");
  }

  @Test
  void publishBeforeFlushWritesNothing() throws IOException {
    JournalCodec.write(input(), List.of());
    JournalTransport transport = new JournalTransport(input(), output());
    transport.publish(event(1, "A"));

    // A half-written journal is indistinguishable from a legitimately short
    // one, which would send a reader hunting for a logic bug that is not there.
    assertThat(Files.exists(output())).isFalse();
  }

  @Test
  void closeFlushes() throws IOException {
    JournalCodec.write(input(), List.of());
    JournalTransport transport = new JournalTransport(input(), output());
    transport.publish(event(1, "A"));
    transport.close();

    assertThat(JournalCodec.read(output())).hasSize(1);
  }

  @Test
  void closeIsIdempotent() throws IOException {
    JournalCodec.write(input(), List.of());
    JournalTransport transport = new JournalTransport(input(), output());
    transport.publish(event(1, "A"));
    transport.close();
    transport.close();

    assertThat(JournalCodec.read(output())).hasSize(1);
  }

  @Test
  void publishAfterCloseIsRejected() throws IOException {
    JournalCodec.write(input(), List.of());
    JournalTransport transport = new JournalTransport(input(), output());
    transport.close();

    assertThatThrownBy(() -> transport.publish(event(1, "A")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void flushWithNothingPublishedWritesAnEmptyFile() throws IOException {
    JournalCodec.write(input(), List.of());
    try (JournalTransport transport = new JournalTransport(input(), output())) {
      transport.flush();
    }

    assertThat(Files.readString(output(), StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void flushIsIdempotentRatherThanAppending() throws IOException {
    JournalCodec.write(input(), List.of());
    try (JournalTransport transport = new JournalTransport(input(), output())) {
      transport.publish(event(1, "A"));
      transport.flush();
      transport.flush();
    }

    // Appending on the second flush would duplicate the whole journal — and the
    // conformance differ would report it as an "extra line" ten lines later.
    assertThat(JournalCodec.read(output())).hasSize(1);
  }

  @Test
  void malformedInputSurfacesAsMalformedJournalException() throws IOException {
    Files.writeString(input(), "not json\n");
    JournalTransport transport = new JournalTransport(input(), output());

    assertThatThrownBy(transport::drain)
        .isInstanceOf(MalformedJournalException.class)
        .hasMessageContaining("line 1");
  }

  @Test
  void missingInputSurfacesAsUncheckedIoException() {
    JournalTransport transport = new JournalTransport(tmp.resolve("nope.jsonl"), output());

    assertThatThrownBy(transport::drain).isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void drainedEventsCannotBeMutatedByTheCaller() throws IOException {
    JournalCodec.write(input(), List.of(event(1, "A")));
    JournalTransport transport = new JournalTransport(input(), output());
    List<JournalEvent> drained = transport.drain();

    assertThatThrownBy(() -> drained.add(event(2, "B")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
