/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

/**
 * Sink for AAA accounting events. Per entry-point-aaa.md.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
@FunctionalInterface
public interface AaaEventLog {
  void record(AaaEvent event);
}
