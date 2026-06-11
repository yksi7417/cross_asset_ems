/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmRunner;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.fsm.generated.TransitionResult;
import io.crossasset.ems.validator.ValidationRequest;
import io.crossasset.ems.validator.ValidationResult;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory implementation of {@link StagedOrderManager}.
 *
 * <p>Uses a {@link ConcurrentHashMap} for order storage and {@link AtomicLong} for order ID
 * generation. Cancel transitions are performed atomically via {@code compute()}.
 */
public final class InMemoryStagedOrderManager implements StagedOrderManager {

  private final ValidatorPipeline validatorPipeline;
  private final ConcurrentHashMap<String, StagedOrder> orders = new ConcurrentHashMap<>();
  private final AtomicLong orderIdSeq = new AtomicLong(1);

  /** Per-session ClOrdID dedup (FIX session rules; backs idempotent re-import, task 8.8). */
  private final ConcurrentHashMap<String, String> clOrdIdIndex = new ConcurrentHashMap<>();

  public InMemoryStagedOrderManager(ValidatorPipeline validatorPipeline) {
    this.validatorPipeline = Objects.requireNonNull(validatorPipeline, "validatorPipeline");
  }

  @Override
  public StageResult stage(OrderRequest request) {
    if (request.qty() <= 0) {
      return new StageResult.Rejected(request.requestId(), "EMS-ORD-2001", "OrderQty must be > 0.");
    }
    String dedupKey = request.sessionId() + "|" + request.clOrdId();
    if (clOrdIdIndex.containsKey(dedupKey)) {
      return new StageResult.Rejected(
          request.requestId(),
          "EMS-ORD-2510",
          "ClOrdID " + request.clOrdId() + " already used on this session.");
    }
    ValidationResult vr =
        validatorPipeline.validate(
            new ValidationRequest(request.requestId(), request.sessionId(), null, request.figi()));
    if (vr instanceof ValidationResult.Reject reject) {
      return new StageResult.Rejected(request.requestId(), reject.code(), reject.message());
    }
    String orderId = "EMS-ORD-" + orderIdSeq.getAndIncrement();
    long nowMicros = System.currentTimeMillis() * 1_000L;
    OrderFsmContext ctx =
        new OrderFsmContext(
            orderId,
            request.clOrdId(),
            null,
            request.figi(),
            request.side(),
            request.qty(),
            request.price(),
            0L,
            request.qty(),
            request.account(),
            request.tif(),
            request.clOrdId(),
            orderId,
            1L,
            null,
            null);
    TransitionResult<OrderFsmState, OrderFsmContext, ?> tr =
        OrderFsmRunner.transition(
            OrderFsmState.PENDING_NEW, OrderFsmEvent.ValidationPassed, ctx, null);
    StagedOrder order =
        new StagedOrder(
            orderId,
            request.clOrdId(),
            request.sessionId(),
            tr.newState(),
            tr.newContext(),
            OrderSubState.NEW,
            Set.of(),
            nowMicros);
    orders.put(orderId, order);
    clOrdIdIndex.put(dedupKey, orderId);
    return new StageResult.Accepted(order);
  }

