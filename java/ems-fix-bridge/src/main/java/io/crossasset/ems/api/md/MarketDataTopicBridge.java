/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.md;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.md.FeedHealth;
import io.crossasset.ems.md.MarketDataFeed;
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.MdTick;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges the market-data feed SPI (18.12) into the 8.4 subscription topics, so the Perspective
 * desktop consumes ticks exactly like order events: cursor-resumable {@code ApiEvent}s over the
 * REST/WS edge (8.10). Each tick publishes a flat JSON row delta — Perspective {@code
 * table.update()} input, FIGI-keyed — on the {@code md} firehose topic and the per-instrument
 * {@code md.{figi}} topic (mirror of {@code orders} / {@code order.{id}}). Feed health publishes on
 * {@code md.feed.{feedId}} for the desktop's feed-status badge.
 */
public final class MarketDataTopicBridge {

  /** Firehose topic carrying every bridged tick. */
  public static final String TOPIC_MD = "md";

  /** Per-instrument topic prefix. */
  public static final String TOPIC_MD_PREFIX = "md.";

  /** Feed-health topic prefix. */
  public static final String TOPIC_FEED_PREFIX = "md.feed.";

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry subscriptions;
  private final ConcurrentHashMap<String, Attachment> attachments = new ConcurrentHashMap<>();

  private record Attachment(MarketDataFeed feed, String subscriptionId) {}

  public MarketDataTopicBridge(SubscriptionRegistry subscriptions) {
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
  }

  /**
   * Subscribe {@code figi}'s fields on {@code feed} and republish every tick onto the md topics.
   * Returns an attachment ID for {@link #detach}. Health bridging rides along: the first attach for
   * a feed also registers the health listener.
   */
  public String attach(MarketDataFeed feed, String figi, Set<MdField> fields) {
    String subscriptionId = feed.subscribe(figi, fields, new BridgeListener());
    String attachmentId = "MDB-" + feed.feedId() + "-" + subscriptionId;
    attachments.put(attachmentId, new Attachment(feed, subscriptionId));
    return attachmentId;
  }

  /** Stop bridging one attachment. Returns false if the ID is unknown. */
  public boolean detach(String attachmentId) {
    Attachment attachment = attachments.remove(attachmentId);
    if (attachment == null) {
      return false;
    }
    return attachment.feed().unsubscribe(attachment.subscriptionId());
  }

  /** Republish {@code feed}'s health transitions onto {@code md.feed.{feedId}}. */
  public void attachHealth(MarketDataFeed feed) {
    feed.addHealthListener(this::publishHealth);
  }

  private void publishHealth(String feedId, FeedHealth health) {
    ObjectNode row = mapper.createObjectNode();
    row.put("feed", feedId);
    row.put("status", health.status().name());
    row.put("detail", health.detail());
    row.put("ts", health.atMillis());
    subscriptions.publish(TOPIC_FEED_PREFIX + feedId, "FeedHealth", feedId, row.toString());
  }

  private final class BridgeListener implements MarketDataFeed.Listener {

    @Override
    public void onTick(MdTick tick) {
      String payload = toRow(tick);
      subscriptions.publish(TOPIC_MD, "MdTick", tick.figi(), payload);
      subscriptions.publish(TOPIC_MD_PREFIX + tick.figi(), "MdTick", tick.figi(), payload);
    }

    @Override
    public void onSubscriptionError(String figi, String code, String message) {
      ObjectNode row = mapper.createObjectNode();
      row.put("figi", figi);
      row.put("code", code);
      row.put("message", message);
      String payload = row.toString();
      subscriptions.publish(TOPIC_MD, "MdSubscriptionError", figi, payload);
      subscriptions.publish(TOPIC_MD_PREFIX + figi, "MdSubscriptionError", figi, payload);
    }
  }

  /** Flat JSON row: {"figi":..,"feed":..,"ts":..,"bid":..,...} — a Perspective row delta. */
  private String toRow(MdTick tick) {
    ObjectNode row = mapper.createObjectNode();
    row.put("figi", tick.figi());
    row.put("feed", tick.feedId());
    row.put("ts", tick.atMillis());
    for (Map.Entry<MdField, Long> entry : tick.values().entrySet()) {
      row.put(entry.getKey().jsonKey(), entry.getValue());
    }
    return row.toString();
  }
}
