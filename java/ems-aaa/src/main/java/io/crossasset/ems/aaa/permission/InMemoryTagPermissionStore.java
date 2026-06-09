/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.permission;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tag permission store for testing and skeleton use. Per arch-tag-permissions.md.
 *
 * <p>Task 5.3 — Tag permissions 3-layer AND-gate.
 */
public final class InMemoryTagPermissionStore implements TagPermissionStore {

  private final Map<String, Set<String>> firmGrants = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> deskGrants = new ConcurrentHashMap<>();

  @Override
  public void grantFirmTag(String firmId, String tag) {
    firmGrants.computeIfAbsent(firmId, k -> ConcurrentHashMap.newKeySet()).add(tag);
  }

  @Override
  public void revokeFirmTag(String firmId, String tag) {
    Set<String> grants = firmGrants.get(firmId);
    if (grants != null) grants.remove(tag);
  }

  @Override
  public void grantDeskTag(String firmId, String deskId, String tag) {
    deskGrants
        .computeIfAbsent(deskKey(firmId, deskId), k -> ConcurrentHashMap.newKeySet())
        .add(tag);
  }

  @Override
  public void revokeDeskTag(String firmId, String deskId, String tag) {
    Set<String> grants = deskGrants.get(deskKey(firmId, deskId));
    if (grants != null) grants.remove(tag);
  }

  @Override
  public boolean firmGranted(String firmId, String tag) {
    Set<String> grants = firmGrants.get(firmId);
    return grants != null && grants.contains(tag);
  }

  @Override
  public boolean deskGranted(String firmId, String deskId, String tag) {
    Set<String> grants = deskGrants.get(deskKey(firmId, deskId));
    return grants != null && grants.contains(tag);
  }

  private static String deskKey(String firmId, String deskId) {
    return firmId + '\0' + deskId;
  }
}
