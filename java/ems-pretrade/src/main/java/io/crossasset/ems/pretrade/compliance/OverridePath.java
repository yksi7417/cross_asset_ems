/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.Objects;
import java.util.Set;

/**
 * How a BLOCK can be released (arch-compliance § Decision shape): the override tags the reviewer
 * must hold, how many distinct sign-offs (four-eyes = 2), how long a release stays valid, and
 * whether a written rationale is mandatory. Enforced by the override mechanics (task 10.5).
 */
public record OverridePath(
    Set<String> requiredTags, int requiredSignoffs, long expiryMillis, boolean requiresRationale) {
  public OverridePath {
    requiredTags = Set.copyOf(Objects.requireNonNull(requiredTags, "requiredTags"));
    if (requiredSignoffs < 1) {
      throw new IllegalArgumentException("requiredSignoffs must be >= 1");
    }
  }
}
