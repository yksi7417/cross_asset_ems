/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import io.crossasset.ems.oms.OrderRequest;

/**
 * Decodes a FIX {@code 35=D NewOrderSingle} into the canonical {@link OrderRequest} API operation
 * (FIX maps to exactly one API op; NewOrderSingle → {@code stage_orders} batch=1 per
 * arch-fix-api-bridge.md).
 *
 * <p>The {@code SecurityID(48)} carries the FIGI on this surface. {@code requestId} is derived
 * deterministically from {@code ClOrdID(11)} so a re-sent NewOrderSingle is idempotent at the
 * application layer.
 */
public final class NewOrderSingleDecoder {

  /** FIX TimeInForce default when tag 59 is absent: Day (0). */
  private static final int DEFAULT_TIF = 0;

  /**
   * Decode {@code msg} for the given session. Required fields are checked in a fixed order so the
   * first absent one is reported; on success returns {@link DecodeResult.Ok}.
   */
  public DecodeResult decode(FixMessage msg, long sessionId) {
    String clOrdId = msg.get(FixTags.CL_ORD_ID);
    if (clOrdId == null) {
      return new DecodeResult.Missing(FixTags.CL_ORD_ID);
    }
    String figi = msg.get(FixTags.SECURITY_ID);
    if (figi == null) {
      return new DecodeResult.Missing(FixTags.SECURITY_ID);
    }
    if (!msg.has(FixTags.SIDE)) {
      return new DecodeResult.Missing(FixTags.SIDE);
    }
    if (!msg.has(FixTags.ORDER_QTY)) {
      return new DecodeResult.Missing(FixTags.ORDER_QTY);
    }
    String account = msg.get(FixTags.ACCOUNT);
    if (account == null) {
      return new DecodeResult.Missing(FixTags.ACCOUNT);
    }

    int side = msg.getInt(FixTags.SIDE);
    long qty = Long.parseLong(msg.get(FixTags.ORDER_QTY));
    Long price = msg.getOptional(FixTags.PRICE).map(Long::parseLong).orElse(null);
    int tif = msg.getOptional(FixTags.TIME_IN_FORCE).map(Integer::parseInt).orElse(DEFAULT_TIF);

    OrderRequest request =
        new OrderRequest(clOrdId, sessionId, clOrdId, figi, side, qty, price, account, tif);
    return new DecodeResult.Ok(request);
  }
}
