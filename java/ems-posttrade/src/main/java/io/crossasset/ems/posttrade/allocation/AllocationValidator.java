/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

/**
 * Pre-allocation validation hook (per arch-allocation-service.md § Pre-allocation validation):
 * account enabled for firm/desk, PB consistent with the template, KYC current, per-account caps
 * respected. The allocation service consults this before applying; a failure becomes an {@link
 * AllocationEvent.AllocationAnomaly} and the fill stays unallocated.
 *
 * <p>The MVP wires the account-enablement check; the richer checks (KYC freshness, caps) plug in
 * here without changing the service. The default permits every account.
 */
@FunctionalInterface
public interface AllocationValidator {

  /** A reason string when the account may not be allocated to, or {@code null} when it is fine. */
  String rejectionReason(AccountShare share, AllocationTemplate template);

  /** Permits every account. */
  static AllocationValidator permissive() {
    return (share, template) -> null;
  }
}
