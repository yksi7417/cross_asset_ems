/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.borrow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Borrow / locate service (task 18.6, arch-borrow-service.md): the EMS's accounting of share
 * locates and live borrow positions behind Reg SHO short-sale gating. Pre-trade {@link #locate}
 * reserves availability (internal book first, then external lenders by best rate) and issues the
 * documented locate Rule 203(b) requires; fills convert locates to borrow positions; recalls start
 * a forced-cover clock; per-day cost accrues onto open borrows. The daily {@link
 * #regShoAttestation} summarizes it all for ops (and the 15c3-5 evidence pack, 18.5).
 *
 * <p>Deterministic: callers supply timestamps; IDs are sequences. Hard-to-borrow names carry a flag
 * + punitive rate; gating of HTB shorting by trader tag happens in the compliance check, not here.
 */
public final class BorrowService {

  /** The internal book's lender key — always consulted first. */
  public static final String INTERNAL = "internal";

  /** One lender's availability for one instrument. */
  public record Availability(String figi, String lender, long availableQty, long rateBp) {}

  /** A documented locate (the Reg SHO affirmative determination). */
  public record LocateRecord(
      String locateId,
      String figi,
      long qty,
      long rateBp,
      String lender,
      String requestedBy,
      long locatedAtMillis,
      long expiresAtMillis,
      Status status) {
    public enum Status {
      ACTIVE,
      CONSUMED,
      EXPIRED,
      CANCELLED
    }

    LocateRecord with(Status newStatus) {
      return new LocateRecord(
          locateId,
          figi,
          qty,
          rateBp,
          lender,
          requestedBy,
          locatedAtMillis,
          expiresAtMillis,
          newStatus);
    }
  }

  /** Locate outcome. */
  public sealed interface LocateResult {
    record Located(LocateRecord locate) implements LocateResult {}

    record NotLocated(String reason) implements LocateResult {}
  }

  /** A live borrow. */
  public record BorrowPosition(
      String positionId,
      String figi,
      long qty,
      String lender,
      long rateBp,
      long sinceMillis,
      Status status,
      long coverDeadlineMillis,
      long accruedCost) {
    public enum Status {
      OPEN,
      RECALLED,
      RETURNED
    }
  }

  /** Daily Reg SHO attestation summary (arch § Reg SHO interactions). */
  public record RegShoAttestation(
      long asOfMillis,
      int locatesActive,
      int locatesConsumed,
      int locatesExpired,
      int openBorrows,
      int recalledPendingCover,
      List<String> thresholdSecuritiesWithOpenBorrows) {}

  /** Recall forced-cover window: T+3 for US equities per the arch note. */
  private static final long COVER_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000;

  private final long locateTtlMillis;
  private final Map<String, Map<String, Availability>> availabilityByFigi = new LinkedHashMap<>();
  private final Map<String, LocateRecord> locates = new LinkedHashMap<>();
  private final Map<String, BorrowPosition> positions = new LinkedHashMap<>();
  private final Map<String, Boolean> hardToBorrow = new LinkedHashMap<>();
  private final Map<String, Boolean> thresholdList = new LinkedHashMap<>();
  private final AtomicLong locateSeq = new AtomicLong(1);
  private final AtomicLong positionSeq = new AtomicLong(1);

  /** {@code locateTtlMillis}: locates not consumed within this window expire (intraday). */
  public BorrowService(long locateTtlMillis) {
    if (locateTtlMillis <= 0) {
      throw new IllegalArgumentException("locateTtlMillis must be positive");
    }
    this.locateTtlMillis = locateTtlMillis;
  }

  // ── Availability (lender feeds + internal book) ──────────────────────────────

  /** Upsert one lender's availability for an instrument. */
  public synchronized void recordAvailability(
      String figi, String lender, long availableQty, long rateBp) {
    availabilityByFigi
        .computeIfAbsent(figi, k -> new LinkedHashMap<>())
        .put(lender, new Availability(figi, lender, availableQty, rateBp));
  }

  public synchronized List<Availability> availability(String figi) {
    Map<String, Availability> byLender = availabilityByFigi.get(figi);
    return byLender == null ? List.of() : List.copyOf(byLender.values());
  }

  /** Flag a name hard-to-borrow (punitive rates, tag-gated shorting in compliance). */
  public synchronized void setHardToBorrow(String figi, boolean htb) {
    hardToBorrow.put(figi, htb);
  }

  public synchronized boolean isHardToBorrow(String figi) {
    return hardToBorrow.getOrDefault(figi, false);
  }

  /** Reg SHO threshold-securities list membership. */
  public synchronized void setThresholdSecurity(String figi, boolean onList) {
    thresholdList.put(figi, onList);
  }

  public synchronized boolean isThresholdSecurity(String figi) {
    return thresholdList.getOrDefault(figi, false);
  }

  // ── Locate (pre-trade) ───────────────────────────────────────────────────────

  /**
   * Source a locate: internal book first, then external lenders by ascending rate; a single locate
   * may span lenders only if no single source suffices — v1 takes the first source that covers the
   * full qty (split locates arrive with real lender integration). Reserves availability.
   */
  public synchronized LocateResult locate(
      String figi, long qty, String requestedBy, long nowMillis) {
    if (qty <= 0) {
      return new LocateResult.NotLocated("locate qty must be positive");
    }
    Map<String, Availability> byLender = availabilityByFigi.get(figi);
    if (byLender == null || byLender.isEmpty()) {
      return new LocateResult.NotLocated("no lender availability for " + figi);
    }
    List<Availability> sources = new ArrayList<>(byLender.values());
    sources.sort(
        Comparator.<Availability>comparingInt(a -> INTERNAL.equals(a.lender()) ? 0 : 1)
            .thenComparingLong(Availability::rateBp));
    for (Availability source : sources) {
      if (source.availableQty() >= qty) {
        byLender.put(
            source.lender(),
            new Availability(figi, source.lender(), source.availableQty() - qty, source.rateBp()));
        LocateRecord locate =
            new LocateRecord(
                "LOC-" + locateSeq.getAndIncrement(),
                figi,
                qty,
                source.rateBp(),
                source.lender(),
                requestedBy,
                nowMillis,
                nowMillis + locateTtlMillis,
                LocateRecord.Status.ACTIVE);
        locates.put(locate.locateId(), locate);
        return new LocateResult.Located(locate);
      }
    }
    return new LocateResult.NotLocated(
        "insufficient availability for "
            + qty
            + " "
            + figi
            + " across "
            + sources.size()
            + " source(s)");
  }

  public synchronized Optional<LocateRecord> findLocate(String locateId) {
    return Optional.ofNullable(locates.get(locateId));
  }

  /** Cancel an ACTIVE locate, returning its qty to the lender's availability. */
  public synchronized boolean cancelLocate(String locateId) {
    LocateRecord locate = locates.get(locateId);
    if (locate == null || locate.status() != LocateRecord.Status.ACTIVE) {
      return false;
    }
    locates.put(locateId, locate.with(LocateRecord.Status.CANCELLED));
    restoreAvailability(locate);
    return true;
  }

  /** Expire ACTIVE locates past their TTL, restoring availability. Returns how many expired. */
  public synchronized int releaseExpired(long nowMillis) {
    int expired = 0;
    for (LocateRecord locate : List.copyOf(locates.values())) {
      if (locate.status() == LocateRecord.Status.ACTIVE && nowMillis >= locate.expiresAtMillis()) {
        locates.put(locate.locateId(), locate.with(LocateRecord.Status.EXPIRED));
        restoreAvailability(locate);
        expired++;
      }
    }
    return expired;
  }

  private void restoreAvailability(LocateRecord locate) {
    Map<String, Availability> byLender =
        availabilityByFigi.computeIfAbsent(locate.figi(), k -> new LinkedHashMap<>());
    Availability current = byLender.get(locate.lender());
    long base = current == null ? 0 : current.availableQty();
    long rate = current == null ? locate.rateBp() : current.rateBp();
    byLender.put(
        locate.lender(),
        new Availability(locate.figi(), locate.lender(), base + locate.qty(), rate));
  }

  // ── Borrow lifecycle (trade / post-trade) ────────────────────────────────────

  /** A short fill executes the borrow: the locate is consumed and a position opens. */
  public synchronized Optional<BorrowPosition> borrowExecuted(String locateId, long nowMillis) {
    LocateRecord locate = locates.get(locateId);
    if (locate == null || locate.status() != LocateRecord.Status.ACTIVE) {
      return Optional.empty();
    }
    locates.put(locateId, locate.with(LocateRecord.Status.CONSUMED));
    BorrowPosition position =
        new BorrowPosition(
            "BRW-" + positionSeq.getAndIncrement(),
            locate.figi(),
            locate.qty(),
            locate.lender(),
            locate.rateBp(),
            nowMillis,
            BorrowPosition.Status.OPEN,
            0,
            0);
    positions.put(position.positionId(), position);
    return Optional.of(position);
  }

  /** Short position covered — shares returned to the lender. */
  public synchronized boolean returnBorrow(String positionId) {
    BorrowPosition position = positions.get(positionId);
    if (position == null || position.status() == BorrowPosition.Status.RETURNED) {
      return false;
    }
    positions.put(
        positionId,
        new BorrowPosition(
            position.positionId(),
            position.figi(),
            position.qty(),
            position.lender(),
            position.rateBp(),
            position.sinceMillis(),
            BorrowPosition.Status.RETURNED,
            position.coverDeadlineMillis(),
            position.accruedCost()));
    return true;
  }

  /**
   * Lender recall: tries to roll to another lender with availability; if none, the position is
   * RECALLED with a T+3 forced-cover deadline and lands on the ops queue.
   */
  public synchronized Optional<BorrowPosition> recall(String positionId, long nowMillis) {
    BorrowPosition position = positions.get(positionId);
    if (position == null || position.status() != BorrowPosition.Status.OPEN) {
      return Optional.empty();
    }
    Map<String, Availability> byLender = availabilityByFigi.get(position.figi());
    if (byLender != null) {
      for (Availability source : byLender.values()) {
        if (!source.lender().equals(position.lender()) && source.availableQty() >= position.qty()) {
          // Roll: same borrow, new lender, lender's availability reserved.
          byLender.put(
              source.lender(),
              new Availability(
                  position.figi(),
                  source.lender(),
                  source.availableQty() - position.qty(),
                  source.rateBp()));
          BorrowPosition rolled =
              new BorrowPosition(
                  position.positionId(),
                  position.figi(),
                  position.qty(),
                  source.lender(),
                  source.rateBp(),
                  position.sinceMillis(),
                  BorrowPosition.Status.OPEN,
                  0,
                  position.accruedCost());
          positions.put(positionId, rolled);
          return Optional.of(rolled);
        }
      }
    }
    BorrowPosition recalled =
        new BorrowPosition(
            position.positionId(),
            position.figi(),
            position.qty(),
            position.lender(),
            position.rateBp(),
            position.sinceMillis(),
            BorrowPosition.Status.RECALLED,
            nowMillis + COVER_WINDOW_MILLIS,
            position.accruedCost());
    positions.put(positionId, recalled);
    return Optional.of(recalled);
  }

  /** Positions awaiting forced cover (the recall ops queue). */
  public synchronized List<BorrowPosition> recallQueue() {
    List<BorrowPosition> queue = new ArrayList<>();
    for (BorrowPosition position : positions.values()) {
      if (position.status() == BorrowPosition.Status.RECALLED) {
        queue.add(position);
      }
    }
    return queue;
  }

  public synchronized Optional<BorrowPosition> findPosition(String positionId) {
    return Optional.ofNullable(positions.get(positionId));
  }

  // ── Cost accrual ─────────────────────────────────────────────────────────────

  /**
   * Accrue one day of borrow cost at the given fixed-point mark: {@code qty * markPx * rateBp /
   * 10_000 / 365}. Returns the day's cost (same fixed-point scale as the mark).
   */
  public synchronized long accrueDaily(String positionId, long markPx) {
    BorrowPosition position = positions.get(positionId);
    if (position == null || position.status() == BorrowPosition.Status.RETURNED) {
      return 0;
    }
    // qty * markPx (fixed-point) * rateBp/1e4, annualized /365 — result in the mark's scale.
    // Long math holds to ~1e16 intermediate (e.g. 1e6 shares * $200.0000 * 100% rate).
    long dailyCost = position.qty() * markPx * position.rateBp() / 10_000 / 365;
    BorrowPosition updated =
        new BorrowPosition(
            position.positionId(),
            position.figi(),
            position.qty(),
            position.lender(),
            position.rateBp(),
            position.sinceMillis(),
            position.status(),
            position.coverDeadlineMillis(),
            position.accruedCost() + dailyCost);
    positions.put(positionId, updated);
    return dailyCost;
  }

  // ── Reg SHO attestation ──────────────────────────────────────────────────────

  /** Daily ops attestation: locate + borrow accounting and threshold exposure. */
  public synchronized RegShoAttestation regShoAttestation(long asOfMillis) {
    int active = 0;
    int consumed = 0;
    int expired = 0;
    for (LocateRecord locate : locates.values()) {
      switch (locate.status()) {
        case ACTIVE -> active++;
        case CONSUMED -> consumed++;
        case EXPIRED -> expired++;
        case CANCELLED -> {}
      }
    }
    int open = 0;
    int recalled = 0;
    List<String> thresholdExposure = new ArrayList<>();
    for (BorrowPosition position : positions.values()) {
      if (position.status() == BorrowPosition.Status.OPEN) {
        open++;
        if (isThresholdSecurity(position.figi()) && !thresholdExposure.contains(position.figi())) {
          thresholdExposure.add(position.figi());
        }
      } else if (position.status() == BorrowPosition.Status.RECALLED) {
        recalled++;
      }
    }
    return new RegShoAttestation(
        asOfMillis, active, consumed, expired, open, recalled, List.copyOf(thresholdExposure));
  }
}
