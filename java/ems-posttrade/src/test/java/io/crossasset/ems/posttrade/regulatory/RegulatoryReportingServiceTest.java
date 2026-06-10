/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportAcked;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportAmended;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportDeferred;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportFailed;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportNacked;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportSubmitted;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportVoided;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link InMemoryRegulatoryReportingService}. */
class RegulatoryReportingServiceTest {

  /** Adapter that nacks the first {@code nacksBeforeAck} submits, then acks (or always nacks). */
  static final class FakeAdapter implements RegulatorAdapter {
    final int nacksBeforeAck;
    final boolean alwaysNack;
    int calls = 0;

    FakeAdapter(int nacksBeforeAck, boolean alwaysNack) {
      this.nacksBeforeAck = nacksBeforeAck;
      this.alwaysNack = alwaysNack;
    }

    @Override
    public Regulator regulator() {
      return Regulator.TRACE;
    }

    @Override
    public String buildPayload(ReportableTrade trade) {
      return "TRACE:" + trade.tradeRef();
    }

    @Override
    public SubmitResponse submit(String payload) {
      calls++;
      if (alwaysNack || calls <= nacksBeforeAck) {
        return SubmitResponse.nacked("E-RETRY");
      }
      return SubmitResponse.acked("ACK-" + calls);
    }
  }

  private static Map<String, String> fullTraceFields() {
    Map<String, String> f = new HashMap<>();
    f.put("trace_party_id", "BD123");
    f.put("cusip_or_isin", "US123456AB12");
    f.put("executing_broker", "EB1");
    f.put("contra_party_id", "C");
    f.put("side", "1");
    f.put("qty", "100");
    f.put("price", "9950");
    f.put("yield", "452");
    f.put("trade_date", "2026-06-09");
    f.put("settle_date", "2026-06-11");
    return f;
  }

  private static ReportableTrade trade(Map<String, String> fields) {
    return new ReportableTrade("TR-1", "CORP_BOND", "US", "US123456AB12", 1, 100, 9950, fields);
  }

  private static InMemoryRegulatoryReportingService service(RegulatorAdapter adapter) {
    InMemoryRegulatoryReportingService svc =
        new InMemoryRegulatoryReportingService(RegulatorDeterminer.usDefaults());
    svc.registerProfile(ReportingProfile.trace());
    svc.registerAdapter(adapter);
    return svc;
  }

  @Test
  void corpBondUs_submitsToTrace_andAcks() {
    InMemoryRegulatoryReportingService svc = service(new FakeAdapter(0, false));
    List<RegReportEvent> events = svc.report(trade(fullTraceFields()), "AllocationApplied");

    assertThat(events).anyMatch(e -> e instanceof RegReportAcked);
    assertThat(svc.stateOf("TR-1:TRACE")).contains(ReportState.ACKED);
  }

  @Test
  void missingRequiredField_deferred() {
    Map<String, String> fields = fullTraceFields();
    fields.remove("yield");
    InMemoryRegulatoryReportingService svc = service(new FakeAdapter(0, false));

    List<RegReportEvent> events = svc.report(trade(fields), "AllocationApplied");

    assertThat(events)
        .anySatisfy(
            e ->
                assertThat(e)
                    .isInstanceOfSatisfying(
                        RegReportDeferred.class,
                        d -> assertThat(d.missingFields()).contains("yield")));
    assertThat(svc.stateOf("TR-1:TRACE")).contains(ReportState.DEFERRED);
    assertThat(events).noneMatch(e -> e instanceof RegReportSubmitted);
  }

  @Test
  void nackThenAck_retriesThenAcks() {
    InMemoryRegulatoryReportingService svc = service(new FakeAdapter(2, false));
    List<RegReportEvent> events = svc.report(trade(fullTraceFields()), "AllocationApplied");

    assertThat(events).filteredOn(e -> e instanceof RegReportSubmitted).hasSize(3);
    assertThat(events).filteredOn(e -> e instanceof RegReportNacked).hasSize(2);
    assertThat(events).anyMatch(e -> e instanceof RegReportAcked);
    assertThat(svc.stateOf("TR-1:TRACE")).contains(ReportState.ACKED);
  }

  @Test
  void allNacks_failsAfterMaxRetries() {
    // TRACE profile maxRetries=3 → 4 attempts then fail.
    InMemoryRegulatoryReportingService svc = service(new FakeAdapter(0, true));
    List<RegReportEvent> events = svc.report(trade(fullTraceFields()), "AllocationApplied");

    assertThat(events).filteredOn(e -> e instanceof RegReportSubmitted).hasSize(4);
    assertThat(events).anyMatch(e -> e instanceof RegReportFailed);
    assertThat(svc.stateOf("TR-1:TRACE")).contains(ReportState.FAILED);
  }

  @Test
  void noApplicableRegulator_emitsNothing() {
    InMemoryRegulatoryReportingService svc = service(new FakeAdapter(0, false));
    ReportableTrade nonUs =
        new ReportableTrade("TR-9", "CORP_BOND", "JP", "JP1234567890", 1, 100, 9950, Map.of());
    assertThat(svc.report(nonUs, "AllocationApplied")).isEmpty();
  }

  @Test
  void amend_voidAndReplace_emitsVoidAndReplacementAndAmended() {
    InMemoryRegulatoryReportingService svc = service(new FakeAdapter(0, false));
    svc.report(trade(fullTraceFields()), "AllocationApplied");

    List<RegReportEvent> events =
        svc.amend(Regulator.TRACE, trade(fullTraceFields()), "trade_correct");

    assertThat(events).anyMatch(e -> e instanceof RegReportVoided);
    assertThat(events).anyMatch(e -> e instanceof RegReportAmended);
    assertThat(events).anyMatch(e -> e instanceof RegReportAcked); // replacement acked
    assertThat(svc.stateOf("TR-1:TRACE")).contains(ReportState.VOIDED);
  }
}
