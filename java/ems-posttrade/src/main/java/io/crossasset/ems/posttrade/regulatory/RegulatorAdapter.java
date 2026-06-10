/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

/**
 * A per-regulator wire adapter: turns the canonical trade into the regulator's payload format and
 * submits it, returning the ack/nack. Per arch-regulatory-reporting-service.md § Architecture
 * (per-regulator adapter). The MVP provides a TRACE-mock adapter (12.6); real submissions are
 * sandboxed under replay so the would-be wire bytes are deterministic.
 */
public interface RegulatorAdapter {

  /** A regulator's reply to a submission. */
  record SubmitResponse(boolean acked, String ackRef, String errorCode) {
    public static SubmitResponse acked(String ackRef) {
      return new SubmitResponse(true, ackRef, null);
    }

    public static SubmitResponse nacked(String errorCode) {
      return new SubmitResponse(false, null, errorCode);
    }
  }

  Regulator regulator();

  /** Build the regulator-specific payload (deterministic for a given trade + profile version). */
  String buildPayload(ReportableTrade trade);

  /** Submit a payload and return the regulator's ack/nack. */
  SubmitResponse submit(String payload);
}
