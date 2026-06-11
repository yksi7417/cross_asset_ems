/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One operation presented to the compliance gate (task 10.1): the action a layer wants to take,
 * with the identity and instrument facts every check needs. Side uses FIX tag 54; qty/price are OMS
 * fixed-point longs. The gate runs after the validator (the operation is well-formed and permitted)
 * and before persistence, per arch-compliance.md.
 */
public record ComplianceOperation(
    Kind kind,
    long sessionId,
    String firm,
    String desk,
    String user,
    @Nullable String orderId,
    String figi,
    int side,
    long qty,
    @Nullable Long price,
    String account) {

  /** The gated decision points (arch-compliance § What Compliance taps into). */
  public enum Kind {
    STAGE,
    AMEND,
    MARK_READY,
    ROUTE
  }

  public ComplianceOperation {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(firm, "firm");
    Objects.requireNonNull(desk, "desk");
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(account, "account");
  }
}
