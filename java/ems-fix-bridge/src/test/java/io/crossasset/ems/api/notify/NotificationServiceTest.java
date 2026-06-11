/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.notify.NotificationService.Channel;
import io.crossasset.ems.api.notify.NotificationService.EscalationStep;
import io.crossasset.ems.api.notify.NotificationService.Kind;
import io.crossasset.ems.api.notify.NotificationService.Notification;
import io.crossasset.ems.api.notify.NotificationService.Rule;
import io.crossasset.ems.api.notify.NotificationService.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Notification tests (task 18.8): rule routing + multi-channel fan-out with full audit, failed
 * deliveries visible, ack tracking, deadline-driven multi-step escalation, throttle collapsing, and
 * the desktop sink's resumable topic rows.
 */
class NotificationServiceTest {

  /** Recording sink; can be told to fail. */
  private static final class RecordingSink implements NotificationService.NotificationSink {
    final Channel channel;
    final List<String> deliveries = new ArrayList<>();
    boolean failing;

    RecordingSink(Channel channel) {
      this.channel = channel;
    }

    @Override
    public Channel channel() {
      return channel;
    }

    @Override
    public boolean deliver(Notification notification, String audience) {
      if (failing) {
        return false;
      }
      deliveries.add(audience + ":" + notification.subject() + ":" + notification.severity());
      return true;
    }
  }

  private final NotificationService service = new NotificationService();
  private final RecordingSink desktop = new RecordingSink(Channel.DESKTOP);
  private final RecordingSink email = new RecordingSink(Channel.EMAIL);
  private final RecordingSink sms = new RecordingSink(Channel.SMS);

  private Rule fillsRule() {
    return new Rule(
        "fills-to-desk",
        "blotter",
        Kind.INFO,
        Severity.INFO,
        "fill",
        "desk-1",
        Set.of(Channel.DESKTOP),
        false,
        0,
        60_000L,
        List.of());
  }

  private Rule rejectRule() {
    return new Rule(
        "rejects-ack",
        "blotter",
        Kind.ALERT,
        Severity.HIGH,
        null,
        "desk-supervisor",
        Set.of(Channel.DESKTOP, Channel.EMAIL),
        true,
        15 * 60_000L,
        0,
        List.of(
            new EscalationStep(0, "head-of-desk", Set.of(Channel.SMS)),
            new EscalationStep(30 * 60_000L, "coo", Set.of(Channel.SMS))));
  }

  @Test
  void ruleRouting_fansOutToChannels_withFullAudit() {
    service.registerSink(desktop);
    service.registerSink(email);
    service.registerRule(rejectRule());

    List<Notification> dispatched =
        service.publish(
            "blotter",
            Kind.ALERT,
            Severity.HIGH,
            "order-rejected",
            "EMS-ORD-1 rejected",
            List.of("EMS-ORD-1"),
            1_000L);

    assertThat(dispatched).hasSize(1);
    assertThat(desktop.deliveries).containsExactly("desk-supervisor:order-rejected:HIGH");
    assertThat(email.deliveries).hasSize(1);
    assertThat(service.auditJournal())
        .extracting(NotificationService.AuditEvent::event)
        .containsExactly("DISPATCHED", "DELIVERED", "DELIVERED");
  }

  @Test
  void noMatchingRule_returnsEmpty_lowSeverityFiltered() {
    service.registerSink(desktop);
    service.registerRule(rejectRule()); // min HIGH

    assertThat(
            service.publish(
                "blotter", Kind.ALERT, Severity.LOW, "order-rejected", "minor", List.of(), 0L))
        .isEmpty();
    assertThat(desktop.deliveries).isEmpty();
  }

  @Test
  void failedDelivery_auditedNotSilent() {
    email.failing = true;
    service.registerSink(desktop);
    service.registerSink(email);
    service.registerRule(rejectRule());

    service.publish("blotter", Kind.ALERT, Severity.HIGH, "x", "b", List.of(), 0L);

    assertThat(service.auditJournal())
        .extracting(NotificationService.AuditEvent::event)
        .contains("DELIVERY_FAILED")
        .contains("DELIVERED");
  }

