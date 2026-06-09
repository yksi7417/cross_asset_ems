/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

import java.util.List;

/**
 * Append-only audit log for licensed identifier access attempts.
 *
 * <p>Records both granted and denied attempts; denied entries capture licensing probe patterns.
 */
public interface AccessAuditLog {

  /** Appends an access record. */
  void record(AccessRecord entry);

  /** Returns all records for the given caller identity, in insertion order. */
  List<AccessRecord> entries(String identity);
}
