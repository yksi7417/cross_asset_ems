/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.FixTags;
import io.crossasset.ems.fix.sim.FixVenueSimulator.Behavior;
import io.crossasset.ems.fix.sim.FixVenueSimulator.ExecutionModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FixVenueSimulator}: logon/heartbeat session layer, execution models with
 * Appendix-D pending states, cancel/replace ClOrdID chaining, busts, gap detection and PossDup
 * resend. Per task 11.15.
 */
class FixVenueSimulatorTest {

  private static final String FIGI = "BBG000BLNNH6";

  private final List<String> wire = new ArrayList<>();
  private long initiatorSeq = 1;

  private FixVenueSimulator simulator(ExecutionModel model) {
    FixVenueSimulator sim = new FixVenueSimulator("SIMX", "EMS", model, wire::add);
    sim.onInbound(
        initiatorMsg("A").field(FixTags.ENCRYPT_METHOD, 0).field(FixTags.HEART_BT_INT, 30).build());
    wire.clear(); // drop the logon ack for cleaner assertions
    return sim;
  }

  // ── Session layer ───────────────────────────────────────────────────────────

  @Test
  void logon_replied() {
    FixVenueSimulator sim =
        new FixVenueSimulator("SIMX", "EMS", ExecutionModel.fullFill(100), wire::add);
    sim.onInbound(initiatorMsg("A").field(FixTags.HEART_BT_INT, 30).build());
    FixMessage logon = FixMessage.parse(wire.get(0));
    assertThat(logon.get(FixTags.MSG_TYPE)).isEqualTo("A");
    assertThat(logon.get(FixTags.SENDER_COMP_ID)).isEqualTo("SIMX");
    assertThat(logon.get(FixTags.HEART_BT_INT)).isEqualTo("30");
  }

  @Test
  void testRequest_heartbeatEcho() {
    FixVenueSimulator sim = simulator(ExecutionModel.fullFill(100));
    sim.onInbound(initiatorMsg("1").field(FixTags.TEST_REQ_ID, "PING").build());
    FixMessage hb = FixMessage.parse(wire.get(0));
    assertThat(hb.get(FixTags.MSG_TYPE)).isEqualTo("0");
    assertThat(hb.get(FixTags.TEST_REQ_ID)).isEqualTo("PING");
  }

  @Test
  void inboundGap_emitsResendRequestAndDropsMessage() {
    FixVenueSimulator sim = simulator(ExecutionModel.fullFill(100));
    initiatorSeq = 10; // jump: simulator expects 2
    sim.onInbound(nos("CL-GAP", 100, null));
    FixMessage rr = FixMessage.parse(wire.get(0));
    assertThat(rr.get(FixTags.MSG_TYPE)).isEqualTo("2");
    assertThat(rr.get(FixTags.BEGIN_SEQ_NO)).isEqualTo("2");
    assertThat(wire).hasSize(1); // the NOS was not processed
  }

  @Test
  void resendRequest_replaysWithPossDupAtOriginalSeqs() {
    FixVenueSimulator sim = simulator(ExecutionModel.fullFill(100));
    sim.onInbound(nos("CL-1", 100, 990L)); // ack + fill emitted (seqs 2,3)
    wire.clear();
    sim.onInbound(
        initiatorMsg("2").field(FixTags.BEGIN_SEQ_NO, 2).field(FixTags.END_SEQ_NO, 0).build());
    assertThat(wire).hasSize(2);
    FixMessage first = FixMessage.parse(wire.get(0));
    assertThat(first.get(FixTags.POSS_DUP_FLAG)).isEqualTo("Y");
    assertThat(first.get(FixTags.MSG_SEQ_NUM)).isEqualTo("2");
    assertThat(first.get(FixTags.EXEC_TYPE)).isEqualTo("0");
    FixMessage second = FixMessage.parse(wire.get(1));
    assertThat(second.get(FixTags.MSG_SEQ_NUM)).isEqualTo("3");
    assertThat(second.get(FixTags.EXEC_TYPE)).isEqualTo("F");
  }

  @Test
  void sequenceReset_jumpsExpectedSeq() {
    FixVenueSimulator sim = simulator(ExecutionModel.fullFill(100));
    sim.onInbound(initiatorMsg("4").field(FixTags.NEW_SEQ_NO, 50).build());
    initiatorSeq = 50;
    sim.onInbound(nos("CL-AFTER-RESET", 100, null));
    assertThat(wire.stream().map(FixMessage::parse))
        .anyMatch(m -> "8".equals(m.get(FixTags.MSG_TYPE)));
  }

