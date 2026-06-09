/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

import io.crossasset.ems.core.clock.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, version-stamped snapshot of all configuration values.
 *
 * <p>Components hold a reference to the current snapshot and read it directly — no map lookups at
 * call-site (the cost is paid once at snapshot publish). The local cache agent (task 3.8)
 * atomically swaps this reference at message boundaries.
 *
 * <h3>Cascade resolution</h3>
 *
 * {@link #get(ConfigKey, ResolutionContext)} walks {@link ConfigScopeLevel#RESOLUTION_ORDER} (most
 * specific first). The first non-null value wins; if none is found the key's {@link
 * ConfigKey#defaultValue()} is returned.
 *
 * <h3>Never-undefined invariant</h3>
 *
 * {@code get()} never returns {@code null} — every key carries a default value. Archived keys lock
 * their default so that replay across any time slice returns the same value.
 *
 * <h3>Codegen note</h3>
 *
 * This map-backed implementation is the stand-in for the code-generated immutable struct described
 * in the architecture. Codegen is deferred until the config schema is stabilised — the API contract
 * (get/version/effectiveAt) is intentionally identical to what the generated version will expose.
 *
 * <p>Task 3.7 — Configuration service.
 */
public final class ConfigSnapshot {

  private final long version;
  private final Timestamp effectiveAt;

  /** {@code keyName → (scope → value)} */
  private final Map<String, Map<ConfigScope, Object>> store;

  ConfigSnapshot(long version, Timestamp effectiveAt, Map<String, Map<ConfigScope, Object>> store) {
    this.version = version;
    this.effectiveAt = effectiveAt;

    // Deep-copy into unmodifiable maps so callers cannot mutate the snapshot.
    Map<String, Map<ConfigScope, Object>> copy = new HashMap<>();
    for (var entry : store.entrySet()) {
      copy.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
    }
    this.store = Collections.unmodifiableMap(copy);
  }

  /**
   * Resolves {@code key} within {@code context} using the 9-level cascade.
   *
   * <p>Walks {@link ConfigScopeLevel#RESOLUTION_ORDER} (ORDER_OVERRIDE → GLOBAL). Returns the value
   * at the most specific scope found, or {@link ConfigKey#defaultValue()} if no scoped value
   * exists.
   *
   * @return the resolved value; never {@code null}
   */
  public <T> T get(ConfigKey<T> key, ResolutionContext context) {
    Map<ConfigScope, Object> scopeMap = store.get(key.name());
    if (scopeMap != null) {
      for (ConfigScopeLevel level : ConfigScopeLevel.RESOLUTION_ORDER) {
        ConfigScope scope = context.scopeAt(level);
        if (scope == null) {
          continue;
        }
        Object value = scopeMap.get(scope);
        if (value != null) {
          return key.type().cast(value);
        }
      }
    }
    return key.defaultValue();
  }

  /** Returns the value at the global scope for {@code key}, or the key's default. */
  public <T> T getGlobal(ConfigKey<T> key) {
    return get(key, ResolutionContext.globalOnly());
  }

  public long version() {
    return version;
  }

  public Timestamp effectiveAt() {
    return effectiveAt;
  }

  /** Creates a builder for a new snapshot at the given version and timestamp. */
  public static Builder builder(long version, Timestamp effectiveAt) {
    return new Builder(version, effectiveAt);
  }

  public static final class Builder {

    private final long version;
    private final Timestamp effectiveAt;
    private final Map<String, Map<ConfigScope, Object>> store = new HashMap<>();

    private Builder(long version, Timestamp effectiveAt) {
      this.version = version;
      this.effectiveAt = effectiveAt;
    }

    /**
     * Sets the value for {@code key} at {@code scope}.
     *
     * @throws IllegalArgumentException if the key is archived (no new values may be set)
     */
    public <T> Builder set(ConfigKey<T> key, ConfigScope scope, T value) {
      if (key.archived()) {
        throw new IllegalArgumentException(
            "Cannot set value for archived key '"
                + key.name()
                + "'. Archive the key to lock its default instead of adding overrides.");
      }
      store.computeIfAbsent(key.name(), k -> new HashMap<>()).put(scope, value);
      return this;
    }

    public ConfigSnapshot build() {
      return new ConfigSnapshot(version, effectiveAt, store);
    }
  }
}
