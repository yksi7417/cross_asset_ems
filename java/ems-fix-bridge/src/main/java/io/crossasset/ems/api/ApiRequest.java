/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import java.util.List;
import java.util.Objects;

/**
 * The canonical batch envelope (arch-api-first.md): {@code requestId} is the client-assigned
 * idempotency key (a duplicate returns the original response without re-execution); {@code
 * sessionSeq} is the per-session monotonic sequence the channel dedups on; identity rides the
 * AAA-established {@code sessionId}. Submitting one order is a batch of size one.
 */
public record ApiRequest(
    String requestId,
    long sessionId,
    long sessionSeq,
    ApiOperation operation,
    List<ApiItem> items,
    BatchOptions options) {
  public ApiRequest {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(operation, "operation");
    items = List.copyOf(Objects.requireNonNull(items, "items"));
    options = options == null ? BatchOptions.DEFAULT : options;
  }

  /** Convenience constructor with default batch options (partial ok, continue on error). */
  public ApiRequest(
      String requestId,
      long sessionId,
      long sessionSeq,
      ApiOperation operation,
      List<ApiItem> items) {
    this(requestId, sessionId, sessionSeq, operation, items, BatchOptions.DEFAULT);
  }
}
