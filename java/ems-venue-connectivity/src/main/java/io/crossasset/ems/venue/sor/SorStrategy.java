/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.sor;

import java.util.List;
import java.util.Map;

/**
 * SOR strategy contract (task 11.11, [[arch-smart-order-router]] § Strategy abstraction): a PURE
 * decision function — route intent + market context in, cascade plan out. No I/O, no clock reads
 * (the context carries {@code nowMillis}); replay reproduces identical decisions. Strategies are
 * registered and versioned like FSMs.
 */
public interface SorStrategy {

  String id();

  int version();

  /** Decide the cascade for one route intent. */
  CascadePlan decide(RouteIntent intent, MarketContext context);

  /** The parent route the SOR is working. */
  record RouteIntent(
      String routeId, String figi, int side, long qty, Long limitPx, String account) {}

  /** One venue's displayed market (fixed-point 1e4 px). */
  record VenueQuote(String venue, long bid, long bidSize, long ask, long askSize) {}

  /** Market state at decision time. {@code nowMillis} is the injected clock. */
  record MarketContext(List<VenueQuote> quotes, long nowMillis) {}

  /** One child route of the cascade. {@code atMillis} schedules slicers (0 = immediate). */
  record ChildRoute(
      String venue,
      String broker,
      String algo,
      long qty,
      Long px,
      long atMillis,
      Map<String, String> params) {}

  /** The decision: children + the human-readable rationale the audit chain stores. */
  record CascadePlan(List<ChildRoute> children, String rationale) {
    public CascadePlan {
      children = List.copyOf(children);
    }
  }
}
