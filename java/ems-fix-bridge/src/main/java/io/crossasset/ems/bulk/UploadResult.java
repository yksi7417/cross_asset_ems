/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Result of one bulk import (task 8.6). {@code fileError} is set when the whole file was rejected
 * (unparseable, required columns missing) — then {@code rows} is empty. Otherwise {@code rows}
 * carries one entry per data row of the file, in file order, merging local coercion failures and
 * API item results.
 */
public record UploadResult(
    String uploadId, @Nullable String fileError, List<RowResult> rows, int accepted, int rejected) {

  public UploadResult {
    Objects.requireNonNull(uploadId, "uploadId");
    rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
  }

  /** Per-row outcome; {@code rowIndex} is 0-based over data rows (header excluded). */
  public record RowResult(
      int rowIndex,
      boolean ok,
      @Nullable String refId,
      @Nullable String errorCode,
      @Nullable String errorMessage) {}

  public static UploadResult fileRejected(String uploadId, String reason) {
    return new UploadResult(uploadId, reason, List.of(), 0, 0);
  }
}
