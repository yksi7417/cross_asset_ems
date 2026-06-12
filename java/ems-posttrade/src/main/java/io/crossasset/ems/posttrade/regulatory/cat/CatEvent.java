/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.cat;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One CAT reportable order event (task 12.12, [[finra]] § CAT). CAT is ORDER-EVENT reporting —
 * every receipt, route, modify, cancel and execution of an equity/options order flows to the
 * Consolidated Audit Trail by 8am next day — structurally different from trade-level reporting
 * (TRACE 12.6): linkage is by order key, not trade ref, and one order yields many events.
 *
 * @param eventType the CAT event taxonomy entry
 * @param orderKey firm-unique order linkage key (orderId; CAT joins the lifecycle on this)
 * @param eventTimestampMicros when the event occurred (micros; CAT requires ms precision or better)
 * @param fields event-specific payload fields (symbol, side, qty, px, routedOrderID, …) —
 *     serialized in key order so payloads are replay-deterministic
 */
public record CatEvent(
    Type eventType, String orderKey, long eventTimestampMicros, Map<String, String> fields) {

  /** The CAT order-event taxonomy (the subset this EMS emits). */
  public enum Type {
    /** New order received/originated (MENO). */
    NEW_ORDER("MENO"),
    /** Order routed to a venue/broker (MEOR). */
    ORDER_ROUTE("MEOR"),
    /** Order modified — qty/px amendment (MEOM). */
    ORDER_MODIFIED("MEOM"),
    /** Order cancelled (MEOC). */
    ORDER_CANCELED("MEOC"),
    /** Order (partially) executed (MEOT). */
    ORDER_TRADE("MEOT");

    public final String catCode;

    Type(String catCode) {
      this.catCode = catCode;
    }
  }

  public CatEvent {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(orderKey, "orderKey");
    fields = fields == null ? Map.of() : Map.copyOf(fields);
  }

  /** Deterministic CAT payload line: type, key, timestamp, then fields in key order. */
  public String toPayload() {
    StringBuilder sb = new StringBuilder();
    sb.append(eventType.catCode);
    sb.append("|orderKey=").append(orderKey);
    sb.append("|ts=").append(eventTimestampMicros);
    for (var e : new TreeMap<>(fields).entrySet()) {
      sb.append('|').append(e.getKey()).append('=').append(e.getValue());
    }
    return sb.toString();
  }
}
