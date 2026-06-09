/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.validator;

/**
 * Outcome of a validation pass. Sealed so callers must handle both variants.
 *
 * <p>The reject envelope matches the {@code ValidatorReject} shape in arch-validator.md: code,
 * category, layer, message, optional admin_hint, and optional field key.
 *
 * <p>Task 6.2 — Layered evaluation pipeline.
 */
public sealed interface ValidationResult permits ValidationResult.Pass, ValidationResult.Reject {

  String requestId();

  /** All layers passed; the operation may proceed. */
  record Pass(String requestId) implements ValidationResult {}

  /**
   * A layer failed. {@code code} is a catalog entry from {@code schemas/reject-codes/catalog.yaml}.
   * {@code adminHint} is the contact who can resolve the failure (null if not applicable). {@code
   * field} names the specific field that caused the reject (null if not field-specific).
   */
  record Reject(
      String requestId,
      String code,
      String category,
      ValidationLayer layer,
      String message,
      String adminHint,
      String field)
      implements ValidationResult {}
}
