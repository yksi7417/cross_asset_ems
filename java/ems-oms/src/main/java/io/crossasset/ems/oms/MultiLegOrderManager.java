/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Optional;

/**
 * Manages multi-leg / package orders: one parent order whose execution intent is N inter-dependent
 * legs. The parent runs the generated MultiLeg FSM (STAGED → READY → LEGS_WORKING → terminal); each
 * leg rides the existing Route FSM via the {@link RouteManager}.
 *
 * <p>Execution-mode semantics (per arch-multileg.md):
 *
 * <ul>
 *   <li>{@code ALL_OR_NONE} — all legs route together to one venue; any leg rejection voids the
 *       package and cascade-cancels the surviving leg routes.
 *   <li>{@code LEGS_INDEPENDENT} — legs route in parallel; partial outcomes accepted.
 *   <li>{@code SEQUENCED} — leg N+1 is dispatched only after leg N fills; a failure halts emission.
 * </ul>
 *
 * <p>Leg-outcome events (fill / reject / cancel) arrive through the {@code leg*} methods, which
 * apply the venue event to the leg's route first and then drive the parent FSM. Per
 * arch-multileg.md, task 7.4.
 */
public interface MultiLegOrderManager {

  /**
   * Stages a multi-leg package. Structural checks (EMS-ORD-4001 package shape / venue homogeneity,
   * EMS-ORD-4002 missing package_id, EMS-ORD-4003 invalid sequence policy, EMS-ORD-2001 leg qty)
   * run first, then the validator pipeline per leg. Failures persist the package in REJECTED state;
   * success leaves it READY.
   */
  MultiLegStageResult stage(MultiLegOrderRequest request);

  /**
   * Starts execution of a READY package: stages + routes all legs (ALL_OR_NONE, LEGS_INDEPENDENT)
   * or the first leg only (SEQUENCED), then advances the parent to LEGS_WORKING via
   * FirstLegDispatched.
   */
  MultiLegEventResult dispatch(String orderId);

  /**
   * Package-level cancel (READY or LEGS_WORKING → CANCELED). Dispatches venue cancels on every
   * non-terminal leg route; venue confirmations arrive later via {@link #legCanceled}.
   */
  MultiLegEventResult cancel(String orderId, long sessionId);

  /** Venue acknowledged the leg's route (SENT → WORKING). No parent FSM effect. */
  MultiLegEventResult legAcknowledged(String orderId, String legId);

  /** Partial fill on a leg. Parent stays LEGS_WORKING (self-loop). */
  MultiLegEventResult legPartiallyFilled(
      String orderId, String legId, long lastQty, long lastPx, String execId);

  /**
   * Final fill on a leg. Drives the parent's LegFilled transition (mode guards decide FILLED /
   * PARTIALLY_FILLED / stay-working) and, under SEQUENCED, dispatches the next pending leg.
   */
  MultiLegEventResult legFilled(
      String orderId, String legId, long lastQty, long lastPx, String execId);

  /**
   * Venue rejected the leg on submission (route SENT → REJECTED). Mode guards decide the parent
   * outcome; under ALL_OR_NONE the surviving leg routes are cascade-canceled.
   */
  MultiLegEventResult legRejected(String orderId, String legId);

  /**
   * Venue confirmed a leg cancel (route PENDING_CANCEL_AT_VENUE → CANCELED). Benign when the parent
   * is already terminal (e.g. after a package-level cancel).
   */
  MultiLegEventResult legCanceled(String orderId, String legId);

  /** Returns the package with live leg states if it exists, empty otherwise. */
  Optional<MultiLegOrder> findOrder(String orderId);
}
