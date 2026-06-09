/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.identity;

import java.util.Objects;

/**
 * User node in the three-level identity hierarchy. A user belongs to exactly one desk at a time.
 * Per arch-firm-desk-user.md.
 *
 * <p>Task 5.2 — Firm/Desk/User hierarchy.
 */
public record User(String firmId, String deskId, String userId, String name, String adminRef) {

  public User {
    Objects.requireNonNull(firmId, "firmId");
    Objects.requireNonNull(deskId, "deskId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(adminRef, "adminRef");
  }
}
