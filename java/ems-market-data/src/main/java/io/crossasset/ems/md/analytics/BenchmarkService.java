/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.analytics;

import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.MdTick;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Streaming benchmark service (task 9.5, [[arch-realtime-analytics]]): computes VWAP / TWAP / PWP /
 * arrival / mid / last continuously from the 18.12 market-data SPI, once, for every consumer —
 * compliance's fat-finger band (10.2), TCA slippage decomposition (12.14), SOR and automation
 * rules. Computing benchmarks in N places is wrong (drift, inconsistency); this is the one place.
 *
 * <p>Deterministic: state advances only on {@link #onTick} (event time from the tick, never wall
 * clock); arrival marks snapshot the stream at the moment of marking. Replaying the same tick
 * stream reproduces identical benchmarks.
 *
 * <p>Prices are fixed-point 1e4 like the rest of the EMS; volumes are feed-cumulative (the SPI
 * convention) and deltas are derived internally.
 */
public final class BenchmarkService {

  /** A point-in-time benchmark snapshot for one instrument. */
  public record Benchmarks(
      String figi, long vwap, long twap, long mid, long last, long totalVolume, long asOfMillis) {}

  /** One trade-tape entry (used by PWP and window VWAPs). */
  private record Print(long atMillis, long px, long volumeDelta) {}

  private static final class State {
    long lastPx;
    long bid;
    long ask;
    long cumVolume; // feed-cumulative
    long vwapNotional; // Σ px × volumeDelta
    long vwapVolume; // Σ volumeDelta
    // TWAP: integral of reference px over time.
    long twapWeightedSum;
    long twapElapsedMillis;
    long lastTickMillis = Long.MIN_VALUE;
    final List<Print> tape = new ArrayList<>();
  }

  private final Map<String, State> byFigi = new LinkedHashMap<>();
  private final Map<String, Benchmarks> arrivalMarks = new LinkedHashMap<>();
  private final int tapeLimit;

  public BenchmarkService() {
    this(100_000);
  }

  /**
   * @param tapeLimit per-instrument trade-tape retention (oldest prints drop past it)
   */
  public BenchmarkService(int tapeLimit) {
    this.tapeLimit = tapeLimit;
  }

  /**
   * Feed one tick (wire as the SPI listener: {@code feed.subscribe(figi, fields, svc::onTick)}).
   */
  public synchronized void onTick(MdTick tick) {
    State state = byFigi.computeIfAbsent(tick.figi(), k -> new State());
    Long bid = tick.values().get(MdField.BID);
    Long ask = tick.values().get(MdField.ASK);
    Long last = tick.values().get(MdField.LAST);
    Long volume = tick.values().get(MdField.VOLUME);

    // TWAP integrates the PREVIOUS reference price over the elapsed interval (step function).
    long reference = referencePx(state);
    if (reference > 0 && state.lastTickMillis != Long.MIN_VALUE) {
      long elapsed = Math.max(0, tick.atMillis() - state.lastTickMillis);
      state.twapWeightedSum += reference * elapsed;
      state.twapElapsedMillis += elapsed;
    }
    state.lastTickMillis = tick.atMillis();

    if (bid != null) {
      state.bid = bid;
    }
    if (ask != null) {
      state.ask = ask;
    }
    if (last != null) {
      state.lastPx = last;
    }
    if (volume != null && last != null) {
      long delta = Math.max(0, volume - state.cumVolume);
      state.cumVolume = Math.max(state.cumVolume, volume);
      if (delta > 0) {
        state.vwapNotional += last * delta;
        state.vwapVolume += delta;
        state.tape.add(new Print(tick.atMillis(), last, delta));
        if (state.tape.size() > tapeLimit) {
          state.tape.remove(0);
        }
      }
    } else if (volume != null) {
      state.cumVolume = Math.max(state.cumVolume, volume);
    }
  }

  /** Current benchmarks, or empty when the instrument has no ticks yet. */
  public synchronized Optional<Benchmarks> benchmarks(String figi) {
    State state = byFigi.get(figi);
    if (state == null || state.lastTickMillis == Long.MIN_VALUE) {
      return Optional.empty();
    }
    return Optional.of(snapshot(figi, state));
  }

  private Benchmarks snapshot(String figi, State state) {
    long vwap = state.vwapVolume > 0 ? state.vwapNotional / state.vwapVolume : state.lastPx;
    long twap =
        state.twapElapsedMillis > 0
            ? state.twapWeightedSum / state.twapElapsedMillis
            : referencePx(state);
    return new Benchmarks(
        figi, vwap, twap, mid(state), state.lastPx, state.cumVolume, state.lastTickMillis);
  }

  /**
   * Record an order's ARRIVAL: snapshots the stream now, keyed by the caller's handle (orderId).
   * TCA's arrival benchmark and IS-style slippage measure against this fixed point.
   */
  public synchronized Optional<Benchmarks> markArrival(String handle, String figi) {
    Optional<Benchmarks> snap = benchmarks(figi);
    snap.ifPresent(s -> arrivalMarks.put(handle, s));
    return snap;
  }

  /** The arrival snapshot recorded for {@code handle}, if any. */
  public synchronized Optional<Benchmarks> arrival(String handle) {
    return Optional.ofNullable(arrivalMarks.get(handle));
  }

  /**
   * Participation-Weighted Price (PWP): what an algo participating at {@code participationBp} of
   * market volume would have achieved for {@code qty}, measured over the tape AFTER the arrival
   * mark — the volume-weighted price of the first {@code qty × 10000 / participationBp} shares
   * printed since arrival. Empty until the tape has absorbed that much volume.
   */
  public synchronized Optional<Long> pwp(
      String handle, String figi, long qty, long participationBp) {
    Benchmarks mark = arrivalMarks.get(handle);
    State state = byFigi.get(figi);
    if (mark == null || state == null || participationBp <= 0) {
      return Optional.empty();
    }
    long targetVolume = qty * 10_000 / participationBp;
    long notional = 0;
    long volume = 0;
    for (Print print : state.tape) {
      if (print.atMillis() <= mark.asOfMillis()) {
        continue; // tape before arrival is not achievable volume
      }
      long take = Math.min(print.volumeDelta(), targetVolume - volume);
      notional += print.px() * take;
      volume += take;
      if (volume >= targetVolume) {
        return Optional.of(notional / volume);
      }
    }
    return Optional.empty(); // not enough volume has printed yet
  }

  private static long mid(State state) {
    if (state.bid > 0 && state.ask > 0) {
      return (state.bid + state.ask) / 2;
    }
    return state.lastPx;
  }

  private static long referencePx(State state) {
    long mid = mid(state);
    return mid > 0 ? mid : state.lastPx;
  }
}
