/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.List;
import java.util.UUID;

/**
 * Audit event hierarchy for the corporate action lifecycle.
 *
 * <p>Sealed interface with one record subtype per lifecycle event. The {@code Applied} subtype is
 * the integration point: it drives security-master supersession via {@link CorporateActionBridge}.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public sealed interface CorporateActionEvent
    permits CorporateActionEvent.Announced,
        CorporateActionEvent.Updated,
        CorporateActionEvent.Locked,
        CorporateActionEvent.Applied,
        CorporateActionEvent.Cancelled,
        CorporateActionEvent.Discrepancy {

  UUID caId();

  long occurredAt();

  record Announced(
      UUID caId, CorporateActionSource source, CorporateAction details, long occurredAt)
      implements CorporateActionEvent {}

  record Updated(
      UUID caId, List<String> fieldsChanged, CorporateActionSource source, long occurredAt)
      implements CorporateActionEvent {
    public Updated {
      fieldsChanged = List.copyOf(fieldsChanged);
    }
  }

  record Locked(UUID caId, long occurredAt) implements CorporateActionEvent {}

  record Applied(UUID caId, long appliedAt, int affectedPositionsCount, long occurredAt)
      implements CorporateActionEvent {}

  record Cancelled(UUID caId, String reason, long occurredAt) implements CorporateActionEvent {}

  record Discrepancy(
      UUID caId,
      List<CorporateActionSource> sourcesDisagreeing,
      List<String> fields,
      long occurredAt)
      implements CorporateActionEvent {
    public Discrepancy {
      sourcesDisagreeing = List.copyOf(sourcesDisagreeing);
      fields = List.copyOf(fields);
    }
  }
}
