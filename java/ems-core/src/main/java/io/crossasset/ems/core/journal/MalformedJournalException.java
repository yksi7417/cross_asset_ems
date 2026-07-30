/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.journal;

/**
 * A journal line could not be parsed.
 *
 * <p>Always carries the 1-based line number. The journal parser is one of the three fuzz targets in
 * the polyglot gate, so every malformed input must produce this — never an index-out-of-bounds, and
 * never a silently-skipped line.
 */
public class MalformedJournalException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int lineNumber;

  public MalformedJournalException(int lineNumber, String message) {
    super("line " + lineNumber + ": " + message);
    this.lineNumber = lineNumber;
  }

  public int lineNumber() {
    return lineNumber;
  }
}
