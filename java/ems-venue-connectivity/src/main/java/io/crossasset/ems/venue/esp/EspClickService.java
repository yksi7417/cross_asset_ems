/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.esp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EMS-side click-to-trade core (task 11.17; the 18.11 desktop calls this): tracks each pair's
 * latest executable quote per venue, guards the trader's click with an EMS-side slippage check
 * <em>before</em> the dealer sees it, executes under the venue's last look, and keeps per-venue
 * last-look statistics (attempts, fills, rejects, hold time) — the awareness that feeds the
 * desktop's venue badge and the best-execution review (12.10).
 */
public final class EspClickService {

  /** A trader's click. {@code expectedPx} is the price on screen when they clicked. */
  public record ClickRequest(
      String figi, int side, long qty, long expectedPx, long maxSlippageBp, long sessionId) {}

  /** Click outcome. */
  public sealed interface ClickResult {
    record Filled(String venueMic, String quoteId, long px, long qty) implements ClickResult {}

    record Rejected(String reason, String detail) implements ClickResult {}
  }

  /** Per-venue last-look behaviour, visible to traders and best-ex. */
  public record LastLookStats(
      String venueMic, long attempts, long fills, long lastLookRejects, long totalHoldMillis) {

    /** Fill rate in basis points (10_000 = 100%). */
    public long acceptRateBp() {
      return attempts == 0 ? 0 : fills * 10_000 / attempts;
    }
  }

  /** One audited click. */
  public record ClickAudit(
      long sessionId,
      String figi,
      int side,
      long qty,
      String outcome,
      String detail,
      long atMillis) {}

  private final Map<String, EspVenue> venueByFigi = new LinkedHashMap<>();
  private final Map<String, EspQuote> latestQuote = new LinkedHashMap<>();
  private final Map<String, LastLookStats> statsByVenue = new LinkedHashMap<>();
  private final List<ClickAudit> audit = new ArrayList<>();

  /** Route a pair's clicks to a venue and start consuming its stream. */
  public synchronized void attach(String figi, EspVenue venue) {
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(venue, "venue");
    venueByFigi.put(figi, venue);
    venue.subscribe(figi, quote -> accept(figi, quote));
  }

  private synchronized void accept(String figi, EspQuote quote) {
    latestQuote.put(figi, quote);
  }

  /** The pair's current executable quote (the desktop tile's content). */
  public synchronized EspQuote quoteFor(String figi) {
    return latestQuote.get(figi);
  }

  /** Execute one click. Slippage-guarded EMS-side, then dealer last look. */
  public synchronized ClickResult click(ClickRequest request, long nowMillis) {
    EspVenue venue = venueByFigi.get(request.figi());
    EspQuote quote = latestQuote.get(request.figi());
    if (venue == null || quote == null) {
      return audited(
          request,
          new ClickResult.Rejected("NO_QUOTE", "no executable stream for pair"),
          nowMillis);
    }
    if (!quote.isLive(nowMillis)) {
      return audited(
          request,
          new ClickResult.Rejected("STALE_QUOTE", "latest quote past its TTL; wait for the next"),
          nowMillis);
    }
    long currentPx = quote.pxForSide(request.side());
    long slippageBp =
        Math.abs(currentPx - request.expectedPx()) * 10_000 / Math.max(1, request.expectedPx());
    if (slippageBp > request.maxSlippageBp()) {
      return audited(
          request,
          new ClickResult.Rejected(
              "SLIPPAGE_GUARD",
              "price moved "
                  + slippageBp
                  + "bp from the clicked "
                  + request.expectedPx()
                  + " (guard "
                  + request.maxSlippageBp()
                  + "bp); not sent to venue"),
          nowMillis);
    }

    EspVenue.EspExecutionResult result =
        venue.execute(quote.quoteId(), request.side(), request.qty(), nowMillis);
    recordStats(venue.venueMic(), result);
    if (result instanceof EspVenue.EspExecutionResult.Filled filled) {
      return audited(
          request,
          new ClickResult.Filled(venue.venueMic(), filled.quoteId(), filled.px(), filled.qty()),
          nowMillis);
    }
    EspVenue.EspExecutionResult.Rejected rejected = (EspVenue.EspExecutionResult.Rejected) result;
    return audited(
        request, new ClickResult.Rejected(rejected.reason().name(), rejected.detail()), nowMillis);
  }

  /** Last-look behaviour of one venue. */
  public synchronized LastLookStats lastLookStats(String venueMic) {
    return statsByVenue.getOrDefault(venueMic, new LastLookStats(venueMic, 0, 0, 0, 0));
  }

  public synchronized List<ClickAudit> auditJournal() {
    return List.copyOf(audit);
  }

  private void recordStats(String venueMic, EspVenue.EspExecutionResult result) {
    LastLookStats stats = lastLookStats(venueMic);
    long fills = stats.fills();
    long lastLookRejects = stats.lastLookRejects();
    long hold = stats.totalHoldMillis();
    if (result instanceof EspVenue.EspExecutionResult.Filled filled) {
      fills++;
      hold += filled.holdMillis();
    } else if (result instanceof EspVenue.EspExecutionResult.Rejected rejected
        && rejected.reason() == EspVenue.RejectReason.LAST_LOOK_PRICE_MOVED) {
      lastLookRejects++;
    }
    statsByVenue.put(
        venueMic, new LastLookStats(venueMic, stats.attempts() + 1, fills, lastLookRejects, hold));
  }

  private ClickResult audited(ClickRequest request, ClickResult result, long nowMillis) {
    String outcome =
        result instanceof ClickResult.Filled ? "FILLED" : ((ClickResult.Rejected) result).reason();
    String detail =
        result instanceof ClickResult.Filled filled
            ? "filled " + filled.qty() + " @ " + filled.px() + " on " + filled.venueMic()
            : ((ClickResult.Rejected) result).detail();
    audit.add(
        new ClickAudit(
            request.sessionId(),
            request.figi(),
            request.side(),
            request.qty(),
            outcome,
            detail,
            nowMillis));
    return result;
  }
}
