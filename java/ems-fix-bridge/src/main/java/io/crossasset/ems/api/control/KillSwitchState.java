/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Shared kill-switch state (task 18.4): the set of engaged scopes, read by the order/route entry
 * guards on every action and mutated only by {@link KillSwitchService}. Split from the service so
 * the guards and the service can reference it without a construction cycle.
 *
 * <p>All methods synchronize on this object — the lockout check is a handful of equality tests on a
 * set that is almost always empty, and the control path must never see a torn update.
 */
public final class KillSwitchState {

  /** What a kill covers. */
  public enum Kind {
    /** Everything in the firm: order entry, mark-ready, all routing. */
    FIRM,
    /** One desk's order entry, mark-ready, and routing. */
    DESK,
    /** Routing to one venue MIC (order staging elsewhere unaffected). */
    VENUE
  }

  /** One engaged scope. {@code value} is the firmId / deskId / venue MIC. */
  public record Scope(Kind kind, String value) {
    public Scope {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
      return kind + ":" + value;
    }
  }

  private final Set<Scope> engaged = new LinkedHashSet<>();
  private final Set<Long> cancelOnDisconnect = new LinkedHashSet<>();

  synchronized boolean engage(Scope scope) {
    return engaged.add(scope);
  }

  synchronized boolean release(Scope scope) {
    return engaged.remove(scope);
  }

  /** Engaged scopes, in engage order. */
  public synchronized Set<Scope> engagedScopes() {
    return new LinkedHashSet<>(engaged);
  }

  /** Order entry (stage / mark-ready) locked for this identity? */
  public synchronized boolean ordersLocked(String firmId, String deskId) {
    for (Scope scope : engaged) {
      if (scope.kind() == Kind.FIRM && scope.value().equals(firmId)) {
        return true;
      }
      if (scope.kind() == Kind.DESK && scope.value().equals(deskId)) {
        return true;
      }
    }
    return false;
  }

  /** Routing locked for this identity + venue? */
  public synchronized boolean routingLocked(String firmId, String deskId, String venueMic) {
    if (ordersLocked(firmId, deskId)) {
      return true;
    }
    for (Scope scope : engaged) {
      if (scope.kind() == Kind.VENUE && scope.value().equals(venueMic)) {
        return true;
      }
    }
    return false;
  }

  synchronized void armCancelOnDisconnect(long sessionId) {
    cancelOnDisconnect.add(sessionId);
  }

  synchronized boolean isArmedCancelOnDisconnect(long sessionId) {
    return cancelOnDisconnect.contains(sessionId);
  }

  synchronized void disarmCancelOnDisconnect(long sessionId) {
    cancelOnDisconnect.remove(sessionId);
  }
}
