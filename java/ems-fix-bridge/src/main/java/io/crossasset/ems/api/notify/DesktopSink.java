/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.api.SubscriptionRegistry;
import java.util.Objects;

/**
 * Desktop channel (task 18.8): notifications publish as keyed rows on {@code notify.{audience}}
 * topics — cursor-resumable over the same WS edge as every other stream, so the trader's
 * notification queue replays on a refreshed tab exactly like the blotter does.
 */
public final class DesktopSink implements NotificationService.NotificationSink {

  /** Topic prefix; full topic is {@code notify.{audience}}. */
  public static final String TOPIC_PREFIX = "notify.";

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry subscriptions;

  public DesktopSink(SubscriptionRegistry subscriptions) {
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
  }

  @Override
  public NotificationService.Channel channel() {
    return NotificationService.Channel.DESKTOP;
  }

  @Override
  public boolean deliver(NotificationService.Notification notification, String audience) {
    ObjectNode row = mapper.createObjectNode();
    row.put("notificationId", notification.notificationId());
    row.put("kind", notification.kind().name());
    row.put("source", notification.source());
    row.put("severity", notification.severity().name());
    row.put("subject", notification.subject());
    row.put("body", notification.body());
    row.put("ackRequired", notification.ackRequired());
    row.put("count", notification.collapsedCount());
    row.put("ts", notification.createdAtMillis());
    subscriptions.publish(
        TOPIC_PREFIX + audience, "NotificationRow", notification.notificationId(), row.toString());
    return true;
  }
}
