/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

import java.util.Objects;

/**
 * A typed, named configuration key.
 *
 * <p>Keys carry a default value that is returned when no scoped override is found. Keys are never
 * deleted — only archived. Archiving locks the {@link #defaultValue()} so that all future and
 * historical replay calls that fall through to the default receive a stable, replay-deterministic
 * value.
 *
 * <p>New values cannot be set on an archived key (rejected by {@link ConfigSnapshot.Builder#set}).
 * Attempting to archive a key without providing a locked default is also rejected.
 *
 * <p>Task 3.7 — Configuration service.
 */
public final class ConfigKey<T> {

  private final String name;
  private final Class<T> type;
  private final T defaultValue;
  private final boolean archived;

  private ConfigKey(String name, Class<T> type, T defaultValue, boolean archived) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("ConfigKey name must not be blank");
    }
    Objects.requireNonNull(type, "ConfigKey type must not be null");
    Objects.requireNonNull(
        defaultValue,
        "ConfigKey requires a non-null defaultValue (use Optional if absence is valid)");
    this.name = name;
    this.type = type;
    this.defaultValue = defaultValue;
    this.archived = archived;
  }

  /** Creates an active (non-archived) key with the given name, type, and default value. */
  public static <T> ConfigKey<T> of(String name, Class<T> type, T defaultValue) {
    return new ConfigKey<>(name, type, defaultValue, false);
  }

  /**
   * Returns a new archived version of this key with the given locked default.
   *
   * <p>The locked default replaces the prior default and is returned by every {@link
   * ConfigSnapshot#get(ConfigKey, ResolutionContext)} call that falls through to the default level.
   * This ensures replay determinism: the same snapshot always returns the same value for an
   * archived key, regardless of future changes to the active config tree.
   *
   * @param lockedDefault the value all callers will receive when no scoped override exists
   * @throws IllegalArgumentException if lockedDefault is null — a null default violates the
   *     never-undefined replay contract
   */
  public ConfigKey<T> archive(T lockedDefault) {
    Objects.requireNonNull(
        lockedDefault,
        "Archive requires a non-null locked default (replay determinism — never undefined)");
    return new ConfigKey<>(name, type, lockedDefault, true);
  }

  public String name() {
    return name;
  }

  public Class<T> type() {
    return type;
  }

  /** The default value returned when no scoped override is found. Locked after archival. */
  public T defaultValue() {
    return defaultValue;
  }

  /** {@code true} if this key has been archived; new value proposals are rejected. */
  public boolean archived() {
    return archived;
  }

  @Override
  public String toString() {
    return "ConfigKey[" + name + ":" + type.getSimpleName() + (archived ? ",archived" : "") + "]";
  }
}
