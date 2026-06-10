/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

/**
 * Per-report lifecycle states (per arch-regulatory-reporting-service.md § Per-trade reporting
 * lifecycle): {@code Triggered → Built → Submitted → Acked | Nacked → Retrying → Failed}, with
 * {@code Deferred} when a required field is missing and {@code Voided/Amended} on bust/correct.
 */
public enum ReportState {
  TRIGGERED,
  BUILT,
  DEFERRED,
  SUBMITTED,
  ACKED,
  NACKED,
  RETRYING,
  FAILED,
  VOIDED,
  AMENDED
}
