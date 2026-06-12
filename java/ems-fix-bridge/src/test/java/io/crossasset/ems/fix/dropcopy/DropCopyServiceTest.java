/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.dropcopy;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fix.dropcopy.DropCopyService.Execution;
import io.crossasset.ems.fix.dropcopy.DropCopyService.ScopeKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 12.16: drop-copy scoping (client/desk/firm), per-subscriber sequencing, the 797=Y copy marker,
 * and deterministic payloads.
 */
class DropCopyServiceTest {

  private record Delivered(String subscriptionId, long seq, String fix) {}

  private static Execution exec(String execId, String account, String desk, String firm) {
    return new Execution(
        execId, "ORD-1", account, desk, firm, "BBG000B9XRY4", 1, 100, 1_824_500L, 100, 400, 1_000L);
  }

  @Test
  void scopesMirrorOnlyTheirOwnFlow() {
    DropCopyService service = new DropCopyService("EMS");
    List<Delivered> client = new ArrayList<>();
    List<Delivered> desk = new ArrayList<>();
    List<Delivered> firm = new ArrayList<>();
    service.subscribe(ScopeKind.CLIENT_ACCOUNT, "ACC-A", "PB1",
        (id, seq, fix) -> client.add(new Delivered(id, seq, fix)));
    service.subscribe(ScopeKind.DESK, "desk-1", "DESKHEAD",
        (id, seq, fix) -> desk.add(new Delivered(id, seq, fix)));
    service.subscribe(ScopeKind.FIRM, "firm-demo", "RISK",
        (id, seq, fix) -> firm.add(new Delivered(id, seq, fix)));

    service.onExecution(exec("E1", "ACC-A", "desk-1", "firm-demo")); // all three
    service.onExecution(exec("E2", "ACC-B", "desk-1", "firm-demo")); // desk + firm
    service.onExecution(exec("E3", "ACC-C", "desk-2", "firm-demo")); // firm only

    assertThat(client).hasSize(1);
    assertThat(desk).hasSize(2);
    assertThat(firm).hasSize(3);
    // Per-subscriber sequence is gapless and ordered.
    assertThat(firm.stream().map(Delivered::seq)).containsExactly(1L, 2L, 3L);
  }

  @Test
  void copiesCarryTheCopyMsgIndicatorAndSubscriberCompId() {
    DropCopyService service = new DropCopyService("EMS");
    List<Delivered> out = new ArrayList<>();
    service.subscribe(ScopeKind.FIRM, "firm-demo", "RISK",
        (id, seq, fix) -> out.add(new Delivered(id, seq, fix)));
    service.onExecution(exec("E1", "ACC-A", "desk-1", "firm-demo"));

    String fix = out.get(0).fix();
    assertThat(fix).contains("35=8"); // ExecutionReport
    assertThat(fix).contains("797=Y"); // a COPY, never the original
    assertThat(fix).contains("49=EMS").contains("56=RISK");
    assertThat(fix).contains("17=E1").contains("32=100").contains("31=1824500");
    assertThat(fix).contains("150=F").contains("39=1"); // partial: leaves 400
  }

  @Test
  void unsubscribeStopsTheMirror_payloadsAreDeterministic() {
    DropCopyService a = new DropCopyService("EMS");
    DropCopyService b = new DropCopyService("EMS");
    List<String> fixA = new ArrayList<>();
    List<String> fixB = new ArrayList<>();
    String subA = a.subscribe(ScopeKind.DESK, "desk-1", "X", (id, seq, fix) -> fixA.add(fix));
    b.subscribe(ScopeKind.DESK, "desk-1", "X", (id, seq, fix) -> fixB.add(fix));

    a.onExecution(exec("E1", "ACC-A", "desk-1", "firm-demo"));
    b.onExecution(exec("E1", "ACC-A", "desk-1", "firm-demo"));
    assertThat(fixA).containsExactlyElementsOf(fixB); // replay-deterministic bytes

    a.unsubscribe(subA);
    a.onExecution(exec("E2", "ACC-A", "desk-1", "firm-demo"));
    assertThat(fixA).hasSize(1);
    assertThat(a.deliveredCount(subA)).isEqualTo(1);
  }
}
