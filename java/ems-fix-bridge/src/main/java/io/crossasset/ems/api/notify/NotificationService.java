/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.notify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Notification service (task 18.8, arch-notification-service.md): one queue + router for alerts to
 * humans — fills/rejects/limit-breaches to the desktop, email/SMS/webhook via pluggable {@link
 * NotificationSink}s. Versioned {@link Rule}s resolve source/kind/severity to audience + channels;
 * acknowledgement is tracked; unacked alerts escalate per policy; identical alerts within the
 * throttle window collapse with a count instead of spamming.
 *
 * <p>Every step is an audited event ({@code DISPATCHED}, {@code DELIVERED}, {@code
 * DELIVERY_FAILED}, {@code ACKED}, {@code ESCALATED}) — the journal answers "did the right person
 * get it, and when did they ack" without reconstructing from logs. Deterministic: time is passed
 * in; IDs are sequences.
 */
public final class NotificationService {

  public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
  }

  public enum Kind {
    INFO,
    ALERT,
    ESCALATION,
    RESOLUTION
  }

  public enum Channel {
    DESKTOP,
    EMAIL,
    SMS,
    WEBHOOK
  }

  /** A channel adapter. Implementations must not throw — return false on failure. */
  public interface NotificationSink {
    Channel channel();

    boolean deliver(Notification notification, String audience);
  }

  /** The routed notification (arch § Notification envelope, v1 fields). */
  public record Notification(
      String notificationId,
      Kind kind,
      String source,
      Severity severity,
      String subject,
      String body,
      List<String> refIds,
      String audience,
      Set<Channel> channels,
      boolean ackRequired,
      long ackDeadlineMillis,
      long createdAtMillis,
      int collapsedCount) {}

  /** One escalation step: unacked after {@code afterMillis} → re-dispatch. */
  public record EscalationStep(long afterMillis, String toAudience, Set<Channel> channels) {}

  /** Routing rule: match source/kind/min-severity (+ optional subject) → audience + channels. */
  public record Rule(
      String ruleId,
      @Nullable String matchSource,
      @Nullable Kind matchKind,
      Severity minSeverity,
      @Nullable String matchSubject,
      String audience,
      Set<Channel> channels,
      boolean ackRequired,
      long ackDeadlineAfterMillis,
      long throttleWindowMillis,
      List<EscalationStep> escalation) {

    boolean matches(String source, Kind kind, Severity severity, String subject) {
      return (matchSource == null || matchSource.equals(source))
          && (matchKind == null || matchKind == kind)
          && severity.ordinal() >= minSeverity.ordinal()
          && (matchSubject == null || matchSubject.equals(subject));
    }
  }

  /** One audit line. */
  public record AuditEvent(String notificationId, String event, String detail, long atMillis) {}

  private record ThrottleKey(String ruleId, String source, String subject) {}

  private final List<Rule> rules = new ArrayList<>();
  private final Map<Channel, NotificationSink> sinks = new LinkedHashMap<>();
  private final Map<String, Notification> notifications = new LinkedHashMap<>();
  private final Map<String, Integer> escalationStepDone = new LinkedHashMap<>();
  private final Map<String, Long> ackedAtById = new LinkedHashMap<>();
  private final Map<ThrottleKey, String> throttleHeads = new LinkedHashMap<>();
  private final List<AuditEvent> audit = new ArrayList<>();
  private final AtomicLong idSeq = new AtomicLong(1);

  public synchronized void registerRule(Rule rule) {
    rules.add(Objects.requireNonNull(rule, "rule"));
  }

  public synchronized void registerSink(NotificationSink sink) {
    sinks.put(sink.channel(), sink);
  }

  /**
   * Publish one alert into the queue: every matching rule routes a notification to its audience
   * over its channels (or collapses into the rule's open throttle head). Returns the notifications
   * dispatched (empty when no rule matched — visible to the caller, not dropped silently).
   */
  public synchronized List<Notification> publish(
      String source,
      Kind kind,
      Severity severity,
      String subject,
      String body,
      List<String> refIds,
      long nowMillis) {
    List<Notification> dispatched = new ArrayList<>();
    for (Rule rule : rules) {
      if (!rule.matches(source, kind, severity, subject)) {
        continue;
      }
      ThrottleKey key = new ThrottleKey(rule.ruleId(), source, subject);
      if (rule.throttleWindowMillis() > 0) {
        String headId = throttleHeads.get(key);
        Notification head = headId == null ? null : notifications.get(headId);
        if (head != null
            && nowMillis - head.createdAtMillis() < rule.throttleWindowMillis()
            && ackedAtById.get(headId) == null) {
          Notification collapsed = withCount(head, head.collapsedCount() + 1);
          notifications.put(headId, collapsed);
          audit.add(
              new AuditEvent(
                  headId, "COLLAPSED", "count=" + collapsed.collapsedCount(), nowMillis));
          continue;
        }
      }
      Notification notification =
          new Notification(
              "NTF-" + idSeq.getAndIncrement(),
              kind,
              source,
              severity,
              subject,
              body,
              List.copyOf(refIds),
              rule.audience(),
              rule.channels(),
              rule.ackRequired(),
              rule.ackRequired() ? nowMillis + rule.ackDeadlineAfterMillis() : 0,
              nowMillis,
              1);
      notifications.put(notification.notificationId(), notification);
      throttleHeads.put(key, notification.notificationId());
      audit.add(
          new AuditEvent(
              notification.notificationId(),
              "DISPATCHED",
              rule.ruleId() + " -> " + rule.audience(),
              nowMillis));
      deliver(notification, rule.audience(), rule.channels(), nowMillis);
      dispatched.add(notification);
    }
    return List.copyOf(dispatched);
  }

  /** Acknowledge. Returns false for unknown / not-ack-required / already-acked. */
  public synchronized boolean ack(String notificationId, String by, long nowMillis) {
    Notification notification = notifications.get(notificationId);
    if (notification == null
        || !notification.ackRequired()
        || ackedAtById.containsKey(notificationId)) {
      return false;
    }
    ackedAtById.put(notificationId, nowMillis);
    audit.add(new AuditEvent(notificationId, "ACKED", "by " + by, nowMillis));
    return true;
  }

  /**
   * Escalate unacked notifications past their deadline: each due step re-dispatches to the step's
   * audience over the step's channels (typical 3-tier chains run one step per sweep). Returns how
   * many escalations fired.
   */
  public synchronized int sweepEscalations(long nowMillis) {
    int fired = 0;
    for (Notification notification : List.copyOf(notifications.values())) {
      if (!notification.ackRequired() || ackedAtById.containsKey(notification.notificationId())) {
        continue;
      }
      Rule rule = ruleOf(notification);
      if (rule == null || rule.escalation().isEmpty()) {
        continue;
      }
      int done = escalationStepDone.getOrDefault(notification.notificationId(), 0);
      if (done >= rule.escalation().size()) {
        continue;
      }
      EscalationStep step = rule.escalation().get(done);
      if (nowMillis < notification.ackDeadlineMillis() + stepOffset(rule, done)) {
        continue;
      }
      escalationStepDone.put(notification.notificationId(), done + 1);
      audit.add(
          new AuditEvent(
              notification.notificationId(),
              "ESCALATED",
              "step " + (done + 1) + " -> " + step.toAudience(),
              nowMillis));
      deliver(notification, step.toAudience(), step.channels(), nowMillis);
      fired++;
    }
    return fired;
  }

  /** Notifications awaiting acknowledgement (the officer queue). */
  public synchronized List<Notification> pendingAcks() {
    List<Notification> pending = new ArrayList<>();
    for (Notification notification : notifications.values()) {
      if (notification.ackRequired() && !ackedAtById.containsKey(notification.notificationId())) {
        pending.add(notification);
      }
    }
    return pending;
  }

  public synchronized Optional<Notification> find(String notificationId) {
    return Optional.ofNullable(notifications.get(notificationId));
  }

  public synchronized List<AuditEvent> auditJournal() {
    return List.copyOf(audit);
  }

  // ── Internals ────────────────────────────────────────────────────────────────

  private void deliver(
      Notification notification, String audience, Set<Channel> channels, long nowMillis) {
    for (Channel channel : channels) {
      NotificationSink sink = sinks.get(channel);
      boolean ok = sink != null && sink.deliver(notification, audience);
      audit.add(
          new AuditEvent(
              notification.notificationId(),
              ok ? "DELIVERED" : "DELIVERY_FAILED",
              channel + " -> " + audience + (sink == null ? " (no sink registered)" : ""),
              nowMillis));
    }
  }

  private @Nullable Rule ruleOf(Notification notification) {
    for (Rule rule : rules) {
      if (rule.audience().equals(notification.audience())
          && rule.matches(
              notification.source(),
              notification.kind(),
              notification.severity(),
              notification.subject())) {
        return rule;
      }
    }
    return null;
  }

  /** Cumulative wait before step N beyond the ack deadline (each step's own delay included). */
  private static long stepOffset(Rule rule, int stepIndex) {
    long offset = 0;
    for (int i = 0; i <= stepIndex; i++) {
      offset += rule.escalation().get(i).afterMillis();
    }
    return offset;
  }

  private static Notification withCount(Notification n, int count) {
    return new Notification(
        n.notificationId(),
        n.kind(),
        n.source(),
        n.severity(),
        n.subject(),
        n.body(),
        n.refIds(),
        n.audience(),
        n.channels(),
        n.ackRequired(),
        n.ackDeadlineMillis(),
        n.createdAtMillis(),
        count);
  }
}
