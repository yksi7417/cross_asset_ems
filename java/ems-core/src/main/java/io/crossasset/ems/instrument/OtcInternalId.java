/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Objects;

/**
 * Internal-allocated identifier for OTC instruments that have no public FIGI.
 *
 * <p>Format: {@code ems_iid:{firmId}:{instrumentClass}:{counter}} (with class) or {@code
 * ems_iid:{firmId}:{counter}} (without class). Total length ≤ 20 characters to fit in the
 * fixed-width {@code internal_iid} SBE field.
 *
 * <p>The prefix {@code ems_iid:} ensures the namespace never collides with OpenFIGI identifiers.
 * The identifier carries the same lifecycle and supersession semantics as FIGI.
 *
 * <p>Task 4.25 — Internal-allocated identifier namespace for OTC.
 */
public record OtcInternalId(String firmId, String instrumentClass, long counter) {

  static final String PREFIX = "ems_iid:";
  static final int MAX_LENGTH = 20;

  public OtcInternalId {
    Objects.requireNonNull(firmId, "firmId must not be null");
    if (firmId.isBlank()) throw new IllegalArgumentException("firmId must not be blank");
    if (!isValidComponent(firmId))
      throw new IllegalArgumentException("firmId contains invalid chars: " + firmId);
    if (instrumentClass != null) {
      if (instrumentClass.isBlank())
        throw new IllegalArgumentException("instrumentClass must not be blank if provided");
      if (!isValidComponent(instrumentClass))
        throw new IllegalArgumentException(
            "instrumentClass contains invalid chars: " + instrumentClass);
    }
    if (counter < 1) throw new IllegalArgumentException("counter must be >= 1");
    String canonical = formatCanonical(firmId, instrumentClass, counter);
    if (canonical.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "OtcInternalId exceeds max length "
              + MAX_LENGTH
              + ": '"
              + canonical
              + "' ("
              + canonical.length()
              + " chars)");
    }
  }

  /** Returns {@code true} if {@code value} starts with the {@code ems_iid:} prefix. */
  public static boolean isOtcInternalId(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  /**
   * Parses an OTC internal ID from its canonical string representation.
   *
   * @throws IllegalArgumentException if the string is not a valid {@code ems_iid:} identifier
   */
  public static OtcInternalId parse(String value) {
    if (value == null || !value.startsWith(PREFIX)) {
      throw new IllegalArgumentException("Not an OTC internal ID: '" + value + "'");
    }
    String body = value.substring(PREFIX.length());
    String[] parts = body.split(":", -1);
    if (parts.length == 2) {
      long counter = parseCounter(parts[1], value);
      return new OtcInternalId(parts[0], null, counter);
    } else if (parts.length == 3) {
      long counter = parseCounter(parts[2], value);
      return new OtcInternalId(parts[0], parts[1], counter);
    } else {
      throw new IllegalArgumentException(
          "Invalid OTC internal ID format (expected 2 or 3 parts after prefix): '" + value + "'");
    }
  }

  /** Returns the canonical string form of this identifier. */
  @Override
  public String toString() {
    return formatCanonical(firmId, instrumentClass, counter);
  }

  private static String formatCanonical(String firmId, String instrumentClass, long counter) {
    if (instrumentClass != null) {
      return PREFIX + firmId + ":" + instrumentClass + ":" + counter;
    } else {
      return PREFIX + firmId + ":" + counter;
    }
  }

  private static boolean isValidComponent(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') return false;
    }
    return !s.isEmpty();
  }

  private static long parseCounter(String s, String fullValue) {
    try {
      long v = Long.parseLong(s);
      if (v < 1) throw new IllegalArgumentException("counter must be >= 1 in '" + fullValue + "'");
      return v;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid counter in OTC internal ID '" + fullValue + "': " + s);
    }
  }
}
