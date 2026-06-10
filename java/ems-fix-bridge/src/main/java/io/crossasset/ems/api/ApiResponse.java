/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import java.util.List;
import java.util.Objects;

/** Response to an {@link ApiRequest}: results in the same order/cardinality as the items. */
public record ApiResponse(String requestId, List<ItemResult> results, Summary summary) {

  public ApiResponse {
    Objects.requireNonNull(requestId, "requestId");
    results = List.copyOf(Objects.requireNonNull(results, "results"));
    Objects.requireNonNull(summary, "summary");
  }

  /** Aggregate counts over {@code results}. */
  public record Summary(int ok, int rejected, int deferred) {}

  public static ApiResponse of(String requestId, List<ItemResult> results) {
    int ok = 0;
    int rejected = 0;
    int deferred = 0;
    for (ItemResult r : results) {
      switch (r.status()) {
        case ACCEPTED -> ok++;
        case REJECTED -> rejected++;
        case DEFERRED -> deferred++;
      }
    }
    return new ApiResponse(requestId, results, new Summary(ok, rejected, deferred));
  }
}
