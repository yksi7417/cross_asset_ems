/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.sor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Price-time sweep (task 11.11, the Reg-NMS-shaped baseline): route across venues in order of best
 * displayed price (then displayed size), taking displayed liquidity at each level until the qty is
 * done — never trading through a better-priced venue. A limit px caps how deep the sweep walks;
 * residual past the displayed book posts to the venue that showed the best price.
 */
public final class SweepStrategy implements SorStrategy {

  @Override
  public String id() {
    return "sweep";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public CascadePlan decide(RouteIntent intent, MarketContext context) {
    boolean buy = intent.side() == 1;
    // Best price first (lowest ask for buys / highest bid for sells), then deepest size.
    List<VenueQuote> venues = new ArrayList<>(context.quotes());
    venues.sort(
        Comparator.comparingLong((VenueQuote q) -> buy ? q.ask() : -q.bid())
            .thenComparing(q -> -(buy ? q.askSize() : q.bidSize()))
            .thenComparing(VenueQuote::venue));

    List<ChildRoute> children = new ArrayList<>();
    StringBuilder rationale = new StringBuilder("price-time sweep: ");
    long remaining = intent.qty();
    for (VenueQuote quote : venues) {
      long px = buy ? quote.ask() : quote.bid();
      long size = buy ? quote.askSize() : quote.bidSize();
      if (remaining <= 0 || size <= 0 || px <= 0) {
        continue;
      }
      if (intent.limitPx() != null && (buy ? px > intent.limitPx() : px < intent.limitPx())) {
        rationale.append(quote.venue()).append(" beyond limit, stop; ");
        break; // venues are price-sorted: everything further is worse
      }
      long take = Math.min(remaining, size);
      children.add(new ChildRoute(quote.venue(), "", "DMA", take, px, 0, Map.of()));
      rationale.append(take).append('@').append(quote.venue()).append(' ');
      remaining -= take;
    }
    if (remaining > 0 && !children.isEmpty()) {
      // Residual posts at the best venue at the limit (or its displayed px).
      ChildRoute best = children.get(0);
      children.add(
          new ChildRoute(
              best.venue(),
              "",
              "POST",
              remaining,
              intent.limitPx() != null ? intent.limitPx() : best.px(),
              0,
              Map.of()));
      rationale.append("residual ").append(remaining).append(" posts @").append(best.venue());
    }
    return new CascadePlan(children, rationale.toString().trim());
  }
}
