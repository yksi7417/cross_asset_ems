/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import io.crossasset.ems.oms.StagedOrder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Order-blotter CSV export (task 8.7). Renders a snapshot of staged orders under an {@link
 * ExportTemplate}: header row from the template, one row per order sorted by order ID
 * (deterministic — same snapshot, same bytes, per arch-bulk-io.md § Replay determinism), prices
 * rendered from 4dp fixed-point to plain decimals, RFC-4180 quoting where needed.
 *
 * <p>The caller supplies the orders (the projection layer owns queries; the exporter owns
 * rendering). Identity-scoped filtering happens before this call — exports are always
 * permission-scoped at the row level.
 */
public final class BlotterExporter {

  private BlotterExporter() {}

  /** Render the snapshot to CSV text under the given template. */
  public static String toCsv(List<StagedOrder> orders, ExportTemplate template) {
    StringBuilder out = new StringBuilder();
    out.append(String.join(",", template.columns())).append('\n');
    List<StagedOrder> sorted = new ArrayList<>(orders);
    sorted.sort(Comparator.comparing(StagedOrder::orderId));
    for (StagedOrder order : sorted) {
      List<String> cells = new ArrayList<>(template.columns().size());
      for (String column : template.columns()) {
        cells.add(quote(field(order, column)));
      }
      out.append(String.join(",", cells)).append('\n');
    }
    return out.toString();
  }

  private static String field(StagedOrder order, String column) {
    var ctx = order.fsmContext();
    return switch (column) {
      case "order_id" -> order.orderId();
      case "client_order_id" -> order.clOrdId();
      case "figi" -> ctx.instrumentId();
      case "side" -> ctx.side() == 1 ? "BUY" : "SELL";
      case "qty" -> Long.toString(ctx.orderQty());
      case "price" -> ctx.price() == null ? "" : renderPrice(ctx.price());
      case "cum_qty" -> Long.toString(ctx.cumQty());
      case "leaves_qty" -> Long.toString(ctx.leavesQty());
      case "state" -> order.fsmState().name();
      case "account" -> ctx.account();
      case "tif" -> Integer.toString(ctx.tif());
      default -> throw new IllegalArgumentException("Unknown column: " + column);
    };
  }

  /** 4dp fixed-point → plain decimal with trailing zeros trimmed ({@code 1012500 → 101.25}). */
  private static String renderPrice(long scaled) {
    return BigDecimal.valueOf(scaled, 4).stripTrailingZeros().toPlainString();
  }

  private static String quote(String cell) {
    if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
      return '"' + cell.replace("\"", "\"\"") + '"';
    }
    return cell;
  }
}
