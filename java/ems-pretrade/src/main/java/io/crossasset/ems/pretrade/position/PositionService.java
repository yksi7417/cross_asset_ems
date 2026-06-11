/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.position;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Position projection service (task 10.7, arch-position-service.md): a pure fold of fill events
 * into per-(account, instrument) positions under the weighted-average-cost model. Single-writer per
 * key (mutations serialize on the key's record); positions change only by fills and busts.
 *
 * <p>Busts re-derive the key's position from the surviving fill set through the same fold —
 * realized P&L and average cost recompute exactly, and a "closed" position can legitimately regress
 * to open (arch § Edge cases). The fold is deterministic, so replaying the same fills yields
 * byte-identical positions.
 *
 * <p>Unrealized P&L is computed at read time from a caller-supplied mark (the mark engine /
 * quote-server integration arrives with the market-data SPI; the projection itself never stores
 * marks). Corporate-action adjustments ride a separate stream (arch-corporate-actions) and are out
 * of this slice; event-log subscription wiring lands with the projection-framework integration.
 */
public final class PositionService {

  /** One applied fill; side uses FIX tag 54 (1 = buy, 2 = sell). */
  public record Fill(
      String execId, String account, String figi, int side, long qty, long px, long eventId) {
    public Fill {
      Objects.requireNonNull(execId, "execId");
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(figi, "figi");
      if (qty <= 0) {
        throw new IllegalArgumentException("qty must be > 0");
      }
      if (side != 1 && side != 2) {
        throw new IllegalArgumentException("side must be 1 (buy) or 2 (sell)");
      }
    }
  }

  private static final class Book {
    final List<Fill> fills = new ArrayList<>();
    Position position;

    Book(String account, String figi) {
      this.position = Position.flat(account, figi);
    }
  }

  private final ConcurrentHashMap<String, Book> books = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> execIdToKey = new ConcurrentHashMap<>();

  /** Apply one fill. Duplicate execIds are rejected (fills are idempotent by exec ID). */
  public Position applyFill(Fill fill) {
    String key = key(fill.account(), fill.figi());
    if (execIdToKey.putIfAbsent(fill.execId(), key) != null) {
      throw new IllegalArgumentException("execId already applied: " + fill.execId());
    }
    Book book = books.computeIfAbsent(key, k -> new Book(fill.account(), fill.figi()));
    synchronized (book) {
      book.fills.add(fill);
      book.position = fold(book.position, fill);
      return book.position;
    }
  }

  /**
   * Bust a previously applied fill (arch-fix-appendix-d TradeBust): the surviving fill set re-folds
   * from flat, exactly re-deriving avg cost and realized P&L. Returns the recomputed position, or
   * empty if the execId is unknown.
   */
  public java.util.Optional<Position> applyBust(String execId) {
    String key = execIdToKey.remove(execId);
    if (key == null) {
      return java.util.Optional.empty();
    }
    Book book = books.get(key);
    synchronized (book) {
      book.fills.removeIf(f -> f.execId().equals(execId));
      Position rebuilt = Position.flat(book.position.account(), book.position.figi());
      for (Fill fill : book.fills) {
        rebuilt = fold(rebuilt, fill);
      }
      book.position = rebuilt;
      return java.util.Optional.of(rebuilt);
    }
  }

  /** Current position; {@code markPx} (nullable) prices the unrealized leg at read time. */
  public Position position(String account, String figi, @Nullable Long markPx) {
    Book book = books.get(key(account, figi));
    if (book == null) {
      return Position.flat(account, figi);
    }
    synchronized (book) {
      return withMark(book.position, markPx);
    }
  }

  /**
   * Every traded book of an account — including flat ones (task 18.7: a closed-out name still
   * carries the day's realized P&L). Ordered by instrument.
   */
  public List<Position> tradedPositionsForAccount(String account) {
    List<Position> out = new ArrayList<>();
    for (Book book : books.values()) {
      synchronized (book) {
        if (book.position.account().equals(account)) {
          out.add(book.position);
        }
      }
    }
    out.sort(java.util.Comparator.comparing(Position::figi));
    return out;
  }

  /** All non-flat positions of an account, ordered by instrument. */
  public List<Position> positionsForAccount(String account) {
    List<Position> out = new ArrayList<>();
    for (Book book : books.values()) {
      synchronized (book) {
        if (book.position.account().equals(account) && book.position.netQty() != 0) {
          out.add(book.position);
        }
      }
    }
    out.sort(java.util.Comparator.comparing(Position::figi));
    return out;
  }

  // ── the fold ─────────────────────────────────────────────────────────────────

  /** Weighted-average-cost fold of one fill into a position. */
  private static Position fold(Position p, Fill fill) {
    long signedQty = fill.side() == 1 ? fill.qty() : -fill.qty();
    long net = p.netQty();
    long avg = p.avgCost();
    long realized = p.realizedPnl();

    if (net == 0 || Long.signum(net) == Long.signum(signedQty)) {
      // Opening or adding: re-weight the average cost.
      long total = Math.abs(net) + fill.qty();
      avg = Math.round(((double) avg * Math.abs(net) + (double) fill.px() * fill.qty()) / total);
      net += signedQty;
    } else {
      // Closing (and possibly flipping through zero).
      long closing = Math.min(fill.qty(), Math.abs(net));
      realized += closing * (net > 0 ? fill.px() - avg : avg - fill.px());
      long remainder = fill.qty() - closing;
      net += signedQty;
      if (remainder > 0) {
        avg = fill.px(); // flipped: the surviving side opened at this fill's price
      } else if (net == 0) {
        avg = 0;
      }
    }

    return new Position(
        p.account(),
        p.figi(),
        Math.max(net, 0),
        Math.max(-net, 0),
        net,
        avg,
        realized,
        null,
        fill.eventId());
  }

  private static Position withMark(Position p, @Nullable Long markPx) {
    if (markPx == null || p.netQty() == 0) {
      return p;
    }
    long unrealized = p.netQty() * (markPx - p.avgCost());
    return new Position(
        p.account(),
        p.figi(),
        p.longQty(),
        p.shortQty(),
        p.netQty(),
        p.avgCost(),
        p.realizedPnl(),
        unrealized,
        p.lastFillEventId());
  }

  private static String key(String account, String figi) {
    return account + "|" + figi;
  }
}
