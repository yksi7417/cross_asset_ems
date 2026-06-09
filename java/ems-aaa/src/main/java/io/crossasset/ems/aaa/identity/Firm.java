/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.identity;

import java.util.Objects;

/**
 * Firm node in the three-level identity hierarchy. Per arch-firm-desk-user.md.
 *
 * <p>Task 5.2 — Firm/Desk/User hierarchy.
 */
public record Firm(String firmId, String name, String adminRef) {

  public Firm {
    Objects.requireNonNull(firmId, "firmId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(adminRef, "adminRef");
  }
}