  @Override
  public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
    StagedOrder order = orders.get(orderId);
    if (order == null) {
      return new AmendResult.Rejected(orderId, "EMS-ORD-4001", "Order " + orderId + " not found.");
    }
    if (order.isTerminal()) {
      return new AmendResult.Rejected(
          orderId,
          "EMS-ORD-3001",
          "Order " + orderId + " is in terminal state " + order.fsmState() + ".");
    }
    ValidationResult vr =
        validatorPipeline.validate(new ValidationRequest(orderId, sessionId, null, null));
    if (vr instanceof ValidationResult.Reject reject) {
      return new AmendResult.Rejected(orderId, reject.code(), reject.message());
    }
    OrderFsmContext ctx = order.fsmContext();
    long newQty = fields.qty() != null ? fields.qty() : ctx.orderQty();
    Long newPrice = fields.price() != null ? fields.price() : ctx.price();
    if (newQty <= 0) {
      return new AmendResult.Rejected(orderId, "EMS-ORD-2001", "OrderQty must be > 0.");
    }
    long newLeavesQty = newQty - ctx.cumQty();
    OrderFsmContext updatedCtx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            newQty,
            newPrice,
            ctx.cumQty(),
            newLeavesQty,
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion() + 1,
            ctx.preCancelStatus(),
            ctx.preReplaceStatus());
    StagedOrder updated =
        new StagedOrder(
            order.orderId(),
            order.clOrdId(),
            order.sessionId(),
            order.fsmState(),
            updatedCtx,
            order.subState(),
            order.pendingActions(),
            order.stagedAtMicros());
    orders.put(orderId, updated);
    return new AmendResult.Amended(updated);
  }

  @Override
  public CancelResult cancel(String orderId, long sessionId) {
    ValidationResult vr =
        validatorPipeline.validate(new ValidationRequest(orderId, sessionId, null, null));
    if (vr instanceof ValidationResult.Reject reject) {
      return new CancelResult.Rejected(orderId, reject.code(), reject.message());
    }
    AtomicReference<CancelResult> result = new AtomicReference<>();
    orders.compute(
        orderId,
        (id, order) -> {
          if (order == null) {
            result.set(
                new CancelResult.Rejected(
                    orderId, "EMS-ORD-4001", "Order " + orderId + " not found."));
            return null;
          }
          if (order.isTerminal()) {
            result.set(
                new CancelResult.Rejected(
                    orderId,
                    "EMS-ORD-3001",
                    "Order "
                        + orderId
                        + " is already in terminal state "
                        + order.fsmState()
                        + "."));
            return order;
          }
          TransitionResult<OrderFsmState, OrderFsmContext, ?> r1 =
              OrderFsmRunner.transition(
                  order.fsmState(), OrderFsmEvent.CancelRequested, order.fsmContext(), null);
          if (r1.isNoTransition()) {
            result.set(
                new CancelResult.Rejected(
                    orderId,
                    "EMS-ORD-3001",
                    "Order " + orderId + " cannot be canceled in state " + order.fsmState() + "."));
            return order;
          }
          TransitionResult<OrderFsmState, OrderFsmContext, ?> r2 =
              OrderFsmRunner.transition(
                  r1.newState(), OrderFsmEvent.CancelAccepted, r1.newContext(), null);
          StagedOrder canceled =
              new StagedOrder(
                  order.orderId(),
                  order.clOrdId(),
                  order.sessionId(),
                  r2.newState(),
                  r2.newContext(),
                  order.subState(),
                  order.pendingActions(),
                  order.stagedAtMicros());
          result.set(new CancelResult.Canceled(canceled));
          return canceled;
        });
    return result.get();
  }

  @Override
  public MarkReadyResult markReady(String orderId, long sessionId) {
    StagedOrder order = orders.get(orderId);
    if (order == null) {
      return new MarkReadyResult.Rejected(
          orderId, "EMS-ORD-4001", "Order " + orderId + " not found.");
    }
    if (order.isTerminal()) {
      return new MarkReadyResult.Rejected(
          orderId,
          "EMS-ORD-3001",
          "Order " + orderId + " is in terminal state " + order.fsmState() + ".");
    }
    if (!order.pendingActions().isEmpty()) {
      return new MarkReadyResult.Rejected(
          orderId,
          "EMS-ORD-1001",
          "Order " + orderId + " has pending actions: " + order.pendingActions() + ".");
    }
    ValidationResult vr =
        validatorPipeline.validate(
            new ValidationRequest(orderId, sessionId, null, order.fsmContext().instrumentId()));
    if (vr instanceof ValidationResult.Reject reject) {
      return new MarkReadyResult.Rejected(orderId, reject.code(), reject.message());
    }
    StagedOrder ready =
        new StagedOrder(
            order.orderId(),
            order.clOrdId(),
            order.sessionId(),
            order.fsmState(),
            order.fsmContext(),
            OrderSubState.READY,
            order.pendingActions(),
            order.stagedAtMicros());
    orders.put(orderId, ready);
    return new MarkReadyResult.Ready(ready);
  }

  @Override
  public void setPendingActionDone(String orderId, String actionRef) {
    orders.computeIfPresent(
        orderId,
        (id, order) -> {
          Set<String> remaining = new HashSet<>(order.pendingActions());
          remaining.remove(actionRef);
          return new StagedOrder(
              order.orderId(),
              order.clOrdId(),
              order.sessionId(),
              order.fsmState(),
              order.fsmContext(),
              order.subState(),
              remaining,
              order.stagedAtMicros());
        });
  }

  @Override
  public Optional<StagedOrder> findOrder(String orderId) {
    return Optional.ofNullable(orders.get(orderId));
  }

  @Override
  public Optional<StagedOrder> applyOrderFsmEvent(
      String orderId, OrderFsmEvent event, Object payload) {
    AtomicReference<StagedOrder> result = new AtomicReference<>();
    orders.computeIfPresent(
        orderId,
        (id, order) -> {
          TransitionResult<OrderFsmState, OrderFsmContext, ?> tr =
              OrderFsmRunner.transition(order.fsmState(), event, order.fsmContext(), payload);
          StagedOrder next =
              tr.isNoTransition()
                  ? order
                  : new StagedOrder(
                      order.orderId(),
                      order.clOrdId(),
                      order.sessionId(),
                      tr.newState(),
                      tr.newContext(),
                      order.subState(),
                      order.pendingActions(),
                      order.stagedAtMicros());
          result.set(next);
          return next;
        });
    return Optional.ofNullable(result.get());
  }

  @Override
  public Optional<StagedOrder> markRouting(String orderId) {
    AtomicReference<StagedOrder> result = new AtomicReference<>();
    orders.computeIfPresent(
        orderId,
        (id, order) -> {
          StagedOrder routing =
              new StagedOrder(
                  order.orderId(),
                  order.clOrdId(),
                  order.sessionId(),
                  order.fsmState(),
                  order.fsmContext(),
                  OrderSubState.ROUTING,
                  order.pendingActions(),
                  order.stagedAtMicros());
          result.set(routing);
          return routing;
        });
    return Optional.ofNullable(result.get());
  }
}
