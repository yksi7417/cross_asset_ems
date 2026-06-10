/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import java.util.Objects;

/**
 * One account's share of an allocation template, expressed in basis points (1/10000) to keep the
 * split exact and floating-point-free. The {@code primeBroker} is the PB the account settles
 * through (PB-agnostic templates may leave it blank).
 */
public record AccountShare(String account, String primeBroker, long weightBps) {
  public AccountShare {
    Objects.requireNonNull(account, "account");
    if (weightBps < 0) {
      throw new IllegalArgumentException("weightBps must be >= 0");
    }
  }
}
