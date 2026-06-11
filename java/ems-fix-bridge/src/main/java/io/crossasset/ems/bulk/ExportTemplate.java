/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import java.util.List;
import java.util.Objects;

/**
 * A firm export template (task 8.7): which canonical columns appear, in which order, plus the
 * templated filename convention {@code {firm}_{domain}_{period}.csv} per arch-bulk-io.md § Outbound
 * delivery. Columns are a subset of the domain's canonical field set; unknown names fail fast at
 * construction so a bad template never produces a silently-empty column.
 */
public record ExportTemplate(String name, List<String> columns) {

  /** Canonical order-blotter fields available to templates. */
  public static final List<String> BLOTTER_FIELDS =
      List.of(
          "order_id",
          "client_order_id",
          "figi",
          "side",
          "qty",
          "price",
          "cum_qty",
          "leaves_qty",
          "state",
          "account",
          "tif");

  /** The default firm blotter template: every canonical field. */
  public static final ExportTemplate DEFAULT_BLOTTER =
      new ExportTemplate("blotter-default", BLOTTER_FIELDS);

  public ExportTemplate {
    Objects.requireNonNull(name, "name");
    columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
    if (columns.isEmpty()) {
      throw new IllegalArgumentException("Template needs at least one column");
    }
    for (String column : columns) {
      if (!BLOTTER_FIELDS.contains(column)) {
        throw new IllegalArgumentException("Unknown template column: " + column);
      }
    }
  }

  /** Templated export filename: {@code {firm}_{domain}_{period}.csv}. */
  public static String fileName(String firm, String domain, String period) {
    return firm + "_" + domain + "_" + period + ".csv";
  }
}
