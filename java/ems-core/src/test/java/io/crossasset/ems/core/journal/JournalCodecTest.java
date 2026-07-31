/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The byte layout asserted here is a cross-language contract, not an implementation detail: the
 * Rust and C++ ports are checked against Java's output byte-for-byte by the conformance gate. A
 * change to any expectation in this file is a change to that contract.
 *
 * <p>See conformance/README.md and docs/decisions/0003-shared-schemas-corpus-harness.md.
 */
class JournalCodecTest {

  @TempDir Path tmp;

  private static JournalEvent event(long seq, String type, Map<String, String> fields) {
    return new JournalEvent(seq, type, new TreeMap<>(fields));
  }

  @Test
  void writesTopLevelAndFieldKeysInLexicographicOrder() throws IOException {
    Path out = tmp.resolve("out.jsonl");
    JournalCodec.write(out, List.of(event(1, "OrderNew", Map.of("qty", "100", "account", "ACC1"))));

    assertThat(Files.readString(out, StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"fields\":{\"account\":\"ACC1\",\"qty\":\"100\"},\"seq\":1,\"type\":\"OrderNew\"}\n");
  }

  @Test
  void everyLineEndsWithNewlineIncludingTheLast() throws IOException {
    Path out = tmp.resolve("out.jsonl");
    JournalCodec.write(out, List.of(event(1, "A", Map.of()), event(2, "B", Map.of())));

    assertThat(Files.readString(out, StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\n{\"fields\":{},\"seq\":2,\"type\":\"B\"}\n");
  }

  @Test
  void roundTripsWithoutLoss() throws IOException {
    Path path = tmp.resolve("rt.jsonl");
    List<JournalEvent> events =
        List.of(
            event(1, "OrderNew", Map.of("figi", "BBG000B9XRY4", "price", "1250000")),
            event(2, "OrderAccepted", Map.of("orderId", "ORD-0000000001")));

    JournalCodec.write(path, events);

    assertThat(JournalCodec.read(path)).isEqualTo(events);
  }

  @Test
  void escapesQuotesBackslashesAndControlCharacters() throws IOException {
    Path path = tmp.resolve("esc.jsonl");
    // U+0001 has no short escape, so it must be written as a \\uXXXX escape.
    // A raw control byte in the journal is a byte-level divergence waiting to
    // happen between three languages' JSON writers.
    String raw = "a\"b\\c\nd\te" + (char) 1 + "f";
    JournalCodec.write(path, List.of(event(1, "Note", Map.of("text", raw))));

    String escaped = "a\\\"b\\\\c\\nd\\te" + "\\" + "u0001" + "f";
    assertThat(Files.readString(path, StandardCharsets.UTF_8))
        .isEqualTo("{\"fields\":{\"text\":\"" + escaped + "\"},\"seq\":1,\"type\":\"Note\"}\n");
    assertThat(JournalCodec.read(path).get(0).fields()).containsEntry("text", raw);
  }

  @Test
  void nonAsciiIsWrittenAsUtf8NotEscaped() throws IOException {
    Path path = tmp.resolve("utf8.jsonl");
    JournalCodec.write(path, List.of(event(1, "Note", Map.of("text", "café — ☕"))));

    assertThat(Files.readString(path, StandardCharsets.UTF_8))
        .isEqualTo("{\"fields\":{\"text\":\"café — ☕\"},\"seq\":1,\"type\":\"Note\"}\n");
    assertThat(JournalCodec.read(path).get(0).fields()).containsEntry("text", "café — ☕");
  }

  @Test
  void emptyJournalRoundTrips() throws IOException {
    Path path = tmp.resolve("empty.jsonl");
    JournalCodec.write(path, List.of());

    assertThat(Files.readString(path, StandardCharsets.UTF_8)).isEmpty();
    assertThat(JournalCodec.read(path)).isEmpty();
  }

  @Test
  void blankLinesAreIgnoredOnRead() throws IOException {
    Path path = tmp.resolve("blank.jsonl");
    Files.writeString(path, "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\n\n", StandardCharsets.UTF_8);

    assertThat(JournalCodec.read(path)).hasSize(1);
  }

  @Test
  void malformedLineReportsItsLineNumber() throws IOException {
    Path path = tmp.resolve("bad.jsonl");
    Files.writeString(path, "{\"fields\":{},\"seq\":1,\"type\":\"A\"}\nnot json\n");

    assertThatThrownBy(() -> JournalCodec.read(path))
        .isInstanceOf(MalformedJournalException.class)
        .hasMessageContaining("line 2");
  }

  @Test
  void unknownTopLevelKeyIsRejected() throws IOException {
    Path path = tmp.resolve("extra.jsonl");
    Files.writeString(path, "{\"fields\":{},\"seq\":1,\"type\":\"A\",\"extra\":\"x\"}\n");

    assertThatThrownBy(() -> JournalCodec.read(path))
        .isInstanceOf(MalformedJournalException.class)
        .hasMessageContaining("extra");
  }

  @Test
  void missingRequiredKeyIsRejected() throws IOException {
    Path path = tmp.resolve("missing.jsonl");
    Files.writeString(path, "{\"fields\":{},\"seq\":1}\n");

    assertThatThrownBy(() -> JournalCodec.read(path))
        .isInstanceOf(MalformedJournalException.class)
        .hasMessageContaining("type");
  }

  @Test
  void nonStringFieldValueIsRejected() throws IOException {
    Path path = tmp.resolve("num.jsonl");
    Files.writeString(path, "{\"fields\":{\"qty\":100},\"seq\":1,\"type\":\"A\"}\n");

    assertThatThrownBy(() -> JournalCodec.read(path)).isInstanceOf(MalformedJournalException.class);
  }

  @Test
  void trailingContentAfterTheObjectIsRejected() throws IOException {
    Path path = tmp.resolve("trailing.jsonl");
    Files.writeString(path, "{\"fields\":{},\"seq\":1,\"type\":\"A\"} junk\n");

    assertThatThrownBy(() -> JournalCodec.read(path)).isInstanceOf(MalformedJournalException.class);
  }

  @Test
  void negativeSequenceIsRejected() throws IOException {
    Path path = tmp.resolve("neg.jsonl");
    Files.writeString(path, "{\"fields\":{},\"seq\":-1,\"type\":\"A\"}\n");

    assertThatThrownBy(() -> JournalCodec.read(path)).isInstanceOf(MalformedJournalException.class);
  }

  @Test
  void fieldsAreOrderedRegardlessOfInsertionOrder() {
    JournalEvent a = event(1, "X", Map.of("b", "2", "a", "1"));
    JournalEvent b = event(1, "X", Map.of("a", "1", "b", "2"));

    assertThat(a).isEqualTo(b);
    assertThat(a.fields().keySet()).containsExactly("a", "b");
  }

  @Test
  void eventFieldsAreImmutable() {
    JournalEvent e = event(1, "X", Map.of("a", "1"));

    assertThatThrownBy(() -> e.fields().put("b", "2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
