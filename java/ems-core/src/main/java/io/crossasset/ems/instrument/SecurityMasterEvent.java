/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/**
 * Sealed event hierarchy for the Security Master Service.
 *
 * <p>All security master state changes are driven by one of these three event types. Events are
 * append-only; they are applied to a {@link SecurityMasterSnapshot} by the projection layer.
 *
 * <ul>
 *   <li>{@link InstrumentCreated} — initial registration of a new instrument.
 *   <li>{@link InstrumentSuperseded} — a new version replaces the prior record, typically driven by
 *       a corporate-action event (split, rename, coupon reset, pool-factor update).
 *   <li>{@link InstrumentRetired} — lifecycle terminal reached (MATURED, EXPIRED, DEFAULTED);
 *       closes the prior version's {@code effectiveTo} range.
 * </ul>
 *
 * <p>Task 4.19 — Security master CRUD + supersession events.
 */
public sealed interface SecurityMasterEvent
    permits SecurityMasterEvent.InstrumentCreated,
        SecurityMasterEvent.InstrumentSuperseded,
        SecurityMasterEvent.InstrumentRetired {

  long occurredAt();

  /** Initial registration of an instrument. {@code versionSeq=1}. */
  record InstrumentCreated(InstrumentVersioned instrument, long occurredAt)
      implements SecurityMasterEvent {}

  /**
   * A new version of an instrument supersedes the prior one.
   *
   * <p>{@code priorVersionSeq} identifies the version whose {@code effectiveTo} is now closed at
   * {@code newVersion.effectiveFrom()}. The new version's {@code effectiveTo} is typically {@link
   * Long#MAX_VALUE} (open-ended) unless it is itself immediately terminal.
   */
  record InstrumentSuperseded(
      String figi, long priorVersionSeq, InstrumentVersioned newVersion, long occurredAt)
      implements SecurityMasterEvent {}

  /**
   * Lifecycle terminal reached. Sets {@code effectiveTo = effectiveFrom} and {@code
   * lifecycleStatus} to a terminal value on the final version.
   */
  record InstrumentRetired(
      String figi, long versionSeq, LifecycleStatus terminalStatus, long occurredAt)
      implements SecurityMasterEvent {}
}
