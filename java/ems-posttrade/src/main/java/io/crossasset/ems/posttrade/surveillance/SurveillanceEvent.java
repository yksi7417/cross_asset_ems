/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import java.util.Objects;

/**
 * One normalized order/exec event on the surveillance export stream (task 12.15,
 * [[arch-surveillance]]). The OMS lifecycle (new/modify/cancel/exec) is flattened into the
 * minimal shape detectors need; the original event id is retained so alerts can cite their
 * evidence ({@code subject_events}) back into the audit spine.
 *
 * @param eventId the source event's id (audit linkage)
 * @param type lifecycle event kind
 * @param actor who did it (user/desk credential — the surveillance SUBJECT)
 * @param instrumentId what
 * @param side FIX side (1 buy / 2 sell / 5 sell short)
 * @param qty order or execution quantity
 * @param price fixed-point 1e4; 0 for market orders
 * @param tsMicros event time
 */
public record SurveillanceEvent(
    String eventId,
    Type type,
    String actor,
    String instrumentId,
    int side,
    long qty,
    long price,
    long tsMicros) {

  public enum Type {
    NEW_ORDER,
    MODIFY,
    CANCEL,
    EXECUTION
  }

  public SurveillanceEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(instrumentId, "instrumentId");
  }
}
