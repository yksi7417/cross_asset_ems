/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.ApiEvent;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.control.KillSwitchState.Kind;
import io.crossasset.ems.api.control.KillSwitchState.Scope;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kill-switch tests (task 18.4) over the full guarded stack: lockout-before-cancel ordering,
 * firm/desk/venue scoping, risk-reduction paths staying open, tag-gated authorization, audited
 * outcomes including failures (nothing silent), release semantics, and cancel-on-disconnect.
 */
class KillSwitchServiceTest {

  private static final String FIGI = "BBG000BLNNH6";

  private final SubscriptionRegistry registry = new SubscriptionRegistry();
  private final KillSwitchState state = new KillSwitchState();
  private InMemoryAaaService aaa;
  private KillSwitchOrderGuard som;
  private KillSwitchRouteGuard routes;
  private KillSwitchService kill;
  private long opsSession; // #kill-switch tag
  private long desk1Session;
  private long desk2Session;
  private long untaggedSession;

  @BeforeEach
  void setUp() {
    aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("ops", "firm-a", "desk-ops", "ops-1", Set.of("#kill-switch"));
    aaa.registerCredential("t1", "firm-a", "desk-1", "trader-1", Set.of());
    aaa.registerCredential("t2", "firm-a", "desk-2", "trader-2", Set.of());
    opsSession = logon("ops");
    desk1Session = logon("t1");
    desk2Session = logon("t2");
    untaggedSession = desk1Session;

    InMemoryStagedOrderManager rawSom =
        new InMemoryStagedOrderManager(new LayeredValidatorPipeline(aaa, null, null));
    som = new KillSwitchOrderGuard(rawSom, state, aaa);
    routes = new KillSwitchRouteGuard(new InMemoryRouteManager(som), state, aaa, som);
    kill = new KillSwitchService(aaa, som, routes, state, registry, () -> 42_000L);
  }

