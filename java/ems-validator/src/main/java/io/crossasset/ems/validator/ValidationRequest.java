/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.validator;

import java.util.Objects;

/**
 * Input to the validator pipeline for a single operation. Fields are restricted to what layers 1–4
 * actually consume; asset-class / limits / market / route fields belong to later phases.
 *
 * <p>{@code tag} and {@code figi} are nullable — when null the corresponding layer is skipped.
 *
 * <p>Task 6.2 — Layered evaluation pipeline.
 */
public record ValidationRequest(
    String requestId,
    long sessionId,
    /** Permission tag required for this action. Null skips the permission layer. */
    String tag,
    /** Canonical FIGI for the instrument. Null skips the reference-data layer. */
    String figi) {

  public ValidationRequest {
    Objects.requireNonNull(requestId, "requestId");
  }
}
