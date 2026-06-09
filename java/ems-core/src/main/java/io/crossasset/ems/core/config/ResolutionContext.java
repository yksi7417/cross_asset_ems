/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

/**
 * Full resolution context for a cascade lookup: specifies the qualifier at each applicable scope
 * level.
 *
 * <p>Used by {@link ConfigSnapshot#get(ConfigKey, ResolutionContext)} to walk the 9-level cascade
 * from most-specific to least-specific. Unset levels (null) are skipped — the resolver falls
 * through to the next level.
 *
 * <p>Build via {@link #builder()}.
 *
 * <p>Task 3.7 — Configuration service.
 */
public final class ResolutionContext {

  private final String environment;
  private final String region;
  private final String podId;
  private final String assetClass;
  private final String firmId;
  private final String deskId;
  private final String userId;

  private ResolutionContext(Builder b) {
    this.environment = b.environment;
    this.region = b.region;
    this.podId = b.podId;
    this.assetClass = b.assetClass;
    this.firmId = b.firmId;
    this.deskId = b.deskId;
    this.userId = b.userId;
  }

  /**
   * Returns the {@link ConfigScope} for {@code level} within this context, or {@code null} if the
   * context does not specify enough qualifiers to build a scope at that level.
   *
   * <p>ORDER_OVERRIDE is not supported here — it is resolved externally at the per-message
   * boundary.
   */
  public ConfigScope scopeAt(ConfigScopeLevel level) {
    return switch (level) {
      case GLOBAL -> ConfigScope.global();
      case ENVIRONMENT -> environment != null ? ConfigScope.environment(environment) : null;
      case REGION -> region != null ? ConfigScope.region(region) : null;
      case POD -> podId != null ? ConfigScope.pod(podId) : null;
      case ASSET_CLASS -> assetClass != null ? ConfigScope.assetClass(assetClass) : null;
      case FIRM -> firmId != null ? ConfigScope.firm(firmId) : null;
      case DESK -> (firmId != null && deskId != null) ? ConfigScope.desk(firmId, deskId) : null;
      case USER ->
          (firmId != null && deskId != null && userId != null)
              ? ConfigScope.user(firmId, deskId, userId)
              : null;
      case ORDER_OVERRIDE -> null; // resolved externally, not from static context
    };
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Convenience: a context that only specifies the global level. */
  public static ResolutionContext globalOnly() {
    return builder().build();
  }

  public static final class Builder {
    private String environment;
    private String region;
    private String podId;
    private String assetClass;
    private String firmId;
    private String deskId;
    private String userId;

    public Builder environment(String v) {
      this.environment = v;
      return this;
    }

    public Builder region(String v) {
      this.region = v;
      return this;
    }

    public Builder podId(String v) {
      this.podId = v;
      return this;
    }

    public Builder assetClass(String v) {
      this.assetClass = v;
      return this;
    }

    public Builder firmId(String v) {
      this.firmId = v;
      return this;
    }

    public Builder deskId(String v) {
      this.deskId = v;
      return this;
    }

    public Builder userId(String v) {
      this.userId = v;
      return this;
    }

    public ResolutionContext build() {
      return new ResolutionContext(this);
    }
  }
}
