/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.oms.OrderSubState;
import io.crossasset.ems.oms.StagedOrder;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BlotterExporter} + {@link ExportTemplate}: templated columns, deterministic
 * ordering, fixed-point price rendering, quoting, filename convention. Per arch-bulk-io.md, task
 * 8.7.
 */
class BlotterExporterTest {

  @Test
  void toCsv_defaultTemplate_rendersHeaderAndSortedRows() {
    String csv =
        BlotterExporter.toCsv(
            List.of(
                order("EMS-ORD-2", "CL-2", 200, null), order("EMS-ORD-1", "CL-1", 100, 1_012_500L)),
            ExportTemplate.DEFAULT_BLOTTER);
    String[] lines = csv.split("\n");
    assertThat(lines[0])
        .isEqualTo(
            "order_id,client_order_id,figi,side,qty,price,cum_qty,leaves_qty,state,account,tif");
    assertThat(lines[1]).startsWith("EMS-ORD-1,CL-1,").contains(",101.25,");
    assertThat(lines[2]).startsWith("EMS-ORD-2,CL-2,").contains(",200,,"); // market: blank price
    assertThat(lines).hasSize(3);
  }

  @Test
  void toCsv_isDeterministic_regardlessOfInputOrder() {
    StagedOrder a = order("EMS-ORD-1", "CL-1", 100, null);
    StagedOrder b = order("EMS-ORD-2", "CL-2", 200, null);
    assertThat(BlotterExporter.toCsv(List.of(a, b), ExportTemplate.DEFAULT_BLOTTER))
        .isEqualTo(BlotterExporter.toCsv(List.of(b, a), ExportTemplate.DEFAULT_BLOTTER));
  }

  @Test
  void toCsv_customTemplate_subsetAndOrder() {
    ExportTemplate template = new ExportTemplate("eod", List.of("client_order_id", "qty", "state"));
    String csv = BlotterExporter.toCsv(List.of(order("EMS-ORD-1", "CL-1", 100, null)), template);
    assertThat(csv).isEqualTo("client_order_id,qty,state\nCL-1,100,NEW\n");
  }

  @Test
  void toCsv_quotesFieldsContainingCommas() {
    String csv =
        BlotterExporter.toCsv(
            List.of(order("EMS-ORD-1", "CL,WITH,COMMAS", 100, null)),
            new ExportTemplate("q", List.of("client_order_id")));
    assertThat(csv).isEqualTo("client_order_id\n\"CL,WITH,COMMAS\"\n");
  }

  @Test
  void template_unknownColumn_failsFast() {
    assertThatThrownBy(() -> new ExportTemplate("bad", List.of("not_a_field")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not_a_field");
  }

  @Test
  void fileName_followsConvention() {
    assertThat(ExportTemplate.fileName("firm-a", "blotter", "2026-06-10"))
        .isEqualTo("firm-a_blotter_2026-06-10.csv");
  }

  private static StagedOrder order(String orderId, String clOrdId, long qty, @Nullable Long price) {
    OrderFsmContext ctx =
        new OrderFsmContext(
            orderId,
            clOrdId,
            null,
            "BBG000BLNNH6",
            1,
            qty,
            price,
            0L,
            qty,
            "acc-1",
            0,
            clOrdId,
            orderId,
            1L,
            null,
            null);
    return new StagedOrder(
        orderId, clOrdId, 1L, OrderFsmState.NEW, ctx, OrderSubState.NEW, Set.of(), 0L);
  }
}
