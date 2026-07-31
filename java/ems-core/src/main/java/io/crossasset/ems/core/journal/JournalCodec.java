/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.journal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes the JSONL event journal.
 *
 * <p>One JSON object per line, UTF-8, {@code \n} terminated — including the last line:
 *
 * <pre>{@code
 * {"fields":{"account":"ACC1","qty":"100"},"seq":1,"type":"OrderNew"}
 * }</pre>
 *
 * <p>Keys are emitted in lexicographic order at both levels. Every value is a string except {@code
 * seq}, which is a non-negative integer. That restriction is not laziness — it removes every place
 * where three languages' JSON libraries could disagree about number formatting, and there are no
 * floating-point values anywhere in the order path to begin with.
 *
 * <p><strong>The encoder is hand-written on purpose.</strong> Jackson is already on the classpath
 * and would work, but the byte layout is a contract with the Rust and C++ ports, and a library
 * upgrade must not be able to change it.
 *
 * <p>The parser is strict: unknown keys, missing keys, duplicate keys, non-string field values and
 * trailing content all fail with the line number. It is one of the three fuzz targets in the
 * polyglot gate, so malformed input must always produce {@link MalformedJournalException} rather
 * than an index-out-of-bounds or a silently dropped line.
 */
public final class JournalCodec {

  private static final String KEY_FIELDS = "fields";
  private static final String KEY_SEQ = "seq";
  private static final String KEY_TYPE = "type";

  private JournalCodec() {}

  // ── writing ────────────────────────────────────────────────────────────────

