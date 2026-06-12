/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.dropcopy;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.FixTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client drop-copy service (task 12.16): a real-time FIX drop of executions to subscribed
 * clients, scoped per client / desk / firm — a prime broker watches its client's flow, a desk
 * head watches the desk, risk watches the firm. Drop-copy sessions are READ-ONLY mirrors of
 * ExecutionReports; they never originate orders.
 *
 * <p>Each subscription owns its FIX session identity (senderCompId per subscriber) and its own
 * outbound sequence; an execution matching the scope is encoded as an ExecutionReport (35=8,
 * CopyMsgIndicator 797=Y) and delivered to the subscriber's sink. Delivery preserves execution
 * order per subscriber; payloads are deterministic for a given execution + subscription.
 */
public final class DropCopyService {

  /** The scope an execution is matched against. */
  public enum ScopeKind {
    CLIENT_ACCOUNT,
    DESK,
    FIRM
  }

  /** One drop-copy execution: the normalized projection of a fill the service mirrors. */
  public record Execution(
      String execId,
      String orderId,
      String account,
      String desk,
      String firm,
      String instrumentId,
      int side,
      long lastQty,
      long lastPx,
      long cumQty,
      long leavesQty,
      long tsMicros) {}

  /** Where a subscriber's copies go (a FIX session writer in production, a list in tests). */
  @FunctionalInterface
  public interface DropSink {
    void deliver(String subscriptionId, long seqNum, String rawFix);
  }

  private record Subscription(
      String subscriptionId,
      ScopeKind kind,
      String scopeValue,
      String targetCompId,
      DropSink sink,
      AtomicLong seq) {}

  private final String senderCompId;
  private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
  private final Map<String, long[]> deliveredCounts = new ConcurrentHashMap<>();

  /** @param senderCompId this EMS's CompID on every drop session */
  public DropCopyService(String senderCompId) {
    this.senderCompId = Objects.requireNonNull(senderCompId, "senderCompId");
  }

  /**
   * Subscribe a drop-copy session.
   *
   * @param scopeValue the account (CLIENT_ACCOUNT), desk id (DESK), or firm id (FIRM) to mirror
   * @param targetCompId the subscriber's CompID on the drop session
   */
  public String subscribe(ScopeKind kind, String scopeValue, String targetCompId, DropSink sink) {
    String subscriptionId = "DC-" + targetCompId + "-" + kind + "-" + scopeValue;
    subscriptions.add(
        new Subscription(
            subscriptionId,
            Objects.requireNonNull(kind),
            Objects.requireNonNull(scopeValue),
            Objects.requireNonNull(targetCompId),
            Objects.requireNonNull(sink),
            new AtomicLong()));
    return subscriptionId;
  }

  public void unsubscribe(String subscriptionId) {
    subscriptions.removeIf(s -> s.subscriptionId().equals(subscriptionId));
  }

  /** Mirror one execution to every subscription whose scope matches. */
  public void onExecution(Execution execution) {
    for (Subscription subscription : subscriptions) {
      if (matches(subscription, execution)) {
        long seq = subscription.seq().incrementAndGet();
        subscription.sink().deliver(
            subscription.subscriptionId(), seq, encode(execution, subscription, seq));
        deliveredCounts.computeIfAbsent(subscription.subscriptionId(), k -> new long[1])[0]++;
      }
    }
  }

  /** Copies delivered per subscription (ops introspection). */
  public long deliveredCount(String subscriptionId) {
    long[] count = deliveredCounts.get(subscriptionId);
    return count == null ? 0 : count[0];
  }

  private static boolean matches(Subscription subscription, Execution execution) {
    return switch (subscription.kind()) {
      case CLIENT_ACCOUNT -> subscription.scopeValue().equals(execution.account());
      case DESK -> subscription.scopeValue().equals(execution.desk());
      case FIRM -> subscription.scopeValue().equals(execution.firm());
    };
  }

  private String encode(Execution execution, Subscription subscription, long seqNum) {
    return FixMessage.builder()
        .field(FixTags.MSG_TYPE, "8")
        .field(FixTags.SENDER_COMP_ID, senderCompId)
        .field(FixTags.TARGET_COMP_ID, subscription.targetCompId())
        .field(FixTags.MSG_SEQ_NUM, seqNum)
        .field(FixTags.ORDER_ID, execution.orderId())
        .field(FixTags.EXEC_ID, execution.execId())
        .field(150, "F") // ExecType = Trade
        .field(39, execution.leavesQty() == 0 ? "2" : "1") // OrdStatus filled/partial
        .field(797, "Y") // CopyMsgIndicator: this is a drop copy, not the original report
        .field(FixTags.SECURITY_ID, execution.instrumentId()) // FIGI on this surface
        .field(FixTags.SIDE, execution.side())
        .field(32, execution.lastQty()) // LastQty
        .field(31, execution.lastPx()) // LastPx
        .field(14, execution.cumQty()) // CumQty
        .field(151, execution.leavesQty()) // LeavesQty
        .field(1, execution.account()) // Account
        .field(60, execution.tsMicros()) // TransactTime (micros, demo encoding)
        .build();
  }
}
