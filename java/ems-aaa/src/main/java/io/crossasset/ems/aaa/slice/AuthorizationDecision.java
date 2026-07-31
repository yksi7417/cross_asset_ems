/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.slice;

import java.util.Objects;

/**
 * The outcome of an entitlement check.
 *
 * <p>Sealed so a caller must handle both arms. The reject {@code code} is a real entry in {@code
 * schemas/reject-codes/catalog.yaml} — it reaches the output journal, so an invented code would
 * diverge from the catalog silently and the conformance gate would not notice.
 */
public sealed interface AuthorizationDecision
    permits AuthorizationDecision.Allow, AuthorizationDecision.Deny {

  /** The action is permitted. */
  record Allow() implements AuthorizationDecision {}

  /**
   * The action is refused.
   *
   * @param code catalog code, e.g. {@code EMS-PRM-1001}
   * @param category catalog category, e.g. {@code PRM}
   * @param reason human-readable explanation; reaches the journal, so the wording is a contract
   */
  record Deny(String code, String category, String reason) implements AuthorizationDecision {
    public Deny {
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(reason, "reason");
    }
  }

  /** Convenience: the single allow instance. */
  AuthorizationDecision ALLOW = new Allow();
}