  @Test
  void missingSink_auditedAsFailure() {
    service.registerSink(desktop); // no EMAIL sink registered
    service.registerRule(rejectRule());

    service.publish("blotter", Kind.ALERT, Severity.HIGH, "x", "b", List.of(), 0L);

    assertThat(service.auditJournal())
        .filteredOn(e -> e.event().equals("DELIVERY_FAILED"))
        .anyMatch(e -> e.detail().contains("no sink registered"));
  }

  @Test
  void ack_tracksAndStopsEscalation() {
    service.registerSink(desktop);
    service.registerSink(email);
    service.registerSink(sms);
    service.registerRule(rejectRule());
    Notification n =
        service.publish("blotter", Kind.ALERT, Severity.HIGH, "x", "b", List.of(), 0L).get(0);

    assertThat(service.pendingAcks()).hasSize(1);
    assertThat(service.ack(n.notificationId(), "supervisor-1", 5_000L)).isTrue();
    assertThat(service.ack(n.notificationId(), "supervisor-1", 6_000L)).as("double ack").isFalse();
    assertThat(service.pendingAcks()).isEmpty();

    // Past the deadline, but acked — no escalation.
    assertThat(service.sweepEscalations(99_999_999L)).isZero();
    assertThat(sms.deliveries).isEmpty();
  }

  @Test
  void escalation_firesStepsInOrderAfterDeadline() {
    service.registerSink(desktop);
    service.registerSink(email);
    service.registerSink(sms);
    service.registerRule(rejectRule());
    service.publish("blotter", Kind.ALERT, Severity.HIGH, "x", "b", List.of(), 0L);
    long deadline = 15 * 60_000L;

    assertThat(service.sweepEscalations(deadline - 1)).isZero();
    assertThat(service.sweepEscalations(deadline)).isEqualTo(1); // step 1 -> head-of-desk
    assertThat(sms.deliveries).containsExactly("head-of-desk:x:HIGH");

    // Step 2 waits its additional 30m beyond the deadline.
    assertThat(service.sweepEscalations(deadline + 1)).isZero();
    assertThat(service.sweepEscalations(deadline + 30 * 60_000L)).isEqualTo(1);
    assertThat(sms.deliveries).containsExactly("head-of-desk:x:HIGH", "coo:x:HIGH");

    // No third step configured: nothing more ever fires.
    assertThat(service.sweepEscalations(99_999_999L)).isZero();
  }

  @Test
  void throttle_collapsesIdenticalAlertsWithinWindow() {
    service.registerSink(desktop);
    service.registerRule(fillsRule());

    Notification first =
        service
            .publish("blotter", Kind.INFO, Severity.INFO, "fill", "fill 1", List.of(), 0L)
            .get(0);
    assertThat(
            service.publish(
                "blotter", Kind.INFO, Severity.INFO, "fill", "fill 2", List.of(), 10_000L))
        .as("collapsed into the open head")
        .isEmpty();
    assertThat(service.find(first.notificationId()).orElseThrow().collapsedCount()).isEqualTo(2);
    assertThat(desktop.deliveries).hasSize(1);

    // Outside the window: a fresh notification.
    assertThat(
            service.publish(
                "blotter", Kind.INFO, Severity.INFO, "fill", "fill 3", List.of(), 61_000L))
        .hasSize(1);
    assertThat(desktop.deliveries).hasSize(2);
  }

  @Test
  void desktopSink_publishesResumableRowOnAudienceTopic() throws Exception {
    SubscriptionRegistry registry = new SubscriptionRegistry();
    NotificationService svc = new NotificationService();
    svc.registerSink(new DesktopSink(registry));
    svc.registerRule(fillsRule());

    svc.publish("blotter", Kind.INFO, Severity.INFO, "fill", "filled 100 IBM", List.of("E1"), 7L);

    var events = registry.fetch(DesktopSink.TOPIC_PREFIX + "desk-1", 1, 10);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).type()).isEqualTo("NotificationRow");
    var row = new ObjectMapper().readTree(events.get(0).payload());
    assertThat(row.get("subject").asText()).isEqualTo("fill");
    assertThat(row.get("severity").asText()).isEqualTo("INFO");
    assertThat(row.get("ts").asLong()).isEqualTo(7L);
  }
}
