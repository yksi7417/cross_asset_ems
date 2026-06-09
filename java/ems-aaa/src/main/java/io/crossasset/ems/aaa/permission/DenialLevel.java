/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.permission;

/**
 * Which level of the three-layer AND-gate is missing. Per arch-tag-permissions.md.
 *
 * <p>Reported outermost-first: FIRM before DESK before USER. A FIRM denial means the
 * desk/user-level grants are irrelevant — the firm hasn't licensed the capability.
 *
 * <p>Task 5.3 — Tag permissions 3-layer AND-gate.
 */
public enum DenialLevel {
  FIRM,
  DESK,
  USER
}
