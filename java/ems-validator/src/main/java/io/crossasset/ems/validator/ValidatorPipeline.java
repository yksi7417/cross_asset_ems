/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.validator;

/**
 * Entry point for the layered validation pipeline. Every EMS action — stage, amend, route, cancel,
 * subscribe, rule-bind — passes through here before reaching business logic.
 *
 * <p>Per arch-validator.md: validation runs in fixed order (SESSION → IDENTITY → REFERENCE →
 * PERMISSION → ASSET_CLASS → LIMITS → MARKET → ROUTE). First failure short-circuits.
 *
 * <p>Task 6.2 — Layered evaluation pipeline.
 */
@FunctionalInterface
public interface ValidatorPipeline {

  /**
   * Runs the layered evaluation for {@code request}.
   *
   * @return {@link ValidationResult.Pass} if all active layers accept; {@link
   *     ValidationResult.Reject} with the first failing layer's code and message otherwise.
   */
  ValidationResult validate(ValidationRequest request);
}