  // ── Execution models ────────────────────────────────────────────────────────

  @Test
  void fullFillModel_ackThenFilledAtLimit() {
    FixVenueSimulator sim = simulator(ExecutionModel.fullFill(100));
    sim.onInbound(nos("CL-1", 500, 990L));
    List<FixMessage> ers = wire.stream().map(FixMessage::parse).toList();
    assertThat(ers).hasSize(2);
    assertThat(ers.get(0).get(FixTags.EXEC_TYPE)).isEqualTo("0");
    assertThat(ers.get(1).get(FixTags.EXEC_TYPE)).isEqualTo("F");
    assertThat(ers.get(1).get(FixTags.ORD_STATUS)).isEqualTo("2");
    assertThat(ers.get(1).get(FixTags.LAST_PX)).isEqualTo("990");
    assertThat(ers.get(1).get(FixTags.LAST_QTY)).isEqualTo("500");
    assertThat(ers.get(1).get(FixTags.LEAVES_QTY)).isEqualTo("0");
  }

  @Test
  void marketOrder_fillsAtFallbackMark() {
    FixVenueSimulator sim = simulator(ExecutionModel.fullFill(1234));
    sim.onInbound(nos("CL-MKT", 100, null));
    FixMessage fill = FixMessage.parse(wire.get(1));
    assertThat(fill.get(FixTags.LAST_PX)).isEqualTo("1234");
  }

  @Test
  void partialThenFullModel_twoFillsWithRunningCum() {
    FixVenueSimulator sim =
        simulator(new ExecutionModel(Behavior.ACK_THEN_PARTIAL_THEN_FULL, false, true, true, 100));
    sim.onInbound(nos("CL-P", 100, 990L));
    List<FixMessage> ers = wire.stream().map(FixMessage::parse).toList();
    assertThat(ers).hasSize(3);
    assertThat(ers.get(1).get(FixTags.ORD_STATUS)).isEqualTo("1");
    assertThat(ers.get(1).get(FixTags.CUM_QTY)).isEqualTo("50");
    assertThat(ers.get(2).get(FixTags.ORD_STATUS)).isEqualTo("2");
    assertThat(ers.get(2).get(FixTags.CUM_QTY)).isEqualTo("100");
  }

  @Test
  void rejectModel_emitsSubmissionReject() {
    FixVenueSimulator sim = simulator(new ExecutionModel(Behavior.REJECT, false, true, true, 100));
    sim.onInbound(nos("CL-R", 100, null));
    FixMessage er = FixMessage.parse(wire.get(0));
    assertThat(er.get(FixTags.EXEC_TYPE)).isEqualTo("8");
    assertThat(er.get(FixTags.TEXT)).contains("reject");
  }

  @Test
  void pendingNewFirst_emitsExecTypeABeforeAck() {
    FixVenueSimulator sim =
        simulator(new ExecutionModel(Behavior.ACK_THEN_FULL_FILL, true, true, true, 100));
    sim.onInbound(nos("CL-PN", 100, null));
    assertThat(FixMessage.parse(wire.get(0)).get(FixTags.EXEC_TYPE)).isEqualTo("A");
    assertThat(FixMessage.parse(wire.get(1)).get(FixTags.EXEC_TYPE)).isEqualTo("0");
  }

  // ── Cancel / replace (Appendix D) ───────────────────────────────────────────

  @Test
  void cancel_pendingCancelThenCanceled_withClOrdIdChain() {
    FixVenueSimulator sim =
        simulator(new ExecutionModel(Behavior.ACK_ONLY, false, true, true, 100));
    sim.onInbound(nos("CL-1", 100, null));
    wire.clear();
    sim.onInbound(
        initiatorMsg("F")
            .field(FixTags.ORIG_CL_ORD_ID, "CL-1")
            .field(FixTags.CL_ORD_ID, "CL-1.C1")
            .build());
    List<FixMessage> ers = wire.stream().map(FixMessage::parse).toList();
    assertThat(ers.get(0).get(FixTags.EXEC_TYPE)).isEqualTo("6");
    assertThat(ers.get(1).get(FixTags.EXEC_TYPE)).isEqualTo("4");
    assertThat(ers.get(1).get(FixTags.CL_ORD_ID)).isEqualTo("CL-1.C1");
    assertThat(ers.get(1).get(FixTags.ORIG_CL_ORD_ID)).isEqualTo("CL-1");
  }

