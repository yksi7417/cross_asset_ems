/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal deterministic CSV reader (task 8.6). Header + rows, RFC-4180-style double-quoted fields
 * (embedded commas/quotes), every cell read as a trimmed string — never as a float, per
 * arch-bulk-io.md § Cell precision. Blank lines are skipped. No I/O, no clock, no locale
 * dependence: a pure function of the file text, as replay determinism requires.
 */
public final class CsvTable {

  private final List<String> header;
  private final List<List<String>> rows;

  private CsvTable(List<String> header, List<List<String>> rows) {
    this.header = header;
    this.rows = rows;
  }

  public static CsvTable parse(String text) {
    List<List<String>> records = new ArrayList<>();
    for (String line : text.split("\r?\n")) {
      if (line.isBlank()) {
        continue;
      }
      records.add(splitLine(line));
    }
    if (records.isEmpty()) {
      return new CsvTable(List.of(), List.of());
    }
    List<String> header =
        records.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
    return new CsvTable(header, records.subList(1, records.size()));
  }

  private static List<String> splitLine(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          cell.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        cells.add(cell.toString().trim());
        cell.setLength(0);
      } else {
        cell.append(c);
      }
    }
    cells.add(cell.toString().trim());
    return cells;
  }

  /** Lower-cased, trimmed header cells. */
  public List<String> header() {
    return header;
  }

  /** Data rows (header excluded), cells trimmed. */
  public List<List<String>> rows() {
    return rows;
  }

  /** Cell at (row, headerIndex), or empty string when the row is ragged-short. */
  public String cell(int rowIndex, int columnIndex) {
    List<String> row = rows.get(rowIndex);
    return columnIndex < row.size() ? row.get(columnIndex) : "";
  }
}
