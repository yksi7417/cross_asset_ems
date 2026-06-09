/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/**
 * Canonical Java representation of the InstrumentCore SBE block defined in arch-security-master.
 *
 * <p>Every instrument SBE message starts with this 22-field block at offset 0. Any consumer that
 * needs only identity, asset class, currency, or lifecycle status reads this record without
 * inspecting the SBE template ID.
 *
 * <p>Nullable fields ({@code compositeFigi}, {@code shareClassFigi}, {@code issuerLei}, {@code
 * countryOfListing}, {@code supersededBy}) are represented as {@code null} when absent. Their SBE
 * encoding uses a null-sentinel value defined per field in the instrument XML schemas.
 *
 * <p>Note: the deployed SBE instrument XML schemas deviate from this canonical layout in field
 * names, types, and presence of some fields (see task 4.11 hold note — InstrumentCore byte
 * mismatch). This record is the reconciliation target.
 *
 * <p>Task 4.4 — InstrumentCore SBE block per arch-security-master.
 */
public record InstrumentCore(
    String figi,
    String internalIid,
    String compositeFigi,
    String shareClassFigi,
    AssetClass assetClass,
    InstrumentType instrumentType,
    String displayName,
    String legalName,
    String issuerLei,
    CurrencyCode currency,
    String countryOfIssue,
    String countryOfListing,
    Fungibility fungibility,
    SettlementConvention settlementConvention,
    int tickSizeRegimeRef,
    LifecycleStatus lifecycleStatus,
    long effectiveFrom,
    long effectiveTo,
    long versionSeq,
    String supersededBy,
    long createdAt,
    long lastAmendedAt) {

  public boolean isActive() {
    return lifecycleStatus == LifecycleStatus.ACTIVE;
  }

  public boolean isOpenEnded() {
    return effectiveTo == Long.MAX_VALUE;
  }
}
