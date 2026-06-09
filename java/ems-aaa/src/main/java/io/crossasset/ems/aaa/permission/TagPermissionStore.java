/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.permission;

/**
 * Stores firm-level and desk-level tag grants. User-level grants live in {@code Identity.tags}. Per
 * arch-tag-permissions.md.
 *
 * <p>Task 5.3 — Tag permissions 3-layer AND-gate.
 */
public interface TagPermissionStore {

  void grantFirmTag(String firmId, String tag);

  void revokeFirmTag(String firmId, String tag);

  void grantDeskTag(String firmId, String deskId, String tag);

  void revokeDeskTag(String firmId, String deskId, String tag);

  boolean firmGranted(String firmId, String tag);

  boolean deskGranted(String firmId, String deskId, String tag);
}
