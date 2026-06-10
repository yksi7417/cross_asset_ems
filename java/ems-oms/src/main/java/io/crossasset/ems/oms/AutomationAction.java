/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import org.jspecify.annotations.Nullable;

/**
 * Action produced by an {@link AutomationRule} when its condition evaluates to {@code true}.
 *
 * <p>Actions are descriptors only — the engine returns them; callers execute them against the
 * appropriate manager (RouteManager, StagedOrderManager). This keeps the engine side-effect-free
 * and easily testable.
 */
public sealed interface AutomationAction
    permits AutomationAction.RouteOrders,
        AutomationAction.CancelRoute,
        AutomationAction.SetPendingActionDone,
        AutomationAction.MarkOrderReady {

  /** Route the triggering order to {@code venueMic} at optional {@code price} (null = market). */
  record RouteOrders(String venueMic, @Nullable Long price) implements AutomationAction {
    public RouteOrders(String venueMic) {
      this(venueMic, null);
    }
  }

  /** Request cancellation of a specific route. */
  record CancelRoute(String routeId) implements AutomationAction {}

  /** Mark a pending action as satisfied so the order can progress. */
  record SetPendingActionDone(String pendingActionKey) implements AutomationAction {}

  /** Transition the order sub-state from ACCEPTED to READY. */
  record MarkOrderReady() implements AutomationAction {}
}
