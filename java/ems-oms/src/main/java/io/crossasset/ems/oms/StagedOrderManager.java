/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import java.util.List;
import java.util.Optional;

/**
 * Manages the pre-route lifecycle of staged orders.
 *
 * <p>Stages, amends, cancels, and marks orders ready for routing. All operations return sealed
 * result types so callers handle both success and rejection without exceptions.
 *
 * <p>Per arch-order-staged.md. Router interaction (7.2), automation (7.3), and multi-leg (7.4)
 * extend this layer.
 */
public interface StagedOrderManager {

  /**
   * Stages a new order. Runs the validator pipeline (session + ref + perm), then fires
   * ValidationPassed to advance the FSM from PENDING_NEW to NEW.
   */
  StageResult stage(OrderRequest request);

  /**
   * Pre-route field edit. Does not send a 35=G to the venue. Increments orderVersion and
   * re-validates the session. Returns the updated order on success.
   */
  AmendResult amend(String orderId, AmendFields fields, long sessionId);

  /**
   * Cancels a non-terminal staged order by firing CancelRequested then CancelAccepted back-to-back
   * (no venue round-trip for an unrouted order).
   */
  CancelResult cancel(String orderId, long sessionId);

  /**
   * Marks an order ready for routing. Re-runs the full validator pipeline and verifies that no
   * pending actions remain. Transitions subState from NEW to READY.
   */
  MarkReadyResult markReady(String orderId, long sessionId);

  /**
   * Clears a pending action reference. When all pending actions are cleared, the order sub-state
   * becomes eligible for markReady.
   */
  void setPendingActionDone(String orderId, String actionRef);

  /** Returns the order if it exists, empty otherwise. */
  Optional<StagedOrder> findOrder(String orderId);

  /**
   * Every non-terminal order (task 18.4). The kill switch's mass-cancel enumerates from the
   * authoritative store — never from a projection — because missing an order here is a silent
   * failure on the control path.
   */
  List<StagedOrder> activeOrders();

  /**
   * Applies a raw FSM event directly to the order (used by the router layer to propagate fills and
   * expirations). Returns the updated order, or empty if the order is not found. If the event is a
   * no-transition in the current state, the order is returned unchanged.
   */
  Optional<StagedOrder> applyOrderFsmEvent(String orderId, OrderFsmEvent event, Object payload);

  /**
   * Transitions the order subState to {@link OrderSubState#ROUTING}. Called by the router on first
   * route creation. Returns the updated order, or empty if the order is not found.
   */
  Optional<StagedOrder> markRouting(String orderId);
}
