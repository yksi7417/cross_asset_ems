/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.bulk;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Spreadsheet-reality cell coercion (task 8.6, per arch-bulk-io.md § Type coercion): comma
 * thousands, k/M/B multiplier shorthand, side alias canonicalization, Excel's forced-text leading
 * apostrophe. Failures throw {@link CoercionException} carrying the reason — the importer turns
 * them into per-row, per-cell errors. Prices parse as scaled longs with 4 implied decimal places
 * (the OMS fixed-point convention).
 */
public final class TypeCoercion {

  /** Fixed-point scale for prices: 4 implied decimal places. */
  public static final long PRICE_SCALE = 10_000L;

  /** A cell that cannot be coerced; {@code reason} is shown to the uploader with the location. */
  public static final class CoercionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CoercionException(String reason) {
      super(reason);
    }
  }

  private TypeCoercion() {}

  /** Strip Excel's leading apostrophe (forced-text marker) and trim. */
  public static String text(String cell) {
    String trimmed = cell.trim();
    return trimmed.startsWith("'") ? trimmed.substring(1) : trimmed;
  }

  /** Quantity: comma thousands and k/M/B suffix shorthand; must be a positive whole number. */
  public static long qty(String cell) {
    String s = text(cell).replace(",", "");
    if (s.isEmpty()) {
      throw new CoercionException("quantity is blank");
    }
    long multiplier = 1;
    char last = Character.toUpperCase(s.charAt(s.length() - 1));
    if (last == 'K' || last == 'M' || last == 'B') {
      multiplier = last == 'K' ? 1_000L : last == 'M' ? 1_000_000L : 1_000_000_000L;
      s = s.substring(0, s.length() - 1);
    }
    try {
      BigDecimal value = new BigDecimal(s).multiply(BigDecimal.valueOf(multiplier));
      if (value.stripTrailingZeros().scale() > 0) {
        throw new CoercionException("quantity " + cell + " is not a whole number");
      }
      long qty = value.longValueExact();
      if (qty <= 0) {
        throw new CoercionException("quantity must be > 0");
      }
      return qty;
    } catch (NumberFormatException | ArithmeticException e) {
      throw new CoercionException("cannot parse quantity '" + cell + "'");
    }
  }

  /** Side: BUY/B/1 → 1, SELL/S/2 → 2 (FIX tag 54). */
  public static int side(String cell) {
    String s = text(cell).toUpperCase(Locale.ROOT);
    return switch (s) {
      case "BUY", "B", "1" -> 1;
      case "SELL", "S", "2" -> 2;
      default -> throw new CoercionException("unknown side '" + cell + "'");
    };
  }

  /** Price: decimal → scaled long (4 dp), comma thousands tolerated; blank → null (market). */
  public static Long price(String cell) {
    String s = text(cell).replace(",", "");
    if (s.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(s)
          .multiply(BigDecimal.valueOf(PRICE_SCALE))
          .setScale(0, java.math.RoundingMode.HALF_UP)
          .longValueExact();
    } catch (NumberFormatException | ArithmeticException e) {
      throw new CoercionException("cannot parse price '" + cell + "'");
    }
  }

  /** TIF: DAY/0, GTC/1, IOC/3, FOK/4 (FIX tag 59); blank → DAY. */
  public static int tif(String cell) {
    String s = text(cell).toUpperCase(Locale.ROOT);
    return switch (s) {
      case "", "DAY", "0" -> 0;
      case "GTC", "1" -> 1;
      case "IOC", "3" -> 3;
      case "FOK", "4" -> 4;
      default -> throw new CoercionException("unknown tif '" + cell + "'");
    };
  }
}
