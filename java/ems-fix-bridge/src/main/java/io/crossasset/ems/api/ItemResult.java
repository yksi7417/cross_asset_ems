/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import org.jspecify.annotations.Nullable;

/**
 * Result of one batch item, position-matched to {@code request.items}. {@code refId} carries the
 * EMS-side ID (order/route/subscription); {@code errorCode}/{@code errorMessage} are set when
 * status is REJECTED (codes per schemas/reject-codes/catalog.yaml).
 */
public record ItemResult(
    Status status,
    @Nullable String refId,
    @Nullable String errorCode,
    @Nullable String errorMessage) {

  public enum Status {
    ACCEPTED,
    REJECTED,
    DEFERRED
  }

  public static ItemResult accepted(String refId) {
    return new ItemResult(Status.ACCEPTED, refId, null, null);
  }

  public static ItemResult rejected(String code, String message) {
    return new ItemResult(Status.REJECTED, null, code, message);
  }
}
