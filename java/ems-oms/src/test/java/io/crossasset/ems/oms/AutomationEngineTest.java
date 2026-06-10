/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutomationEngineTest {

  private InMemoryAutomationEngine engine;
  private AutomationContext ctx;

  @BeforeEach
  void setUp() {
    engine = new InMemoryAutomationEngine();
    StagedOrder order = mock(StagedOrder.class);
    ctx = new AutomationContext(order);
  }

  // ── bind / unbind / list ──────────────────────────────────────────────────

  @Test
  void listRules_empty_initially() {
    assertThat(engine.listRules()).isEmpty();
  }

  @Test
  void bindRule_addsToList() {
    engine.bindRule(rule("r1", "OrderAccepted", 10));
    assertThat(engine.listRules()).hasSize(1);
    assertThat(engine.listRules().get(0).ruleId()).isEqualTo("r1");
  }

  @Test
  void bindRule_replacesExistingById() {
    engine.bindRule(rule("r1", "OrderAccepted", 10));
    engine.bindRule(rule("r1", "OrderFilled", 20));
    assertThat(engine.listRules()).hasSize(1);
    assertThat(engine.listRules().get(0).triggerEvent()).isEqualTo("OrderFilled");
  }

  @Test
  void unbindRule_removesRule() {
    engine.bindRule(rule("r1", "OrderAccepted", 10));
    engine.unbindRule("r1");
    assertThat(engine.listRules()).isEmpty();
  }

  @Test
  void unbindRule_unknownId_isNoOp() {
    engine.unbindRule("nonexistent");
    assertThat(engine.listRules()).isEmpty();
  }

  @Test
  void listRules_sortedByDescendingPriority() {
    engine.bindRule(rule("low", "OrderAccepted", 5));
    engine.bindRule(rule("high", "OrderAccepted", 100));
    engine.bindRule(rule("mid", "OrderAccepted", 50));

    List<String> ids = engine.listRules().stream().map(AutomationRule::ruleId).toList();
    assertThat(ids).containsExactly("high", "mid", "low");
  }

  // ── evaluate ─────────────────────────────────────────────────────────────

  @Test
  void evaluate_noRules_returnsEmpty() {
    var event = new AutomationEvent("OrderAccepted", "ORD-1");
    assertThat(engine.evaluate(event, ctx)).isEmpty();
  }

  @Test
  void evaluate_matchingRule_firesFired() {
    engine.bindRule(rule("r1", "OrderAccepted", 10));
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    List<RuleFiringDecision> decisions = engine.evaluate(event, ctx);

    assertThat(decisions).hasSize(1);
    assertThat(decisions.get(0)).isInstanceOf(RuleFiringDecision.Fired.class);
    assertThat(decisions.get(0).ruleId()).isEqualTo("r1");
  }

  @Test
  void evaluate_wrongEvent_skips() {
    engine.bindRule(rule("r1", "OrderFilled", 10));
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    List<RuleFiringDecision> decisions = engine.evaluate(event, ctx);

    assertThat(decisions).isEmpty();
  }

  @Test
  void evaluate_disabledRule_skipped() {
    engine.bindRule(disabledRule("r1", "OrderAccepted", 10));
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    List<RuleFiringDecision> decisions = engine.evaluate(event, ctx);

    assertThat(decisions).hasSize(1);
    assertThat(decisions.get(0)).isInstanceOf(RuleFiringDecision.Skipped.class);
    assertThat(((RuleFiringDecision.Skipped) decisions.get(0)).reason()).isEqualTo("disabled");
  }

  @Test
  void evaluate_conditionFalse_skipped() {
    AutomationRule conditionFalse =
        new AutomationRule(
            "r1",
            AutomationScope.FIRM,
            null,
            "OrderAccepted",
            c -> false,
            List.of(new AutomationAction.MarkOrderReady()),
            10,
            true);
    engine.bindRule(conditionFalse);
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    List<RuleFiringDecision> decisions = engine.evaluate(event, ctx);

    assertThat(decisions).hasSize(1);
    assertThat(decisions.get(0)).isInstanceOf(RuleFiringDecision.Skipped.class);
    assertThat(((RuleFiringDecision.Skipped) decisions.get(0)).reason())
        .isEqualTo("condition-false");
  }

  @Test
  void evaluate_priorityOrderRespected() {
    engine.bindRule(rule("low", "OrderAccepted", 1));
    engine.bindRule(rule("high", "OrderAccepted", 99));
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    List<String> ruleIds =
        engine.evaluate(event, ctx).stream().map(RuleFiringDecision::ruleId).toList();

    assertThat(ruleIds).containsExactly("high", "low");
  }

  @Test
  void evaluate_mixedFiredAndSkipped() {
    engine.bindRule(rule("enabled", "OrderAccepted", 20));
    engine.bindRule(disabledRule("disabled", "OrderAccepted", 10));
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    List<RuleFiringDecision> decisions = engine.evaluate(event, ctx);

    assertThat(decisions).hasSize(2);
    assertThat(decisions.get(0)).isInstanceOf(RuleFiringDecision.Fired.class);
    assertThat(decisions.get(1)).isInstanceOf(RuleFiringDecision.Skipped.class);
  }

  @Test
  void evaluate_firedDecisionCarriesActions() {
    var action = new AutomationAction.RouteOrders("XNAS", 10050L);
    AutomationRule r =
        new AutomationRule(
            "r1", AutomationScope.FIRM, null, "OrderAccepted", List.of(action), 10, true);
    engine.bindRule(r);
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    var decision = (RuleFiringDecision.Fired) engine.evaluate(event, ctx).get(0);

    assertThat(decision.actions()).containsExactly(action);
    assertThat(((AutomationAction.RouteOrders) decision.actions().get(0)).venueMic())
        .isEqualTo("XNAS");
    assertThat(((AutomationAction.RouteOrders) decision.actions().get(0)).price())
        .isEqualTo(10050L);
  }

  @Test
  void evaluate_conditionReceivesContext() {
    StagedOrder sentinel = mock(StagedOrder.class);
    AutomationContext specificCtx = new AutomationContext(sentinel);

    AutomationRule r =
        new AutomationRule(
            "r1",
            AutomationScope.FIRM,
            null,
            "OrderAccepted",
            c -> c.order() == sentinel,
            List.of(new AutomationAction.MarkOrderReady()),
            10,
            true);
    engine.bindRule(r);
    var event = new AutomationEvent("OrderAccepted", "ORD-1");

    assertThat(engine.evaluate(event, specificCtx).get(0))
        .isInstanceOf(RuleFiringDecision.Fired.class);
    assertThat(engine.evaluate(event, ctx).get(0)).isInstanceOf(RuleFiringDecision.Skipped.class);
  }

  @Test
  void automationAction_routeOrders_nullPriceConvenience() {
    var action = new AutomationAction.RouteOrders("XLON");
    assertThat(action.venueMic()).isEqualTo("XLON");
    assertThat(action.price()).isNull();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static AutomationRule rule(String ruleId, String event, int priority) {
    return new AutomationRule(
        ruleId,
        AutomationScope.FIRM,
        null,
        event,
        List.of(new AutomationAction.MarkOrderReady()),
        priority,
        true);
  }

  private static AutomationRule disabledRule(String ruleId, String event, int priority) {
    return new AutomationRule(
        ruleId,
        AutomationScope.FIRM,
        null,
        event,
        List.of(new AutomationAction.MarkOrderReady()),
        priority,
        false);
  }
}
