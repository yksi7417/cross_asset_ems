/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/**
 * Converts applied corporate action events into security master supersession events.
 *
 * <p>When a corporate action reaches the APPLIED state, certain action types (splits, mergers,
 * spin-offs, name changes, conversions) require that the affected instruments be versioned in the
 * security master. This bridge produces the appropriate {@link SecurityMasterEvent} given the
 * applied action and the caller-supplied new or terminal instrument version.
 *
 * <p>Responsibility boundary: the caller supplies the correct {@link InstrumentVersioned} for the
 * new version (including setting {@code causedByCorporateActionEventId} to the CA's ID). This
 * bridge validates the linkage and assembles the event.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public final class CorporateActionBridge {

  private CorporateActionBridge() {}

  /**
   * Builds an {@link SecurityMasterEvent.InstrumentSuperseded} from an applied corporate action.
   *
   * @param ca the corporate action in APPLIED state
   * @param figi the FIGI of the instrument being superseded
   * @param priorVersionSeq the version sequence of the version being superseded
   * @param newVersion the new instrument version; its {@code causedByCorporateActionEventId} must
   *     equal {@code ca.caId()}
   * @param occurredAt epoch-millis timestamp for the event
   * @throws IllegalStateException if the corporate action is not APPLIED, or if {@code
   *     newVersion.causedByCorporateActionEventId()} does not match {@code ca.caId()}
   */
  public static SecurityMasterEvent.InstrumentSuperseded toSupersession(
      CorporateAction ca,
      String figi,
      long priorVersionSeq,
      InstrumentVersioned newVersion,
      long occurredAt) {

    requireApplied(ca);

    if (!ca.caId().equals(newVersion.causedByCorporateActionEventId())) {
      throw new IllegalStateException(
          "newVersion.causedByCorporateActionEventId "
              + newVersion.causedByCorporateActionEventId()
              + " does not match CA id "
              + ca.caId());
    }

    return new SecurityMasterEvent.InstrumentSuperseded(
        figi, priorVersionSeq, newVersion, occurredAt);
  }

  /**
   * Builds a {@link SecurityMasterEvent.InstrumentRetired} from an applied corporate action that
   * terminates an instrument (e.g., redemption, merger producing no direct successor).
   *
   * @param ca the corporate action in APPLIED state
   * @param figi the FIGI of the instrument being retired
   * @param versionSeq the version sequence of the version being retired
   * @param terminalStatus the terminal lifecycle status (EXPIRED, MATURED, etc.)
   * @param occurredAt epoch-millis timestamp for the event
   */
  public static SecurityMasterEvent.InstrumentRetired toRetirement(
      CorporateAction ca,
      String figi,
      long versionSeq,
      LifecycleStatus terminalStatus,
      long occurredAt) {

    requireApplied(ca);

    if (terminalStatus == LifecycleStatus.ACTIVE || terminalStatus == LifecycleStatus.UNKNOWN) {
      throw new IllegalArgumentException(
          "terminalStatus must be a non-active terminal status, got: " + terminalStatus);
    }

    return new SecurityMasterEvent.InstrumentRetired(figi, versionSeq, terminalStatus, occurredAt);
  }

  private static void requireApplied(CorporateAction ca) {
    if (ca.state() != CorporateActionState.APPLIED) {
      throw new IllegalStateException(
          "Corporate action must be in APPLIED state, but was: " + ca.state());
    }
  }
}