  /** Writes every event, one per line, replacing any existing file. */
  public static void write(Path path, List<JournalEvent> events) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      for (JournalEvent event : events) {
        writer.write(encode(event));
        writer.write('\n');
      }
    }
  }

  /** Encodes one event, without the trailing newline. */
  public static String encode(JournalEvent event) {
    StringBuilder out = new StringBuilder(64);
    out.append("{\"").append(KEY_FIELDS).append("\":{");
    boolean first = true;
    for (Map.Entry<String, String> entry : event.fields().entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      appendString(out, entry.getKey());
      out.append(':');
      appendString(out, entry.getValue());
    }
    out.append("},\"").append(KEY_SEQ).append("\":").append(event.seq());
    out.append(",\"").append(KEY_TYPE).append("\":");
    appendString(out, event.type());
    return out.append('}').toString();
  }

  private static void appendString(StringBuilder out, String value) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            // No short escape exists; a raw control byte in the journal is a
            // byte-level divergence waiting to happen between three writers.
            out.append(String.format("\\u%04x", (int) c));
          } else {
            // Non-ASCII is written as UTF-8, not escaped. All three languages
            // agree on UTF-8; they would not agree on when to escape.
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  // ── reading ────────────────────────────────────────────────────────────────

  /** Reads every non-blank line. Throws {@link MalformedJournalException} on the first bad line. */
  public static List<JournalEvent> read(Path path) throws IOException {
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    List<JournalEvent> events = new ArrayList<>(lines.size());
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isBlank()) {
        continue;
      }
      events.add(decode(line, i + 1));
    }
    return events;
  }

  /** Decodes one line. {@code lineNumber} is 1-based and appears in any error message. */
  public static JournalEvent decode(String line, int lineNumber) {
    return new Parser(line, lineNumber).parseEvent();
  }

  /**
   * Recursive-descent parser for the restricted grammar above. Accepting only what the format
   * actually uses keeps the fuzz surface small and makes every rejection specific.
   */
  private static final class Parser {

    private final String src;
    private final int lineNumber;
    private int pos;

    Parser(String src, int lineNumber) {
      this.src = src;
      this.lineNumber = lineNumber;
    }

    JournalEvent parseEvent() {
      skipWhitespace();
      expect('{');

      TreeMap<String, String> fields = null;
      Long seq = null;
      String type = null;

      skipWhitespace();
      if (peek() != '}') {
        while (true) {
          skipWhitespace();
          String key = parseString();
          skipWhitespace();
          expect(':');
          skipWhitespace();
          switch (key) {
            case KEY_FIELDS -> {
              requireAbsent(fields, KEY_FIELDS);
              fields = parseFields();
            }
            case KEY_SEQ -> {
              requireAbsent(seq, KEY_SEQ);
              seq = parseNonNegativeLong();
            }
            case KEY_TYPE -> {
              requireAbsent(type, KEY_TYPE);
              type = parseString();
            }
            default -> throw fail("unknown key \"" + key + "\"");
          }
          skipWhitespace();
          if (peek() == ',') {
            pos++;
            continue;
          }
          break;
        }
      }

      expect('}');
      skipWhitespace();
      if (pos != src.length()) {
        throw fail("trailing content after the object");
      }

      if (fields == null) {
        throw fail("missing key \"" + KEY_FIELDS + "\"");
      }
      if (seq == null) {
        throw fail("missing key \"" + KEY_SEQ + "\"");
      }
      if (type == null) {
        throw fail("missing key \"" + KEY_TYPE + "\"");
      }
      return new JournalEvent(seq, type, fields);
    }

    private TreeMap<String, String> parseFields() {
      TreeMap<String, String> fields = new TreeMap<>();
      expect('{');
      skipWhitespace();
      if (peek() == '}') {
        pos++;
        return fields;
      }
      while (true) {
        skipWhitespace();
        String key = parseString();
        skipWhitespace();
        expect(':');
        skipWhitespace();
        if (peek() != '"') {
          throw fail("field \"" + key + "\" must be a string");
        }
        String value = parseString();
        if (fields.put(key, value) != null) {
          throw fail("duplicate field \"" + key + "\"");
        }
        skipWhitespace();
        if (peek() == ',') {
          pos++;
          continue;
        }
        break;
      }
      expect('}');
      return fields;
    }

    private String parseString() {
      expect('"');
      StringBuilder out = new StringBuilder();
      while (true) {
        if (pos >= src.length()) {
          throw fail("unterminated string");
        }
        char c = src.charAt(pos++);
        if (c == '"') {
          return out.toString();
        }
        if (c != '\\') {
          if (c < 0x20) {
            throw fail("raw control character in string");
          }
          out.append(c);
          continue;
        }
        if (pos >= src.length()) {
          throw fail("unterminated escape");
        }
        char esc = src.charAt(pos++);
        switch (esc) {
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          case '/' -> out.append('/');
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'u' -> out.append(parseUnicodeEscape());
          default -> throw fail("invalid escape \"\\" + esc + "\"");
        }
      }
    }

    private char parseUnicodeEscape() {
      if (pos + 4 > src.length()) {
        throw fail("truncated unicode escape");
      }
      int value = 0;
      for (int i = 0; i < 4; i++) {
        int digit = Character.digit(src.charAt(pos + i), 16);
        if (digit < 0) {
          throw fail("invalid unicode escape");
        }
        value = value * 16 + digit;
      }
      pos += 4;
      return (char) value;
    }

    private long parseNonNegativeLong() {
      int start = pos;
      while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') {
        pos++;
      }
      if (pos == start) {
        throw fail("expected a non-negative integer for \"" + KEY_SEQ + "\"");
      }
      try {
        return Long.parseLong(src, start, pos, 10);
      } catch (NumberFormatException e) {
        throw fail("sequence number out of range");
      }
    }

    /** {@code current} is the slot being filled — null means "not seen yet". */
    private void requireAbsent(@Nullable Object current, String key) {
      if (current != null) {
        throw fail("duplicate key \"" + key + "\"");
      }
    }

    private void skipWhitespace() {
      while (pos < src.length()) {
        char c = src.charAt(pos);
        if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
          pos++;
        } else {
          break;
        }
      }
    }

    private char peek() {
      if (pos >= src.length()) {
        throw fail("unexpected end of line");
      }
      return src.charAt(pos);
    }

    private void expect(char expected) {
      if (pos >= src.length() || src.charAt(pos) != expected) {
        throw fail("expected '" + expected + "' at offset " + pos);
      }
      pos++;
    }

    private MalformedJournalException fail(String message) {
      return new MalformedJournalException(lineNumber, message);
    }
  }
}
