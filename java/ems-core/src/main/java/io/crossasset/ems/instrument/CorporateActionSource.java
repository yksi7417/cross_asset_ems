/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/**
 * Source of a corporate action record.
 *
 * <p>Multiple sources may report the same action. DTCC is authoritative for US equity actions.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public enum CorporateActionSource {
  DTCC,
  BLOOMBERG_CACS,
  EDI,
  MANUAL
}
