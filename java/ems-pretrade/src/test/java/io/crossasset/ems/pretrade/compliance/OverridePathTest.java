/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OverridePathTest {

  @Test
  void construction() {
    OverridePath path = new OverridePath(Set.of("TAG1", "TAG2"), 2, 1000L, true);
    assertEquals(Set.of("TAG1", "TAG2"), path.requiredTags());
    assertEquals(2, path.requiredSignoffs());
    assertEquals(1000L, path.expiryMillis());
    assertEquals(true, path.requiresRationale());
  }

  @Test
  void copyOfTags() {
    Set<String> input = Set.of("TAG1");
    OverridePath path = new OverridePath(input, 1, 0L, false);
    // requiredTags should be an unmodifiable copy
    assertThrows(UnsupportedOperationException.class, () -> path.requiredTags().add("TAG2"));
  }

  @Test
  void rejectsNullTags() {
    assertThrows(NullPointerException.class, () -> new OverridePath(null, 1, 0L, false));
  }

  @Test
  void rejectsZeroSignoffs() {
    assertThrows(
        IllegalArgumentException.class, () -> new OverridePath(Set.of("TAG"), 0, 0L, false));
  }

  @Test
  void rejectsNegativeSignoffs() {
    assertThrows(
        IllegalArgumentException.class, () -> new OverridePath(Set.of("TAG"), -1, 0L, false));
  }
}
