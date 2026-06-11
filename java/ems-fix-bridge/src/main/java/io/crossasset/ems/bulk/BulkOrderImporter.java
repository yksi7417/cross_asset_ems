/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import io.crossasset.ems.api.ApiItem;
import io.crossasset.ems.api.ApiOperation;
import io.crossasset.ems.api.ApiRequest;
import io.crossasset.ems.api.ApiResponse;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.ItemResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * CSV bulk order import (task 8.6, ORDERS domain v1 of arch-bulk-io.md): parse → header/alias match
 * → per-cell type coercion → assemble one {@code stage_orders} batch → submit through the {@link
 * ApiSurface} → per-row results in file order.
 *
 * <p>Schema (ORDERS v1): required {@code client_order_id}, {@code figi}, {@code side}, {@code qty},
 * {@code account}; optional {@code price} (blank = market), {@code tif} (blank = DAY). Common
 * header variants map via the alias table. Missing required columns reject the whole file;
 * cell-level coercion failures reject only their row (with the cell named) while the rest of the
 * batch proceeds — standard partial-success semantics.
 *
 * <p>Idempotency rides the API envelope: the {@code uploadId} is the batch {@code requestId}, so
 * re-importing the same file with the same uploadId returns the original result without re-staging
 * (verified end-to-end by task 8.8). XLSX bindings and the FILLS/ALLOCATIONS/REFDATA domains follow
 * as separate slices; this importer is the engine's order path.
 */
public final class BulkOrderImporter {

  private static final Map<String, String> COLUMN_ALIASES = new HashMap<>();

  static {
    alias("client_order_id", "client_order_id", "clordid", "cl_ord_id", "order_id", "clientid");
    alias("figi", "figi", "security_id", "securityid", "id_bbg", "bbgid", "instrument");
    alias("side", "side", "buy_sell", "buysell", "b_s", "direction");
    alias("qty", "qty", "quantity", "order_qty", "orderqty", "size", "amount");
    alias("account", "account", "acct", "account_id");
    alias("price", "price", "limit_price", "limitprice", "px", "limit");
    alias("tif", "tif", "time_in_force", "timeinforce");
  }

  private static void alias(String canonical, String... variants) {
    for (String variant : variants) {
      COLUMN_ALIASES.put(variant, canonical);
    }
  }

  private static final List<String> REQUIRED =
      List.of("client_order_id", "figi", "side", "qty", "account");

  private final ApiSurface api;

  public BulkOrderImporter(ApiSurface api) {
    this.api = Objects.requireNonNull(api, "api");
  }

  /**
   * Import one CSV file's text as a {@code stage_orders} batch on the caller's session. {@code
   * uploadId} doubles as the API request ID (idempotency key); {@code sessionSeq} is the caller's
   * next channel sequence.
   */
  public UploadResult importCsv(String uploadId, long sessionId, long sessionSeq, String csvText) {
    CsvTable table = CsvTable.parse(csvText);
    if (table.header().isEmpty()) {
      return UploadResult.fileRejected(uploadId, "File is empty.");
    }

    Map<String, Integer> columns = new HashMap<>();
    for (int i = 0; i < table.header().size(); i++) {
      String canonical = COLUMN_ALIASES.get(table.header().get(i));
      if (canonical != null) {
        columns.putIfAbsent(canonical, i);
      }
    }
    List<String> missing = REQUIRED.stream().filter(c -> !columns.containsKey(c)).toList();
    if (!missing.isEmpty()) {
      return UploadResult.fileRejected(uploadId, "Missing required column(s): " + missing + ".");
    }

    // Coerce rows; collect local failures and the API batch (with its row mapping) in one pass.
    int rowCount = table.rows().size();
    UploadResult.RowResult[] rowResults = new UploadResult.RowResult[rowCount];
    List<ApiItem> items = new ArrayList<>();
    List<Integer> itemRowIndex = new ArrayList<>();
    for (int row = 0; row < rowCount; row++) {
      try {
        items.add(
            new ApiItem.StageOrder(
                requireCell(table, row, columns.get("client_order_id"), "client_order_id"),
                requireCell(table, row, columns.get("figi"), "figi"),
                TypeCoercion.side(table.cell(row, columns.get("side"))),
                TypeCoercion.qty(table.cell(row, columns.get("qty"))),
                price(table, row, columns.get("price")),
                requireCell(table, row, columns.get("account"), "account"),
                tif(table, row, columns.get("tif"))));
        itemRowIndex.add(row);
      } catch (TypeCoercion.CoercionException e) {
        rowResults[row] =
            new UploadResult.RowResult(
                row, false, null, "EMS-ORD-1001", "row " + (row + 2) + ": " + e.getMessage());
      }
    }

    if (!items.isEmpty()) {
      ApiResponse response =
          api.execute(
              new ApiRequest(uploadId, sessionId, sessionSeq, ApiOperation.STAGE_ORDERS, items));
      for (int i = 0; i < response.results().size(); i++) {
        ItemResult result = response.results().get(i);
        int row = itemRowIndex.get(i);
        rowResults[row] =
            new UploadResult.RowResult(
                row,
                result.status() == ItemResult.Status.ACCEPTED,
                result.refId(),
                result.errorCode(),
                result.errorMessage());
      }
    }

    List<UploadResult.RowResult> rows = new ArrayList<>(rowCount);
    int accepted = 0;
    int rejected = 0;
    for (int row = 0; row < rowCount; row++) {
      UploadResult.RowResult r = rowResults[row];
      if (r == null) {
        r = new UploadResult.RowResult(row, false, null, "EMS-ORD-1001", "row not processed");
      }
      rows.add(r);
      if (r.ok()) {
        accepted++;
      } else {
        rejected++;
      }
    }
    return new UploadResult(uploadId, null, rows, accepted, rejected);
  }

  private static String requireCell(CsvTable table, int row, int column, String name) {
    String value = TypeCoercion.text(table.cell(row, column));
    if (value.isEmpty()) {
      throw new TypeCoercion.CoercionException(name + " is blank");
    }
    return value;
  }

  private static @Nullable Long price(CsvTable table, int row, @Nullable Integer column) {
    return column == null ? null : TypeCoercion.price(table.cell(row, column));
  }

  private static int tif(CsvTable table, int row, @Nullable Integer column) {
    return column == null ? 0 : TypeCoercion.tif(table.cell(row, column));
  }
}