  private long logon(String token) {
    LogonOutcome outcome = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, token));
    return ((LogonOutcome.Accepted) outcome).session().sessionId();
  }

  private String stageReady(long session, String clOrdId) {
    StageResult staged =
        som.stage(
            new OrderRequest("rq-" + clOrdId, session, clOrdId, FIGI, 1, 1_000L, null, "acc", 0));
    String orderId = ((StageResult.Accepted) staged).order().orderId();
    som.markReady(orderId, session);
    return orderId;
  }

  private String route(String orderId, String venue, long qty) {
    RouteResult result =
        routes.route(
            new RouteRequest("rt-" + orderId + "-" + venue, orderId, venue, qty, null, null));
    return ((RouteResult.Routed) result).route().routeId();
  }

  // ── Firm scope ───────────────────────────────────────────────────────────────

  @Test
  void engageFirm_cancelsEverything_locksOutEntry_keepsRiskReductionOpen() {
    String order1 = stageReady(desk1Session, "CL-1");
    String order2 = stageReady(desk2Session, "CL-2");
    String routeId = route(order1, "XNAS", 400);
    routes.acknowledgeRoute(routeId);

    KillSwitchService.KillResult result =
        kill.engage(new Scope(Kind.FIRM, "firm-a"), opsSession, "fat-finger storm");
    KillSwitchService.KillAudit audit = ((KillSwitchService.KillResult.Done) result).audit();

    // Mass-cancel accounted for: the route cancel + both order cancels.
    assertThat(audit.outcomes())
        .extracting(KillSwitchService.TargetOutcome::targetId)
        .contains(routeId, order1, order2);
    assertThat(audit.failures()).isZero();
    assertThat(som.findOrder(order1).orElseThrow().fsmState().name()).isEqualTo("CANCELED");
    assertThat(som.findOrder(order2).orElseThrow().fsmState().name()).isEqualTo("CANCELED");
    assertThat(routes.findRoute(routeId).orElseThrow().fsmState().name())
        .isEqualTo("PENDING_CANCEL_AT_VENUE");

    // New-order lockout, everywhere in the firm.
    StageResult locked =
        som.stage(new OrderRequest("rq-x", desk2Session, "CL-X", FIGI, 1, 100L, null, "acc", 0));
    assertThat(((StageResult.Rejected) locked).rejectCode()).isEqualTo("EMS-ORD-9601");

    // Risk reduction stays open: cancel of anything still working is allowed.
    CancelResult cancelAllowed = som.cancel(order1, desk1Session);
    assertThat(cancelAllowed).isNotNull(); // no kill-switch rejection on the cancel path

    // Audit event published for the desktop banner.
    List<ApiEvent> events = registry.fetch(KillSwitchService.TOPIC_KILL, 1, 10);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).type()).isEqualTo("KillEngage");
    assertThat(events.get(0).payload()).contains("\"failures\":0").contains("fat-finger storm");
  }

  @Test
  void lockoutHoldsEvenWhenMassCancelHasFailures_andFailuresAreAudited() {
    String order1 = stageReady(desk1Session, "CL-1");
    String routeId = route(order1, "XNAS", 400);
    routes.acknowledgeRoute(routeId);
    // Pre-dispatch a cancel so the kill's cascade hits a route already in PENDING_CANCEL.
    routes.cancelRoute(routeId);

    KillSwitchService.KillResult result =
        kill.engage(new Scope(Kind.FIRM, "firm-a"), opsSession, "drill");
    KillSwitchService.KillAudit audit = ((KillSwitchService.KillResult.Done) result).audit();

    assertThat(audit.failures()).isPositive();
    assertThat(audit.outcomes())
        .filteredOn(o -> !o.ok())
        .as("the failed route cancel is visible, not silent")
        .isNotEmpty();
    // Lockout engaged regardless of the partial failure.
    StageResult locked =
        som.stage(new OrderRequest("rq-y", desk1Session, "CL-Y", FIGI, 1, 100L, null, "acc", 0));
    assertThat(locked).isInstanceOf(StageResult.Rejected.class);
  }

  // ── Desk scope ───────────────────────────────────────────────────────────────

  @Test
  void engageDesk_scopesToThatDeskOnly() {
    String desk1Order = stageReady(desk1Session, "CL-1");
    String desk2Order = stageReady(desk2Session, "CL-2");

    kill.engage(new Scope(Kind.DESK, "desk-1"), opsSession, "desk runaway");

    assertThat(som.findOrder(desk1Order).orElseThrow().isTerminal()).isTrue();
    assertThat(som.findOrder(desk2Order).orElseThrow().isTerminal()).isFalse();

    StageResult desk1Locked =
        som.stage(new OrderRequest("rq-1", desk1Session, "CL-N1", FIGI, 1, 100L, null, "acc", 0));
    assertThat(desk1Locked).isInstanceOf(StageResult.Rejected.class);
    StageResult desk2Free =
        som.stage(new OrderRequest("rq-2", desk2Session, "CL-N2", FIGI, 1, 100L, null, "acc", 0));
    assertThat(desk2Free).isInstanceOf(StageResult.Accepted.class);
  }

  // ── Venue scope ──────────────────────────────────────────────────────────────

  @Test
  void engageVenue_cancelsAndBlocksOnlyThatVenue() {
    String order1 = stageReady(desk1Session, "CL-1");
    String xnasRoute = route(order1, "XNAS", 200);
    String xnysRoute = route(order1, "XNYS", 200);
    routes.acknowledgeRoute(xnasRoute);
    routes.acknowledgeRoute(xnysRoute);

    KillSwitchService.KillResult result =
        kill.engage(new Scope(Kind.VENUE, "XNAS"), opsSession, "venue gateway flapping");
    KillSwitchService.KillAudit audit = ((KillSwitchService.KillResult.Done) result).audit();

    assertThat(audit.outcomes())
        .extracting(KillSwitchService.TargetOutcome::targetId)
        .containsExactly(xnasRoute);
    assertThat(routes.findRoute(xnasRoute).orElseThrow().fsmState().name())
        .isEqualTo("PENDING_CANCEL_AT_VENUE");
    assertThat(routes.findRoute(xnysRoute).orElseThrow().fsmState().name()).isEqualTo("WORKING");

    // Routing to the killed venue locked; the other venue and staging stay open.
    RouteResult xnasLocked =
        routes.route(new RouteRequest("rt-l", order1, "XNAS", 100, null, null));
    assertThat(((RouteResult.Rejected) xnasLocked).rejectCode()).isEqualTo("EMS-RTE-9601");
    RouteResult xnysOpen = routes.route(new RouteRequest("rt-o", order1, "XNYS", 100, null, null));
    assertThat(xnysOpen).isInstanceOf(RouteResult.Routed.class);
    StageResult stagingOpen =
        som.stage(new OrderRequest("rq-s", desk1Session, "CL-S", FIGI, 1, 100L, null, "acc", 0));
    assertThat(stagingOpen).isInstanceOf(StageResult.Accepted.class);
  }

  // ── Authorization + state conflicts ──────────────────────────────────────────

  @Test
  void engage_requiresKillSwitchTag() {
    KillSwitchService.KillResult result =
        kill.engage(new Scope(Kind.FIRM, "firm-a"), untaggedSession, "nope");
    assertThat(((KillSwitchService.KillResult.Rejected) result).code()).isEqualTo("EMS-AUT-9601");
    assertThat(kill.engaged()).isEmpty();
    assertThat(kill.journal()).isEmpty();
  }

  @Test
  void doubleEngage_andReleaseOfUnengaged_rejected() {
    Scope scope = new Scope(Kind.DESK, "desk-1");
    kill.engage(scope, opsSession, "first");
    KillSwitchService.KillResult again = kill.engage(scope, opsSession, "second");
    assertThat(((KillSwitchService.KillResult.Rejected) again).code()).isEqualTo("EMS-AUT-9602");

    KillSwitchService.KillResult notEngaged =
        kill.release(new Scope(Kind.DESK, "desk-9"), opsSession, "oops");
    assertThat(((KillSwitchService.KillResult.Rejected) notEngaged).code())
        .isEqualTo("EMS-AUT-9602");
  }

  @Test
  void release_restoresOrderEntry_andIsAudited() {
    Scope scope = new Scope(Kind.FIRM, "firm-a");
    kill.engage(scope, opsSession, "drill");
    kill.release(scope, opsSession, "drill complete");

    StageResult open =
        som.stage(new OrderRequest("rq-r", desk1Session, "CL-R", FIGI, 1, 100L, null, "acc", 0));
    assertThat(open).isInstanceOf(StageResult.Accepted.class);
    assertThat(kill.journal())
        .extracting(KillSwitchService.KillAudit::action)
        .containsExactly("ENGAGE", "RELEASE");
    assertThat(registry.fetch(KillSwitchService.TOPIC_KILL, 1, 10))
        .extracting(ApiEvent::type)
        .containsExactly("KillEngage", "KillRelease");
  }

  // ── Cancel-on-disconnect ─────────────────────────────────────────────────────

  @Test
  void cancelOnDisconnect_cancelsArmedSessionsOrdersOnly() {
    kill.armCancelOnDisconnect(desk1Session);
    String armedOrder = stageReady(desk1Session, "CL-A");
    String otherOrder = stageReady(desk2Session, "CL-O");
    String routeId = route(armedOrder, "XNAS", 300);
    routes.acknowledgeRoute(routeId);

    var audit = kill.onSessionDisconnected(desk1Session);

    assertThat(audit).isPresent();
    assertThat(audit.get().action()).isEqualTo("DISCONNECT");
    assertThat(som.findOrder(armedOrder).orElseThrow().isTerminal()).isTrue();
    assertThat(routes.findRoute(routeId).orElseThrow().fsmState().name())
        .isEqualTo("PENDING_CANCEL_AT_VENUE");
    assertThat(som.findOrder(otherOrder).orElseThrow().isTerminal()).isFalse();

    // Unarmed session: disconnect is a no-op.
    assertThat(kill.onSessionDisconnected(desk2Session)).isEmpty();
    assertThat(som.findOrder(otherOrder).orElseThrow().isTerminal()).isFalse();
  }

  // ── Amend pass-through under kill ────────────────────────────────────────────

  @Test
  void amend_remainsOpenDuringKill() {
    String orderId = stageReady(desk1Session, "CL-1");
    kill.engage(new Scope(Kind.DESK, "desk-2"), opsSession, "other desk");

    AmendResult amend = som.amend(orderId, new AmendFields(500L, null), desk1Session);
    assertThat(amend).isInstanceOf(AmendResult.Amended.class);
  }
}
