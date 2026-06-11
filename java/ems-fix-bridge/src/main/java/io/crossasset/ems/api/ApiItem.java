/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import org.jspecify.annotations.Nullable;

/**
 * One item of a batch {@link ApiRequest}. The item type must match the request's {@link
 * ApiOperation}. Quantities/prices use the same fixed-point longs as the OMS; side/tif use FIX tag
 * 54/59 integer codes.
 */
public sealed interface ApiItem {

  /**
   * Item for {@link ApiOperation#PREVIEW_VALIDATE} (18.2): dry-run the validator over a partial
   * ticket envelope. Accepted refId = figi; rejected refId names the offending field.
   */
  record PreviewOrder(String figi) implements ApiItem {}

  /** Item for {@link ApiOperation#STAGE_ORDERS}. */
  record StageOrder(
      String clOrdId,
      String figi,
      int side,
      long qty,
      @Nullable Long price,
      String account,
      int tif)
      implements ApiItem {}

  /** Item for {@link ApiOperation#AMEND_ORDERS} (pre-route field edit). */
  record AmendOrder(String orderId, @Nullable Long qty, @Nullable Long price) implements ApiItem {}

  /** Item for {@link ApiOperation#CANCEL_ORDERS}. */
  record CancelOrder(String orderId) implements ApiItem {}

  /** Item for {@link ApiOperation#MARK_READY}. */
  record MarkReady(String orderId) implements ApiItem {}

  /** Item for {@link ApiOperation#ROUTE_ORDERS}. */
  record RouteOrder(String orderId, String venueMic, long qty, @Nullable Long price)
      implements ApiItem {}

  /** Item for {@link ApiOperation#CANCEL_ROUTES}. */
  record CancelRoute(String routeId) implements ApiItem {}

  /** Item for {@link ApiOperation#SUBSCRIBE}: a topic cursor starting at {@code fromSeq}. */
  record Subscribe(String topic, long fromSeq) implements ApiItem {}

  /** Item for {@link ApiOperation#UNSUBSCRIBE}. */
  record Unsubscribe(String subscriptionId) implements ApiItem {}
}
