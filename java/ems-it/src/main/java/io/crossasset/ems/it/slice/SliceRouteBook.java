/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.fsm.generated.RouteFsmContext;
import io.crossasset.ems.fsm.generated.RouteFsmEffect;
import io.crossasset.ems.fsm.generated.RouteFsmEvent;
import io.crossasset.ems.fsm.generated.RouteFsmRunner;
import io.crossasset.ems.fsm.generated.RouteFsmState;
import io.crossasset.ems.fsm.generated.TransitionResult;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Live routes and their state, driven by the generated route FSM.
 *
 * <p>A route is the EMS's outbound projection of one order to one venue. As with {@link
 * SliceOrderBook}, the FSM is <strong>not</strong> reimplemented here — {@link RouteFsmRunner} is
 * generated from {@code schemas/fsm/route.fsm.yaml} and this class only decides which event to hand
 * it.
 *
 * <p><strong>One map, no derived indexes.</strong> "How much have we routed for this order" and "is
 * this route ClOrdID taken" are both answered by scanning {@link #routes}, not by side tables kept
 * in step with it. Scanning is O(n) in a book that holds tens of routes, and the alternative — two
 * maps that can disagree after a partial failure — is the bug that would actually cost something.
 * The scan order is the {@link TreeMap}'s, so the answer is deterministic.
 */
final class SliceRouteBook {

  /** A route, its FSM state, and the context the FSM threads through transitions. */
  record Entry(RouteFsmState state, RouteFsmContext context) {}

  private final SortedMap<String, Entry> routes = new TreeMap<>();

  /**
   * Opens a route in {@code PENDING} and immediately dispatches it.
   *
   * <p>Every route starts in the schema's initial state and is moved out of it by {@code
   * RouteSent}, exactly as an order is moved out of {@code PENDING_NEW} by a validation outcome.
   * Seeding a route straight into {@code SENT} would make the first transition unreachable by any
   * corpus case.
   */
  Optional<TransitionResult<RouteFsmState, RouteFsmContext, RouteFsmEffect>> open(
      String routeId, RouteFsmContext context) {
    routes.put(routeId, new Entry(RouteFsmState.PENDING, context));

    TransitionResult<RouteFsmState, RouteFsmContext, RouteFsmEffect> result =
        RouteFsmRunner.transition(RouteFsmState.PENDING, RouteFsmEvent.RouteSent, context, null);

    if (!result.isNoTransition()) {
      routes.put(routeId, new Entry(result.newState(), result.newContext()));
    }
    return Optional.of(result);
  }

  /**
   * Applies {@code event} to {@code routeId}.
   *
   * <p>Empty when the route is unknown. A result carrying {@code isNoTransition()} means the FSM
   * had no rule for this (state, event) pair — the venue said something the route was not in a
   * position to hear, which the schema answers by ignoring it.
   */
  Optional<TransitionResult<RouteFsmState, RouteFsmContext, RouteFsmEffect>> apply(
      String routeId, RouteFsmEvent event, @Nullable Object payload) {
    Entry entry = routes.get(routeId);
    if (entry == null) {
      return Optional.empty();
    }

    TransitionResult<RouteFsmState, RouteFsmContext, RouteFsmEffect> result =
        RouteFsmRunner.transition(entry.state(), event, entry.context(), payload);

    if (!result.isNoTransition()) {
      routes.put(routeId, new Entry(result.newState(), result.newContext()));
    }
    return Optional.of(result);
  }

  /**
   * States in which a route holds no quantity.
   *
   * <p>The venue killed the route without filling it, so nothing is committed anywhere and the
   * quantity is routable again. {@code FILLED} is deliberately absent — a filled route consumed its
   * quantity, and forgetting that would let an order be over-filled.
   */
  private static boolean releasesQuantity(RouteFsmState state) {
    return state == RouteFsmState.REJECTED
        || state == RouteFsmState.CANCELED
        || state == RouteFsmState.EXPIRED
        || state == RouteFsmState.SUPERSEDED;
  }

  /**
   * Quantity currently committed to venues for {@code orderId}.
   *
   * <p>Counts live and filled routes; a route the venue rejected, cancelled, expired or superseded
   * releases its quantity back. Until component 6b those states were unreachable, so this counted
   * every route and an order whose only route was refused could never be re-routed.
   */
  long routedQty(String orderId) {
    long total = 0;
    for (Entry entry : routes.values()) {
      if (entry.context().orderId().equals(orderId) && !releasesQuantity(entry.state())) {
        total += entry.context().routeQty();
      }
    }
    return total;
  }

  /** How many routes exist for {@code orderId}. Used to name the next one. */
  int countForOrder(String orderId) {
    int count = 0;
    for (Entry entry : routes.values()) {
      if (entry.context().orderId().equals(orderId)) {
        count++;
      }
    }
    return count;
  }

  /**
   * The state of {@code routeId}, or empty when no such route exists.
   *
   * <p>The runner journals <em>this</em>, not the state the transition result predicted. The two
   * agree today; reporting the book's own answer means a journal that says {@code to=SENT} is
   * claiming the book holds a route in {@code SENT}, rather than that the FSM said it would.
   */
  Optional<RouteFsmState> stateOf(String routeId) {
    return Optional.ofNullable(routes.get(routeId)).map(Entry::state);
  }

  /** The route, or empty when no such route exists. */
  Optional<Entry> get(String routeId) {
    return Optional.ofNullable(routes.get(routeId));
  }

  /** Whether any route already carries {@code clOrdId} — FIX requires them to be unique. */
  boolean hasClOrdId(String clOrdId) {
    for (Entry entry : routes.values()) {
      if (entry.context().clOrdId().equals(clOrdId)) {
        return true;
      }
    }
    return false;
  }
}
