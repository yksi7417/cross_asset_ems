/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmEffect;
import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmPayloads;
import io.crossasset.ems.fsm.generated.OrderFsmRunner;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.fsm.generated.TransitionResult;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Live orders and their state, driven by the generated order FSM.
 *
 * <p>The FSM is <strong>not</strong> reimplemented here. {@link OrderFsmRunner} is generated from
 * {@code schemas/fsm/order.fsm.yaml}, and this class only decides which FSM event an inbound
 * journal event corresponds to, then hands it over. That split is what lets the Rust and C++ ports
 * mirror the same 31 transitions without three people reading the YAML and disagreeing.
 *
 * <p>Backed by a {@link TreeMap}: iteration order reaches the output journal, which the conformance
 * gate compares byte-for-byte.
 */
final class SliceOrderBook {

  /** An order, its FSM state, and the context the FSM threads through transitions. */
  record Entry(OrderFsmState state, OrderFsmContext context) {}

  private final SortedMap<String, Entry> orders = new TreeMap<>();

  /**
   * Opens an order in {@code PENDING_NEW} and immediately applies {@code event}.
   *
   * <p>Every order starts in the schema's initial state and is moved out of it by an explicit
   * transition — {@code ValidationPassed} or {@code ValidationFailed}. Seeding an order directly
   * into {@code NEW} would skip the first transition, and then a corpus case could never exercise
   * it, which is precisely what {@code fsm-coverage} exists to notice.
   */
  Optional<TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect>> open(
      String orderId, OrderFsmContext context, OrderFsmEvent event) {
    orders.put(orderId, new Entry(OrderFsmState.PENDING_NEW, context));
    return apply(orderId, event, null);
  }

  /**
   * Applies {@code event} to {@code orderId}.
   *
   * <p>Returns empty when the order is unknown. Returns a result carrying {@code isNoTransition()}
   * when the FSM had no rule for this (state, event) pair — an unexpected venue message leaves the
   * order where it was, which is the schema's own answer, not a decision taken here.
   */
  Optional<TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect>> apply(
      String orderId, OrderFsmEvent event, @Nullable Object payload) {
    Entry entry = orders.get(orderId);
    if (entry == null) {
      return Optional.empty();
    }

    TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect> result =
        OrderFsmRunner.transition(entry.state(), event, entry.context(), payload);

    if (!result.isNoTransition()) {
      orders.put(orderId, new Entry(result.newState(), result.newContext()));
    }
    return Optional.of(result);
  }

  Optional<Entry> get(String orderId) {
    return Optional.ofNullable(orders.get(orderId));
  }

  /** Builds the fill payload the FSM's fill transitions read. */
  static OrderFsmPayloads.PartialFillPayload partialFill(long lastQty, long lastPx, String execId) {
    return new OrderFsmPayloads.PartialFillPayload(lastQty, lastPx, execId);
  }

  /** Builds the full-fill payload. */
  static OrderFsmPayloads.FullFillPayload fullFill(long lastQty, long lastPx, String execId) {
    return new OrderFsmPayloads.FullFillPayload(lastQty, lastPx, execId);
  }
}
