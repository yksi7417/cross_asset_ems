/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

import java.util.List;

/**
 * A fully-qualified point in the 9-level configuration hierarchy.
 *
 * <p>Factory methods create scopes for the common levels; each level carries the qualifiers needed
 * to identify it uniquely (e.g. DESK requires firmId + deskId). Equality is structural: two
 * ConfigScopes are equal iff their level and qualifiers are equal.
 *
 * <p>ConfigScopes are the keys in {@link ConfigSnapshot}'s internal store — the snapshot maps
 * {@code (keyName, scope)} pairs to values.
 *
 * <p>Task 3.7 — Configuration service.
 */
public record ConfigScope(ConfigScopeLevel level, List<String> qualifiers) {

  public ConfigScope {
    qualifiers = List.copyOf(qualifiers);
  }

  public static ConfigScope global() {
    return new ConfigScope(ConfigScopeLevel.GLOBAL, List.of());
  }

  public static ConfigScope environment(String env) {
    return new ConfigScope(ConfigScopeLevel.ENVIRONMENT, List.of(env));
  }

  public static ConfigScope region(String region) {
    return new ConfigScope(ConfigScopeLevel.REGION, List.of(region));
  }

  public static ConfigScope pod(String podId) {
    return new ConfigScope(ConfigScopeLevel.POD, List.of(podId));
  }

  public static ConfigScope assetClass(String assetClass) {
    return new ConfigScope(ConfigScopeLevel.ASSET_CLASS, List.of(assetClass));
  }

  public static ConfigScope firm(String firmId) {
    return new ConfigScope(ConfigScopeLevel.FIRM, List.of(firmId));
  }

  public static ConfigScope desk(String firmId, String deskId) {
    return new ConfigScope(ConfigScopeLevel.DESK, List.of(firmId, deskId));
  }

  public static ConfigScope user(String firmId, String deskId, String userId) {
    return new ConfigScope(ConfigScopeLevel.USER, List.of(firmId, deskId, userId));
  }
}
