/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.oms.OrderSubState;
import io.crossasset.ems.oms.StagedOrder;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests for {@link ExecutionReportEncoder} and {@link BusinessMessageRejectEncoder}. */
class EncoderTest {

  static final char SOH = '\u0001';
  static final long SESSION_ID = 7L;

  // ── ExecutionReport ────────────────────────────────────────────────────────

  @Test
  void execReport_acceptedNewOrder_hasCorrectOrdStatusAndExecType() {
    StagedOrder order = makeStagedOrder("EMS-ORD-1", "CL001", SESSION_ID, OrderFsmState.NEW);

    ExecutionReportEncoder encoder = new ExecutionReportEncoder("EMS", "CLIENT");
    String wire =
        encoder.encode(order, 1L, 1L, "0"); // seqNum=1, execId=1, execType override ignored

    FixMessage msg = FixMessage.parse(wire);
    assertThat(msg.get(FixTags.MSG_TYPE)).isEqualTo("8");
    assertThat(msg.get(FixTags.ORD_STATUS)).isEqualTo("0"); // NEW
    assertThat(msg.get(FixTags.EXEC_TYPE)).isEqualTo("0"); // NEW
    assertThat(msg.get(FixTags.CL_ORD_ID)).isEqualTo("CL001");
    assertThat(msg.get(FixTags.ORDER_ID)).isEqualTo("EMS-ORD-1");
  }

  @Test
  void execReport_pendingNew_hasCorrectOrdStatus() {
    StagedOrder order =
        makeStagedOrder("EMS-ORD-2", "CL002", SESSION_ID, OrderFsmState.PENDING_NEW);
    ExecutionReportEncoder encoder = new ExecutionReportEncoder("EMS", "CLIENT");
    String wire = encoder.encode(order, 2L, 2L, "A");
    FixMessage msg = FixMessage.parse(wire);
    assertThat(msg.get(FixTags.ORD_STATUS)).isEqualTo("A");
    assertThat(msg.get(FixTags.EXEC_TYPE)).isEqualTo("A");
  }

  @Test
  void execReport_checksumPresent() {
    StagedOrder order = makeStagedOrder("EMS-ORD-3", "CL003", SESSION_ID, OrderFsmState.NEW);
    String wire = new ExecutionReportEncoder("EMS", "CLIENT").encode(order, 3L, 3L, "0");
    FixMessage msg = FixMessage.parse(wire);
    assertThat(msg.get(FixTags.CHECKSUM)).matches("\\d{3}");
  }

  // ── BusinessMessageReject ──────────────────────────────────────────────────

  @Test
  void businessReject_hasMsgType_j() {
    BusinessMessageRejectEncoder encoder = new BusinessMessageRejectEncoder("EMS", "CLIENT");
    String wire = encoder.encode(1L, "D", 0, "EMS-FIX-1001", "Unsupported order type");
    FixMessage msg = FixMessage.parse(wire);
    assertThat(msg.get(FixTags.MSG_TYPE)).isEqualTo("j");
  }

  @Test
  void businessReject_containsRefMsgTypeAndText() {
    BusinessMessageRejectEncoder encoder = new BusinessMessageRejectEncoder("EMS", "CLIENT");
    String wire = encoder.encode(1L, "F", 3, "EMS-FIX-1002", "Cancel not supported");
    FixMessage msg = FixMessage.parse(wire);
    assertThat(msg.get(FixTags.REF_MSG_TYPE)).isEqualTo("F"); // tag 372
    assertThat(msg.get(FixTags.BUSINESS_REJECT_REASON)).isEqualTo("3"); // tag 380
    assertThat(msg.get(FixTags.TEXT)).contains("Cancel not supported");
  }

  @Test
  void businessReject_checksumPresent() {
    String wire =
        new BusinessMessageRejectEncoder("EMS", "CLIENT")
            .encode(1L, "G", 3, "EMS-FIX-1003", "Replace not supported");
    FixMessage msg = FixMessage.parse(wire);
    assertThat(msg.get(FixTags.CHECKSUM)).matches("\\d{3}");
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private StagedOrder makeStagedOrder(
      String orderId, String clOrdId, long sessionId, OrderFsmState state) {
    OrderFsmContext ctx =
        new OrderFsmContext(
            orderId,
            clOrdId,
            null,
            "BBG000BLNNH6",
            1,
            100L,
            null,
            0L,
            100L,
            "ACC1",
            0,
            clOrdId,
            orderId,
            1L,
            null,
            null);
    return new StagedOrder(
        orderId, clOrdId, sessionId, state, ctx, OrderSubState.NEW, Set.of(), 1_000_000L);
  }
}
