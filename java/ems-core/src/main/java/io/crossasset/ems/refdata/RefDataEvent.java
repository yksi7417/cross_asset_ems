/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

/**
 * Audit event hierarchy for reference data changes.
 *
 * <p>Task 4.21 — Reference data service.
 */
public sealed interface RefDataEvent<V>
    permits RefDataEvent.Added, RefDataEvent.Amended, RefDataEvent.Retired {

  String domain();

  String key();

  long occurredAt();

  record Added<V>(RefDataRecord<V> record, long occurredAt) implements RefDataEvent<V> {
    public String domain() {
      return record.domain();
    }

    public String key() {
      return record.key();
    }
  }

  record Amended<V>(RefDataRecord<V> record, long occurredAt) implements RefDataEvent<V> {
    public String domain() {
      return record.domain();
    }

    public String key() {
      return record.key();
    }
  }

  record Retired<V>(String domain, String key, long occurredAt) implements RefDataEvent<V> {}
}
