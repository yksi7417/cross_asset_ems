/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory license registry. Thread-safe; grants are additive (no revocation in this tier). */
public class InMemoryLicenseRegistry implements LicenseRegistry {

  private final Map<String, Set<SymbologyService.IdType>> grants = new ConcurrentHashMap<>();

  /** Grants {@code firmId} a license for {@code idType}. Idempotent. */
  public void grant(String firmId, SymbologyService.IdType idType) {
    grants.computeIfAbsent(firmId, k -> ConcurrentHashMap.newKeySet()).add(idType);
  }

  @Override
  public boolean isLicensed(String firmId, SymbologyService.IdType idType) {
    Set<SymbologyService.IdType> firmGrants = grants.get(firmId);
    return firmGrants != null && firmGrants.contains(idType);
  }
}
