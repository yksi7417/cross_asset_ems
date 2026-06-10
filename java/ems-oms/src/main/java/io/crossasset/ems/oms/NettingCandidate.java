/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One staged order offered for netting, with the FX attributes that form its netting key. {@code
 * valueDate} is the already-computed settlement date (ISO yyyy-MM-dd) — spot-days arithmetic and
 * holiday calendars live upstream in the reference-data service (arch-reference-data-service).
 * {@code accountGroup} is the prime-broker-or-internal settlement bucket. A set {@code pac}
 * isolates the order into a pre-authorized-counterparty sub-bucket. {@code doNotNet} is the
 * per-order user opt-out.
 */
public record NettingCandidate(
    String orderId,
    String ccyPair,
    String valueDate,
    String accountGroup,
    @Nullable String pac,
    boolean doNotNet) {
  public NettingCandidate {
    Objects.requireNonNull(orderId, "orderId");
    Objects.requireNonNull(ccyPair, "ccyPair");
    Objects.requireNonNull(valueDate, "valueDate");
    Objects.requireNonNull(accountGroup, "accountGroup");
  }
}
