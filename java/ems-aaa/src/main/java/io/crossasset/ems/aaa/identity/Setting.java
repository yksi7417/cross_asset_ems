/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.identity;

import java.util.Objects;

/**
 * A hierarchical setting resolved via user → desk → firm cascade. Per arch-firm-desk-user.md.
 *
 * <p>Caps narrow downward only: a desk-level cap cannot be widened by a user-level setting. That
 * narrowing discipline is enforced by the validator layer (arch-validator.md), not here.
 *
 * <p>Task 5.2 — Firm/Desk/User hierarchy.
 */
public record Setting<T>(String key, T firmValue, T deskValue, T userValue) {

  public Setting {
    Objects.requireNonNull(key, "key");
  }

  /**
   * Returns the most-specific non-null value: userValue ?? deskValue ?? firmValue ?? null.
   *
   * @return the resolved value, or {@code null} if no level supplies one
   */
  public T resolve() {
    if (userValue != null) return userValue;
    if (deskValue != null) return deskValue;
    return firmValue;
  }
}
