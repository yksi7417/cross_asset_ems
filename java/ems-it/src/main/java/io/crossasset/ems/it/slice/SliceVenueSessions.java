/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.fsm.generated.TransitionResult;
import io.crossasset.ems.fsm.generated.VenueSessionFsmContext;
import io.crossasset.ems.fsm.generated.VenueSessionFsmEffect;
import io.crossasset.ems.fsm.generated.VenueSessionFsmEvent;
import io.crossasset.ems.fsm.generated.VenueSessionFsmRunner;
import io.crossasset.ems.fsm.generated.VenueSessionFsmState;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One FIX session per venue, driven by the generated venue-session FSM.
 *
 * <p>Keyed on the venue MIC. As with the order and route books the FSM is not reimplemented here —
 * {@link VenueSessionFsmRunner} is generated from {@code schemas/fsm/venue_session.fsm.yaml}.
 *
 * <p><strong>A venue with no session is not a venue with a broken session.</strong> {@link
 * #isActive} answers false for both, which is what the routing gate wants; {@link #stateOf} keeps
 * them apart, because "we never connected" and "we connected and got logged out" are different
 * things to tell an operator.
 */
final class SliceVenueSessions {

  /** A session, its FSM state, and the context the FSM threads through transitions. */
  record Entry(VenueSessionFsmState state, VenueSessionFsmContext context) {}

  private final SortedMap<String, Entry> sessions = new TreeMap<>();

  /**
   * Applies {@code event} to the session for {@code venueMic}, opening one if needed.
   *
   * <p>A venue we have never heard of starts in the schema's initial state — {@code DISCONNECTED} —
   * rather than being rejected. That is what makes {@code ConnectRequested} the first transition a
   * corpus case can exercise, exactly as {@code ValidationPassed} is for an order.
   */
  TransitionResult<VenueSessionFsmState, VenueSessionFsmContext, VenueSessionFsmEffect> apply(
      String venueMic, VenueSessionFsmEvent event) {
    Entry entry = sessions.get(venueMic);
    if (entry == null) {
      entry = new Entry(VenueSessionFsmState.DISCONNECTED, contextFor(venueMic));
      sessions.put(venueMic, entry);
    }

    TransitionResult<VenueSessionFsmState, VenueSessionFsmContext, VenueSessionFsmEffect> result =
        VenueSessionFsmRunner.transition(entry.state(), event, entry.context(), null);

    if (!result.isNoTransition()) {
      sessions.put(venueMic, new Entry(result.newState(), result.newContext()));
    }
    return result;
  }

  /** The state of the session for {@code venueMic}, or empty when we have never seen it. */
  Optional<VenueSessionFsmState> stateOf(String venueMic) {
    return Optional.ofNullable(sessions.get(venueMic)).map(Entry::state);
  }

  /**
   * Whether {@code venueMic} can currently take an order.
   *
   * <p>{@code ACTIVE} only. A session in {@code LOGON_SENT} has a TCP connection and no agreed
   * sequence numbers; one in {@code RESEND_IN_PROGRESS} is mid-gap-fill. Sending a new order into
   * either is how you end up with an order the venue has and the EMS cannot account for.
   */
  boolean isActive(String venueMic) {
    return stateOf(venueMic).filter(state -> state == VenueSessionFsmState.ACTIVE).isPresent();
  }

  private static VenueSessionFsmContext contextFor(String venueMic) {
    return new VenueSessionFsmContext("SES-" + venueMic, 1L, 1L, 30L, false, 0L, 0L, venueMic);
  }
}
