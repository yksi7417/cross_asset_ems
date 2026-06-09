/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

/** Per-firm license grants for secondary identifier types (ISIN, CUSIP, SEDOL). */
public interface LicenseRegistry {

  /**
   * Returns {@code true} if {@code firmId} holds a valid license for the given identifier type.
   *
   * <p>FIGI ({@code ID_BB_GLOBAL}) and ticker ({@code ID_TICKER}) are always allowed; callers need
   * not check this method for those types.
   */
  boolean isLicensed(String firmId, SymbologyService.IdType idType);
}
