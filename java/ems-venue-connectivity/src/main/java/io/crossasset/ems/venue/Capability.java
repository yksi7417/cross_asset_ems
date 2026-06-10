/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

/**
 * A capability a venue adapter advertises at startup. The router consults the adapter's capability
 * set before routing a {@code (venue, instrument, instruction)} tuple; a mismatch is rejected with
 * {@code EMS-RTE-1003 capability_unsupported}. Per arch-venue-connectivity.md.
 */
public enum Capability {
  SUPPORTS_MARKET,
  SUPPORTS_LIMIT,
  SUPPORTS_RFQ,
  SUPPORTS_HIDDEN,
  SUPPORTS_CANCEL,
  SUPPORTS_REPLACE
}
