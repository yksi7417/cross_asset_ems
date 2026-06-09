/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.identity;

import java.util.Objects;

/**
 * Desk node in the three-level identity hierarchy. A desk belongs to exactly one firm and contains
 * one or more users. Per arch-firm-desk-user.md.
 *
 * <p>Task 5.2 — Firm/Desk/User hierarchy.
 */
public record Desk(String firmId, String deskId, String name, String adminRef) {

  public Desk {
    Objects.requireNonNull(firmId, "firmId");
    Objects.requireNonNull(deskId, "deskId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(adminRef, "adminRef");
  }
}
