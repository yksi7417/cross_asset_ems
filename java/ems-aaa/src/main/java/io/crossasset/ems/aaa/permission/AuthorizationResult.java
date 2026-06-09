/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.permission;

/**
 * Result of a tag-permission check. Per arch-tag-permissions.md.
 *
 * <p>Task 5.3 — Tag permissions 3-layer AND-gate.
 */
public sealed interface AuthorizationResult
    permits AuthorizationResult.Allow, AuthorizationResult.Deny {

  /** The tag check passed; the operation is permitted. */
  record Allow() implements AuthorizationResult {}

  /**
   * The tag check failed at the specified level. {@code rejectCode} is one of
   * EMS-PRM-1001/1002/1003 per the catalog. {@code adminHint} names the person who can resolve the
   * missing grant.
   */
  record Deny(String rejectCode, String message, DenialLevel missingLevel, String adminHint)
      implements AuthorizationResult {}
}
