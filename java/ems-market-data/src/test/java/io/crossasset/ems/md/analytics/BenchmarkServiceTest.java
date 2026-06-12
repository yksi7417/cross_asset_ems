/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.MdTick;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 9.5: hand-computed benchmark pins — VWAP, TWAP, arrival, PWP — and replay determinism. */
class BenchmarkServiceTest {

  private static final String FIGI = "BBG000B9XRY4";

  private final BenchmarkService service = new BenchmarkService();

  private void trade(long px, long cumVolume, long atMillis) {
    service.onTick(
        new MdTick("sim", FIGI, Map.of(MdField.LAST, px, MdField.VOLUME, cumVolume), atMillis));
  }

  private void quote(long bid, long ask, long atMillis) {
    service.onTick(new MdTick("sim", FIGI, Map.of(MdField.BID, bid, MdField.ASK, ask), atMillis));
  }

  @Test
  void vwap_isVolumeWeighted_fromCumulativeFeedVolumes() {
    trade(100_0000, 100, 1_000); // 100 @ 100.00
    trade(101_0000, 300, 2_000); // +200 @ 101.00
    trade(102_0000, 400, 3_000); // +100 @ 102.00

    BenchmarkService.Benchmarks b = service.benchmarks(FIGI).orElseThrow();
    // VWAP = (100×100 + 101×200 + 102×100) / 400 = 101.00
    assertThat(b.vwap()).isEqualTo(101_0000);
    assertThat(b.last()).isEqualTo(102_0000);
    assertThat(b.totalVolume()).isEqualTo(400);
  }

  @Test
  void twap_isTimeWeighted_notVolumeWeighted() {
    quote(99_9000, 100_1000, 0); // mid 100.00 …
    quote(101_9000, 102_1000, 9_000); // … held for 9s, then mid 102.00 …
    quote(99_0000, 99_0000, 10_000); // … held for 1s

    BenchmarkService.Benchmarks b = service.benchmarks(FIGI).orElseThrow();
    // TWAP = (100.00×9s + 102.00×1s) / 10s = 100.20
    assertThat(b.twap()).isEqualTo(100_2000);
  }

  @Test
  void arrivalMark_freezesTheSnapshot_whileTheStreamMovesOn() {
    quote(99_9000, 100_1000, 1_000);
    trade(100_0000, 100, 1_500);
    service.markArrival("ORD-1", FIGI);

    trade(110_0000, 200, 2_000); // market runs away after arrival

    BenchmarkService.Benchmarks arrival = service.arrival("ORD-1").orElseThrow();
    assertThat(arrival.mid()).isEqualTo(100_0000);
    assertThat(arrival.last()).isEqualTo(100_0000);
    // live snapshot moved
    assertThat(service.benchmarks(FIGI).orElseThrow().last()).isEqualTo(110_0000);
  }

  @Test
  void pwp_isTheVolumeWeightedPriceOfTheAchievableTapeSinceArrival() {
    trade(100_0000, 1_000, 1_000); // pre-arrival tape: must NOT count
    service.markArrival("ORD-1", FIGI);
    trade(101_0000, 1_200, 2_000); // +200 @ 101
    trade(103_0000, 1_500, 3_000); // +300 @ 103

    // Algo at 25% participation needs 4× its 100 qty = 400 shares of tape:
    // first 400 since arrival = 200@101 + 200@103 → PWP = 102.00
    assertThat(service.pwp("ORD-1", FIGI, 100, 2_500)).contains(102_0000L);
    // 100 qty at 5% needs 20_000 shares — not printed yet.
    assertThat(service.pwp("ORD-1", FIGI, 100, 50)).isEmpty();
  }

  @Test
  void replayDeterminism_sameTickStreamSameBenchmarks() {
    BenchmarkService a = new BenchmarkService();
    BenchmarkService b = new BenchmarkService();
    for (BenchmarkService s : new BenchmarkService[] {a, b}) {
      s.onTick(new MdTick("sim", FIGI, Map.of(MdField.BID, 99_0000L, MdField.ASK, 101_0000L), 1));
      s.onTick(new MdTick("sim", FIGI, Map.of(MdField.LAST, 100_5000L, MdField.VOLUME, 50L), 2));
      s.onTick(new MdTick("sim", FIGI, Map.of(MdField.LAST, 100_7000L, MdField.VOLUME, 90L), 9));
    }
    assertThat(a.benchmarks(FIGI)).isEqualTo(b.benchmarks(FIGI));
  }

  @Test
  void noTicks_noBenchmarks() {
    assertThat(service.benchmarks("BBG0UNKNOWN0")).isEmpty();
    assertThat(service.markArrival("X", "BBG0UNKNOWN0")).isEmpty();
  }
}
