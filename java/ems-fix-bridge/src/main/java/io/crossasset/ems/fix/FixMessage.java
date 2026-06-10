/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A minimal, deterministic FIX 4.4 tag-value codec.
 *
 * <p>FIX messages are {@code SOH}-delimited {@code tag=value} fields. This codec parses a wire
 * string into an ordered tag→value map (first occurrence wins, per FIX) and builds a wire string
 * with a correctly computed {@code BodyLength(9)} and {@code CheckSum(10)}.
 *
 * <p>Deliberately does <b>not</b> use a third-party FIX engine: per arch-fix-api-bridge.md the
 * session concerns are implemented locally, and a QuickFIX/J-style engine's global state, file
 * sessions, and wall-clock are hostile to the replay determinism the EMS guarantees. This codec is
 * pure: no clock, no I/O, no shared mutable state.
 */
public final class FixMessage {

  /** FIX field delimiter (Start-Of-Header, ASCII 0x01). */
  public static final char SOH = '\u0001';

  private static final String DEFAULT_BEGIN_STRING = "FIX.4.4";

  private final Map<Integer, String> fields;

  private FixMessage(Map<Integer, String> fields) {
    this.fields = fields;
  }

  // ── Parsing ────────────────────────────────────────────────────────────────

  /**
   * Parse a {@code SOH}-delimited FIX string. Malformed fields (no {@code =}) are skipped. If a tag
   * repeats, the first occurrence wins (matching FIX's treatment of standard fields).
   */
  public static FixMessage parse(String raw) {
    Map<Integer, String> map = new LinkedHashMap<>();
    if (raw != null) {
      int n = raw.length();
      int start = 0;
      while (start < n) {
        int end = raw.indexOf(SOH, start);
        if (end < 0) {
          end = n;
        }
        String field = raw.substring(start, end);
        int eq = field.indexOf('=');
        if (eq > 0) {
          try {
            int tag = Integer.parseInt(field.substring(0, eq));
            map.putIfAbsent(tag, field.substring(eq + 1));
          } catch (NumberFormatException ignored) {
            // non-numeric tag — skip
          }
        }
        start = end + 1;
      }
    }
    return new FixMessage(map);
  }

  /** The raw string value for {@code tag}, or {@code null} if absent. */
  public String get(int tag) {
    return fields.get(tag);
  }

  /** The value for {@code tag} parsed as an int. Throws if absent or non-numeric. */
  public int getInt(int tag) {
    String v = fields.get(tag);
    if (v == null) {
      throw new IllegalStateException("Missing tag " + tag);
    }
    return Integer.parseInt(v);
  }

  /** The value for {@code tag} if present. */
  public Optional<String> getOptional(int tag) {
    return Optional.ofNullable(fields.get(tag));
  }

  /** Whether {@code tag} is present. */
  public boolean has(int tag) {
    return fields.containsKey(tag);
  }

  // ── Building ───────────────────────────────────────────────────────────────

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Computes the FIX checksum of {@code segment}: the sum of the byte values of every character,
   * modulo 256.
   */
  public static int checksumOf(String segment) {
    int sum = 0;
    for (int i = 0; i < segment.length(); i++) {
      sum += segment.charAt(i);
    }
    return sum % 256;
  }

  /**
   * Ordered builder for an outbound FIX message. Callers add body fields (optionally including
   * {@code BeginString(8)}); {@link #build()} emits the standard envelope: {@code
   * 8=...|9=<bodyLength>|<body>|10=<checksum>|}. Any {@code 9} or {@code 10} added by the caller is
   * ignored — they are always computed.
   */
  public static final class Builder {
    private final Map<Integer, String> body = new LinkedHashMap<>();
    private String beginString = DEFAULT_BEGIN_STRING;

    public Builder field(int tag, String value) {
      if (tag == FixTags.BEGIN_STRING) {
        beginString = value;
      } else if (tag != FixTags.BODY_LENGTH && tag != FixTags.CHECKSUM) {
        body.put(tag, value);
      }
      return this;
    }

    public Builder field(int tag, long value) {
      return field(tag, Long.toString(value));
    }

    public Builder field(int tag, int value) {
      return field(tag, Integer.toString(value));
    }

    /** Assemble the wire string with computed {@code BodyLength} and {@code CheckSum}. */
    public String build() {
      StringBuilder bodyBuf = new StringBuilder();
      for (Map.Entry<Integer, String> e : body.entrySet()) {
        bodyBuf.append(e.getKey()).append('=').append(e.getValue()).append(SOH);
      }
      String bodyStr = bodyBuf.toString();
      String header =
          FixTags.BEGIN_STRING
              + "="
              + beginString
              + SOH
              + FixTags.BODY_LENGTH
              + "="
              + bodyStr.length()
              + SOH;
      String prefix = header + bodyStr;
      int checksum = checksumOf(prefix);
      return prefix + FixTags.CHECKSUM + "=" + String.format("%03d", checksum) + SOH;
    }
  }
}
