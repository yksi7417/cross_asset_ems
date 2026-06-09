/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

/**
 * Lifecycle status of a reference data record.
 *
 * <p>Task 4.21 — Reference data service.
 */
public enum RefDataStatus {
  ACTIVE,
  SUPERSEDED,
  RETIRED,
  DRAFT
}
