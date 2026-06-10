/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.oms.StagedOrder;

/**
 * Encodes an outbound {@code 35=8 ExecutionReport} from a {@link StagedOrder}. {@code
 * OrdStatus(39)} and {@code ExecType(150)} are derived from the order's FSM state per the
 * arch-order-route-lifecycle mapping (the same mapping documented on {@link OrderFsmState}).
 */
public final class ExecutionReportEncoder {

  private final String senderCompId;
  private final String targetCompId;

  public ExecutionReportEncoder(String senderCompId, String targetCompId) {
    this.senderCompId = senderCompId;
    this.targetCompId = targetCompId;
  }

  /** {@code OrdStatus(39)} / {@code ExecType(150)} pair for an FSM state. */
  record Status(String ordStatus, String execType) {}

  static Status statusFor(OrderFsmState state) {
    return switch (state) {
      case PENDING_NEW -> new Status("A", "A");
      case NEW -> new Status("0", "0");
      case PENDING_REPLACE -> new Status("E", "E");
      case REPLACED -> new Status("5", "5");
      case PENDING_CANCEL -> new Status("6", "6");
      case PARTIALLY_FILLED -> new Status("1", "F");
      case FILLED -> new Status("2", "F");
      case CANCELED -> new Status("4", "4");
      case REJECTED -> new Status("8", "8");
      case EXPIRED -> new Status("C", "C");
      case DONE_FOR_DAY -> new Status("3", "3");
      case TRADE_CORRECTED -> new Status("2", "G");
      case TRADE_CANCELED -> new Status("2", "H");
    };
  }

  /**
   * Encode an ExecutionReport. {@code OrdStatus}/{@code ExecType} come from the order's FSM state;
   * the {@code execTypeHint} argument is accepted for caller convenience but the wire value is
   * always derived from state (the two never disagree for a state-driven report).
   */
  public String encode(StagedOrder order, long seqNum, long execId, String execTypeHint) {
    Status status = statusFor(order.fsmState());
    var ctx = order.fsmContext();
    long leaves = Math.max(0L, ctx.orderQty() - ctx.cumQty());
    return FixMessage.builder()
        .field(FixTags.MSG_TYPE, "8")
        .field(FixTags.SENDER_COMP_ID, senderCompId)
        .field(FixTags.TARGET_COMP_ID, targetCompId)
        .field(FixTags.MSG_SEQ_NUM, seqNum)
        .field(FixTags.ORDER_ID, order.orderId())
        .field(FixTags.EXEC_ID, execId)
        .field(FixTags.CL_ORD_ID, order.clOrdId())
        .field(FixTags.SECURITY_ID, ctx.instrumentId())
        .field(FixTags.SIDE, ctx.side())
        .field(FixTags.ORDER_QTY, ctx.orderQty())
        .field(FixTags.EXEC_TYPE, status.execType())
        .field(FixTags.ORD_STATUS, status.ordStatus())
        .field(FixTags.LEAVES_QTY, leaves)
        .field(FixTags.CUM_QTY, ctx.cumQty())
        .build();
  }
}
