/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

/**
 * The nine-level configuration resolution cascade.
 *
 * <p>The resolver walks {@link #RESOLUTION_ORDER} (most-specific first) and returns the first value
 * found for a given key, implementing the "most specific scope wins" rule. Each level only carries
 * overrides; absent values fall through.
 *
 * <p>The cascade is fixed — adding a new level is a schema change, not permitted at runtime.
 *
 * <p>Task 3.7 — Configuration service.
 */
public enum ConfigScopeLevel {
  GLOBAL(0),
  ENVIRONMENT(1),
  REGION(2),
  POD(3),
  ASSET_CLASS(4),
  FIRM(5),
  DESK(6),
  USER(7),
  ORDER_OVERRIDE(8);

  private final int specificity;

  ConfigScopeLevel(int specificity) {
    this.specificity = specificity;
  }

  /** Higher specificity = more specific scope = checked earlier in resolution. */
  public int specificity() {
    return specificity;
  }

  /**
   * Resolution walk order: most specific (ORDER_OVERRIDE) first, least specific (GLOBAL) last. The
   * resolver returns the first non-null value it finds in this order.
   */
  public static final ConfigScopeLevel[] RESOLUTION_ORDER = {
    ORDER_OVERRIDE, USER, DESK, FIRM, ASSET_CLASS, POD, REGION, ENVIRONMENT, GLOBAL
  };
}