  @Test
  void cancel_unknownOrder_rejectedWith35Eq9() {
    FixVenueSimulator sim = simulator(ExecutionModel.fullFill(100));
    sim.onInbound(
        initiatorMsg("F")
            .field(FixTags.ORIG_CL_ORD_ID, "NOPE")
            .field(FixTags.CL_ORD_ID, "NOPE.C1")
            .build());
    FixMessage rej = FixMessage.parse(wire.get(0));
    assertThat(rej.get(FixTags.MSG_TYPE)).isEqualTo("9");
    assertThat(rej.get(FixTags.CXL_REJ_RESPONSE_TO)).isEqualTo("1");
  }

  @Test
  void replace_pendingThenReplaced_newClOrdIdBecomesLive() {
    FixVenueSimulator sim =
        simulator(new ExecutionModel(Behavior.ACK_ONLY, false, true, true, 100));
    sim.onInbound(nos("CL-1", 100, 990L));
    wire.clear();
    sim.onInbound(
        initiatorMsg("G")
            .field(FixTags.ORIG_CL_ORD_ID, "CL-1")
            .field(FixTags.CL_ORD_ID, "CL-1-R1")
            .field(FixTags.ORDER_QTY, 150)
            .field(FixTags.PRICE, 980)
            .build());
    List<FixMessage> ers = wire.stream().map(FixMessage::parse).toList();
    assertThat(ers.get(0).get(FixTags.EXEC_TYPE)).isEqualTo("E");
    assertThat(ers.get(1).get(FixTags.EXEC_TYPE)).isEqualTo("5");
    assertThat(ers.get(1).get(FixTags.LEAVES_QTY)).isEqualTo("150");

    // The replacement ClOrdID is now the live chain identity: cancel against it succeeds.
    wire.clear();
    sim.onInbound(
        initiatorMsg("F")
            .field(FixTags.ORIG_CL_ORD_ID, "CL-1-R1")
            .field(FixTags.CL_ORD_ID, "CL-1-R1.C1")
            .build());
    assertThat(FixMessage.parse(wire.get(1)).get(FixTags.EXEC_TYPE)).isEqualTo("4");
  }

  // ── Fills + busts (ACK_ONLY external drive) ─────────────────────────────────

  @Test
  void externalFill_thenBust_rewindsCumQty() {
    FixVenueSimulator sim =
        simulator(new ExecutionModel(Behavior.ACK_ONLY, false, true, true, 100));
    sim.onInbound(nos("CL-1", 100, 990L));
    String execId = sim.fill("CL-1", 60, 990).orElseThrow();
    wire.clear();
    String bustId = sim.bust(execId).orElseThrow();
    assertThat(bustId).isNotEqualTo(execId);
    FixMessage bust = FixMessage.parse(wire.get(0));
    assertThat(bust.get(FixTags.EXEC_TYPE)).isEqualTo("H");
    assertThat(bust.get(FixTags.EXEC_REF_ID)).isEqualTo(execId);
    assertThat(bust.get(FixTags.CUM_QTY)).isEqualTo("0");
    assertThat(bust.get(FixTags.LEAVES_QTY)).isEqualTo("100");
  }

  @Test
  void overfill_refused() {
    FixVenueSimulator sim =
        simulator(new ExecutionModel(Behavior.ACK_ONLY, false, true, true, 100));
    sim.onInbound(nos("CL-1", 100, null));
    assertThat(sim.fill("CL-1", 101, 990)).isEmpty();
    assertThat(sim.fill("CL-UNKNOWN", 1, 990)).isEmpty();
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private FixMessage.Builder initiatorMsg(String msgType) {
    return FixMessage.builder()
        .field(FixTags.MSG_TYPE, msgType)
        .field(FixTags.MSG_SEQ_NUM, initiatorSeq++)
        .field(FixTags.SENDER_COMP_ID, "EMS")
        .field(FixTags.TARGET_COMP_ID, "SIMX");
  }

  private String nos(String clOrdId, long qty, Long px) {
    FixMessage.Builder b =
        initiatorMsg("D")
            .field(FixTags.CL_ORD_ID, clOrdId)
            .field(FixTags.SECURITY_ID, FIGI)
            .field(FixTags.SIDE, 1)
            .field(FixTags.ORDER_QTY, qty);
    if (px != null) {
      b.field(FixTags.ORD_TYPE, 2).field(FixTags.PRICE, px);
    } else {
      b.field(FixTags.ORD_TYPE, 1);
    }
    return b.build();
  }
}
