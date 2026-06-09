/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

/**
 * Generic metadata wrapper for a reference data value.
 *
 * <p>All reference data domains share this envelope. {@code expiryDate} is {@code Long.MAX_VALUE}
 * for open-ended records. {@code version} starts at 1 and increments on each amendment.
 *
 * <p>Task 4.21 — Reference data service.
 */
public record RefDataRecord<V>(
    String domain,
    String key,
    V value,
    int version,
    long effectiveDate,
    long expiryDate,
    RefDataStatus status,
    String changedBy,
    long changedAt,
    String changeReason) {

  public RefDataRecord {
    if (domain == null || domain.isBlank()) throw new IllegalArgumentException("domain required");
    if (key == null || key.isBlank()) throw new IllegalArgumentException("key required");
    if (value == null) throw new IllegalArgumentException("value required");
    if (status == null) throw new IllegalArgumentException("status required");
    if (version < 1) throw new IllegalArgumentException("version must be >= 1");
  }

  public boolean isActive() {
    return status == RefDataStatus.ACTIVE;
  }

  public boolean isOpenEnded() {
    return expiryDate == Long.MAX_VALUE;
  }

  /** Returns an amended copy with incremented version and updated change metadata. */
  public RefDataRecord<V> amend(
      V newValue, long newEffectiveDate, String amendedBy, long amendedAt, String reason) {
    return new RefDataRecord<>(
        domain,
        key,
        newValue,
        version + 1,
        newEffectiveDate,
        expiryDate,
        RefDataStatus.ACTIVE,
        amendedBy,
        amendedAt,
        reason);
  }

  /** Returns a retired copy with the given expiry date. */
  public RefDataRecord<V> retire(long expiredAt, String retiredBy, long retiredAt, String reason) {
    return new RefDataRecord<>(
        domain,
        key,
        value,
        version + 1,
        effectiveDate,
        expiredAt,
        RefDataStatus.RETIRED,
        retiredBy,
        retiredAt,
        reason);
  }
}
