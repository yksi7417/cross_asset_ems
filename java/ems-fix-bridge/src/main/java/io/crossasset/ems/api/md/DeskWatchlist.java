/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.md;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.md.MarketDataFeed;
import io.crossasset.ems.md.MdField;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-desk symbol lists (task 18.14). Adding a symbol attaches it to the market-data topic bridge
 * (ticks start flowing on {@code md}/{@code md.{figi}}) and publishes a {@code WatchRow} delta on
 * the desk's {@code watchlist.{deskId}} topic; removing detaches and publishes {@code
 * WatchRemoved}. The desktop's watchlist panel replays the topic from seq 1 to rebuild the desk's
 * set, then filters the {@code md} firehose to it — so every desk sees its own list, while tick
 * fan-out stays shared.
 */
public final class DeskWatchlist {

  /** Watchlist topic prefix; full topic is {@code watchlist.{deskId}}. */
  public static final String TOPIC_PREFIX = "watchlist.";

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry subscriptions;
  private final MarketDataTopicBridge bridge;
  private final MarketDataFeed feed;
  private final Set<MdField> fields;
  private final Map<String, Map<String, String>> attachmentsByDesk = new LinkedHashMap<>();

  public DeskWatchlist(
      SubscriptionRegistry subscriptions,
      MarketDataTopicBridge bridge,
      MarketDataFeed feed,
      Set<MdField> fields) {
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.bridge = Objects.requireNonNull(bridge, "bridge");
    this.feed = Objects.requireNonNull(feed, "feed");
    this.fields = Set.copyOf(Objects.requireNonNull(fields, "fields"));
  }

  /** Add a symbol to the desk's list. Returns false if already watched. */
  public synchronized boolean add(String deskId, String figi) {
    Map<String, String> attachments =
        attachmentsByDesk.computeIfAbsent(deskId, k -> new LinkedHashMap<>());
    if (attachments.containsKey(figi)) {
      return false;
    }
    attachments.put(figi, bridge.attach(feed, figi, fields));
    publish(deskId, "WatchRow", figi);
    return true;
  }

  /** Remove a symbol from the desk's list. Returns false if it was not watched. */
  public synchronized boolean remove(String deskId, String figi) {
    Map<String, String> attachments = attachmentsByDesk.get(deskId);
    if (attachments == null) {
      return false;
    }
    String attachmentId = attachments.remove(figi);
    if (attachmentId == null) {
      return false;
    }
    bridge.detach(attachmentId);
    publish(deskId, "WatchRemoved", figi);
    return true;
  }

  /** The desk's current symbols, in insertion order. */
  public synchronized Set<String> list(String deskId) {
    Map<String, String> attachments = attachmentsByDesk.get(deskId);
    return attachments == null ? Set.of() : Set.copyOf(attachments.keySet());
  }

  private void publish(String deskId, String type, String figi) {
    ObjectNode row = mapper.createObjectNode();
    row.put("figi", figi);
    row.put("desk", deskId);
    subscriptions.publish(TOPIC_PREFIX + deskId, type, figi, row.toString());
  }
}
