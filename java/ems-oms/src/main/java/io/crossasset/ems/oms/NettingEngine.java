/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Optional;

/**
 * Collapses opposing FX orders sharing a netting key into fewer market-facing residual parents
 * while preserving per-child accountability. The key is (figi, ccy_pair, value_date, account_group,
 * pac): prime brokers isolate because settlement obligations are PB-specific; value dates isolate
 * (same pair, different date is a swap — arch-multileg); a set PAC forms its own sub-bucket.
 *
 * <p>The residual parent is a real {@link StagedOrder} the router routes unchanged. As parent fills
 * arrive ({@link #allocateNetFill}), the residual side books pro-rata and the matched portion
 * crosses internally at the same execution rate, so both sides complete exactly when the parent
 * does. Net-to-zero buckets have no parent — the whole match books in one shot at a caller-supplied
 * cross rate ({@link #bookInternalCross}, e.g. a WM fix).
 *
 * <p>Per arch-fx-netting.md, task 7.6. Out of scope for v1 (documented for the next slice):
 * swap-aware leg-walking netting (needs 7.4 leg structures wired to FX swap instruments),
 * end-of-day value-date rolls (tradedate-roll), spot-days/holiday value-date arithmetic (callers
 * pass computed dates), and the firm/desk/tag policy cascade (callers pass resolved policy).
 */
public interface NettingEngine {

  /**
   * Buckets candidates by netting key and collapses every bucket containing both sides. Single-side
   * buckets and {@code doNotNet} candidates pass through untouched. Failures reject the whole
   * request with no state change: EMS-ORD-4001 unknown child, EMS-ORD-3003 child not READY /
   * already in a group / duplicate, EMS-PRM-1601 cross-session, EMS-ORD-2203 net-to-zero blocked,
   * EMS-ORD-2201 post-net validation failure (already-formed groups are unwound).
   */
  NetResult net(NettingRequest request);

  /**
   * Books one residual-parent fill back to the children: residual-side children book pro-rata of
   * the fill, matched qty crosses at the same price scaled by execution progress; the final fill
   * force-completes every child exactly. EMS-ORD-3003 when the fill exceeds the residual remainder
   * or the group is net-to-zero.
   */
  NettingEventResult allocateNetFill(String groupId, long fillQty, long fillPx);

  /**
   * Books a net-to-zero group's internal cross in full at the supplied rate. EMS-ORD-3003 when the
   * group has a market-facing residual or was already booked.
   */
  NettingEventResult bookInternalCross(String groupId, long crossPx);

  /**
   * Dissolves an unfilled group (EMS-ORD-2210 once fills, cross bookings, or a routed parent
   * exist): cancels the residual parent and frees the children for re-netting.
   */
  NettingEventResult unnet(String groupId, long sessionId);

  /** Returns the group if it exists, empty otherwise. */
  Optional<NetGroup> findGroup(String groupId);
}
