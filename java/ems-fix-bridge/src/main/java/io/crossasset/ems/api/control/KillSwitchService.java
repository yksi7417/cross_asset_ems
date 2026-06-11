/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.aaa.Session;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.control.KillSwitchState.Kind;
import io.crossasset.ems.api.control.KillSwitchState.Scope;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.Route;
import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Firm-wide kill switch (task 18.4) — the control path a head trader attests to under SEC 15c3-5.
 * One audited action per engage/release:
 *
 * <ul>
 *   <li><b>Lockout first.</b> The scope is engaged <em>before</em> any cancel is attempted, so even
 *       a partial mass-cancel leaves no window where new orders slip through. Lockout is enforced
 *       by {@link KillSwitchOrderGuard} / {@link KillSwitchRouteGuard} in front of the managers —
 *       every entry surface (REST, FIX, native) passes through them.
 *   <li><b>Mass-cancel, fully accounted.</b> FIRM/DESK scopes cancel every in-scope working order
 *       (venue route cancels cascade first, then the staged order); VENUE scope cancels that
 *       venue's open routes. Every target produces an audit line — success or failure — and
 *       failures are counted, surfaced on the result, and published. Nothing is silent.
 *   <li><b>Cancel-on-disconnect.</b> Armed sessions get the same mass-cancel treatment for their
 *       orders when the session layer reports the disconnect.
 *   <li><b>Authorization.</b> Engage/release require the {@code #kill-switch} tag (EMS-AUT-9601).
 * </ul>
 *
 * <p>Engage/release events publish on topic {@code control.kill} for the desktop banner; the full
 * audit journal is queryable for the 15c3-5 evidence pack (18.5).
 */
public final class KillSwitchService {

  /** Control topic carrying engage/release events. */
  public static final String TOPIC_KILL = "control.kill";

  /** Tag required to operate the switch. */
  public static final String REQUIRED_TAG = "#kill-switch";

  /** One audit line: what was done to one target (order / route), or a scope-level note. */
  public record TargetOutcome(String targetId, String action, boolean ok, String detail) {}

  /** The audited record of one engage/release. */
  public record KillAudit(
      String action,
      Scope scope,
      String by,
      String reason,
      long atMillis,
      List<TargetOutcome> outcomes,
      int failures) {}

  /** Result returned to the caller; failures demand operator attention, never silence. */
  public sealed interface KillResult {
    record Done(KillAudit audit) implements KillResult {}

    record Rejected(String code, String message) implements KillResult {}
  }

  private final AaaService aaa;
  private final StagedOrderManager som;
  private final RouteManager routes;
  private final KillSwitchState state;
  private final SubscriptionRegistry subscriptions;
  private final LongSupplier nowMillis;
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<KillAudit> journal = new ArrayList<>();

  public KillSwitchService(
      AaaService aaa,
      StagedOrderManager som,
      RouteManager routes,
      KillSwitchState state,
      SubscriptionRegistry subscriptions,
      LongSupplier nowMillis) {
    this.aaa = Objects.requireNonNull(aaa, "aaa");
    this.som = Objects.requireNonNull(som, "som");
    this.routes = Objects.requireNonNull(routes, "routes");
    this.state = Objects.requireNonNull(state, "state");
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
  }

  /** Engage a scope: lockout immediately, then mass-cancel everything in scope. Audited. */
  public synchronized KillResult engage(Scope scope, long bySessionId, String reason) {
    Optional<Session> session = authorize(bySessionId);
    if (session.isEmpty()) {
      return new KillResult.Rejected(
          "EMS-AUT-9601", "Engaging the kill switch requires the #kill-switch tag.");
    }
    if (!state.engage(scope)) {
      return new KillResult.Rejected("EMS-AUT-9602", "Scope already engaged: " + scope);
    }
    // Lockout is now active; everything below is cleanup that cannot re-open the gate.
    List<TargetOutcome> outcomes = new ArrayList<>();
    if (scope.kind() == Kind.VENUE) {
      cancelVenueRoutes(scope.value(), outcomes);
    } else {
      cancelScopedOrders(scope, bySessionId, outcomes);
    }
    return audited("ENGAGE", scope, session.get(), reason, outcomes);
  }

  /** Release a scope. Audited; does not resurrect canceled orders. */
  public synchronized KillResult release(Scope scope, long bySessionId, String reason) {
    Optional<Session> session = authorize(bySessionId);
    if (session.isEmpty()) {
      return new KillResult.Rejected(
          "EMS-AUT-9601", "Releasing the kill switch requires the #kill-switch tag.");
    }
    if (!state.release(scope)) {
      return new KillResult.Rejected("EMS-AUT-9602", "Scope not engaged: " + scope);
    }
    return audited("RELEASE", scope, session.get(), reason, List.of());
  }

  /** Engaged scopes (status endpoint / desktop banner). */
  public synchronized List<Scope> engaged() {
    return List.copyOf(state.engagedScopes());
  }

  /** Full audit journal, oldest first (15c3-5 evidence pack input). */
  public synchronized List<KillAudit> journal() {
    return List.copyOf(journal);
  }

  /** Arm cancel-on-disconnect for a session (venue CoD semantics at the client edge). */
  public void armCancelOnDisconnect(long sessionId) {
    state.armCancelOnDisconnect(sessionId);
  }

  /**
   * Session-layer disconnect hook: cancels the dead session's working orders if it was armed.
   * Audited like any other kill action (action=DISCONNECT, scope=DESK of the dead session).
   */
  public synchronized Optional<KillAudit> onSessionDisconnected(long sessionId) {
    if (!state.isArmedCancelOnDisconnect(sessionId)) {
      return Optional.empty();
    }
    state.disarmCancelOnDisconnect(sessionId);
    List<TargetOutcome> outcomes = new ArrayList<>();
    for (StagedOrder order : som.activeOrders()) {
      if (order.sessionId() == sessionId) {
        cancelOrderWithRoutes(order, sessionId, outcomes);
      }
    }
    KillAudit audit =
        new KillAudit(
            "DISCONNECT",
            new Scope(Kind.DESK, "session-" + sessionId),
            "session-" + sessionId,
            "cancel-on-disconnect",
            nowMillis.getAsLong(),
            List.copyOf(outcomes),
            (int) outcomes.stream().filter(o -> !o.ok()).count());
    journal.add(audit);
    publish(audit);
    return Optional.of(audit);
  }

  // ── Mass-cancel internals ────────────────────────────────────────────────────

  private void cancelScopedOrders(Scope scope, long bySessionId, List<TargetOutcome> outcomes) {
    for (StagedOrder order : som.activeOrders()) {
      Optional<Session> orderSession = aaa.sessionInfo(order.sessionId());
      boolean inScope;
      if (orderSession.isEmpty()) {
        // Conservative on FIRM scope: an unattributable order is canceled, visibly.
        inScope = scope.kind() == Kind.FIRM;
        if (!inScope) {
          outcomes.add(
              new TargetOutcome(
                  order.orderId(),
                  "SKIP",
                  true,
                  "session " + order.sessionId() + " unresolved; outside DESK scope proof"));
          continue;
        }
      } else {
        inScope =
            switch (scope.kind()) {
              case FIRM -> orderSession.get().identity().firmId().equals(scope.value());
              case DESK -> orderSession.get().identity().deskId().equals(scope.value());
              case VENUE -> false;
            };
      }
      if (inScope) {
        cancelOrderWithRoutes(order, bySessionId, outcomes);
      }
    }
  }

  private void cancelOrderWithRoutes(
      StagedOrder order, long bySessionId, List<TargetOutcome> outcomes) {
    for (RouteEventResult result : routes.cascadeOrderCancel(order.orderId())) {
      if (result instanceof RouteEventResult.Applied applied) {
        outcomes.add(
            new TargetOutcome(
                applied.route().routeId(), "ROUTE_CANCEL", true, "cancel dispatched to venue"));
      } else if (result instanceof RouteEventResult.Rejected rejected) {
        outcomes.add(
            new TargetOutcome(
                rejected.routeId(),
                "ROUTE_CANCEL",
                false,
                rejected.rejectCode() + ": " + rejected.message()));
      }
    }
    CancelResult cancel = som.cancel(order.orderId(), bySessionId);
    if (cancel instanceof CancelResult.Rejected rejected) {
      outcomes.add(
          new TargetOutcome(
              order.orderId(),
              "ORDER_CANCEL",
              false,
              rejected.rejectCode() + ": " + rejected.message()));
    } else {
      outcomes.add(new TargetOutcome(order.orderId(), "ORDER_CANCEL", true, "canceled"));
    }
  }

  private void cancelVenueRoutes(String venueMic, List<TargetOutcome> outcomes) {
    for (Route route : routes.activeRoutes()) {
      if (!venueMic.equals(route.fsmContext().venueMic())) {
        continue;
      }
      RouteEventResult result = routes.cancelRoute(route.routeId());
      if (result instanceof RouteEventResult.Rejected rejected) {
        outcomes.add(
            new TargetOutcome(
                route.routeId(),
                "ROUTE_CANCEL",
                false,
                rejected.rejectCode() + ": " + rejected.message()));
      } else {
        outcomes.add(
            new TargetOutcome(route.routeId(), "ROUTE_CANCEL", true, "cancel dispatched to venue"));
      }
    }
  }

  // ── Audit + publish ──────────────────────────────────────────────────────────

  private Optional<Session> authorize(long sessionId) {
    return aaa.sessionInfo(sessionId)
        .filter(s -> s.identity().effectiveTags().contains(REQUIRED_TAG));
  }

  private KillResult audited(
      String action, Scope scope, Session by, String reason, List<TargetOutcome> outcomes) {
    KillAudit audit =
        new KillAudit(
            action,
            scope,
            by.identity().userId() + "@" + by.identity().firmId(),
            reason,
            nowMillis.getAsLong(),
            List.copyOf(outcomes),
            (int) outcomes.stream().filter(o -> !o.ok()).count());
    journal.add(audit);
    publish(audit);
    return new KillResult.Done(audit);
  }

  private void publish(KillAudit audit) {
    ObjectNode row = mapper.createObjectNode();
    row.put("action", audit.action());
    row.put("scopeKind", audit.scope().kind().name());
    row.put("scopeValue", audit.scope().value());
    row.put("by", audit.by());
    row.put("reason", audit.reason());
    row.put("ts", audit.atMillis());
    row.put("targets", audit.outcomes().size());
    row.put("failures", audit.failures());
    ArrayNode failures = row.putArray("failedTargets");
    for (TargetOutcome outcome : audit.outcomes()) {
      if (!outcome.ok()) {
        failures.add(outcome.targetId() + " " + outcome.action() + ": " + outcome.detail());
      }
    }
    subscriptions.publish(
        TOPIC_KILL,
        "Kill"
            + audit.action().charAt(0)
            + audit.action().substring(1).toLowerCase(java.util.Locale.ROOT),
        audit.scope().toString(),
        row.toString());
  }
}
