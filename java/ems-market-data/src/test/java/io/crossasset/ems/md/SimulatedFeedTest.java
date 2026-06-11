/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * SPI contract tests (task 18.12) run against the reference {@link SimulatedFeed}: FIGI+field-set
 * subscription with filtered delivery, unsubscribe semantics, health transitions, and
 * per-subscription entitlement-style errors. The Bloomberg adapter (18.13) must behave identically
 * behind the same interface.
 */
class SimulatedFeedTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final String OTHER_FIGI = "BBG000B9XRY4";

  private final SimulatedFeed feed = new SimulatedFeed("sim");

  private static final class CapturingListener implements MarketDataFeed.Listener {
    final List<MdTick> ticks = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    @Override
    public void onTick(MdTick tick) {
      ticks.add(tick);
    }

    @Override
    public void onSubscriptionError(String figi, String code, String message) {
      errors.add(figi + ":" + code + ":" + message);
    }
  }

  @Test
  void subscribeAndEmit_deliversTickFilteredToRequestedFields() {
    CapturingListener listener = new CapturingListener();
    feed.start();
    feed.subscribe(FIGI, Set.of(MdField.BID, MdField.ASK), listener);

    feed.emit(
        FIGI, Map.of(MdField.BID, 100_25L, MdField.ASK, 100_75L, MdField.VOLUME, 9_000L), 1_000L);

    assertThat(listener.ticks).hasSize(1);
    MdTick tick = listener.ticks.get(0);
    assertThat(tick.feedId()).isEqualTo("sim");
    assertThat(tick.figi()).isEqualTo(FIGI);
    assertThat(tick.atMillis()).isEqualTo(1_000L);
    assertThat(tick.values())
        .containsOnly(Map.entry(MdField.BID, 100_25L), Map.entry(MdField.ASK, 100_75L));
  }

  @Test
  void emitWithNoRequestedField_deliversNothing() {
    CapturingListener listener = new CapturingListener();
    feed.subscribe(FIGI, Set.of(MdField.LAST), listener);

    feed.emit(FIGI, Map.of(MdField.BID, 100L), 1_000L);

    assertThat(listener.ticks).isEmpty();
  }

  @Test
  void otherInstrument_notDelivered() {
    CapturingListener listener = new CapturingListener();
    feed.subscribe(FIGI, Set.of(MdField.LAST), listener);

    feed.emit(OTHER_FIGI, Map.of(MdField.LAST, 55L), 1_000L);

    assertThat(listener.ticks).isEmpty();
  }

  @Test
  void twoSubscribers_eachSeesOwnFieldView() {
    CapturingListener quotes = new CapturingListener();
    CapturingListener trades = new CapturingListener();
    feed.subscribe(FIGI, Set.of(MdField.BID, MdField.ASK), quotes);
    feed.subscribe(FIGI, Set.of(MdField.LAST, MdField.LAST_SIZE), trades);

    feed.emit(FIGI, Map.of(MdField.BID, 99L, MdField.LAST, 100L, MdField.LAST_SIZE, 10L), 2_000L);

    assertThat(quotes.ticks).hasSize(1);
    assertThat(quotes.ticks.get(0).values()).containsOnlyKeys(MdField.BID);
    assertThat(trades.ticks).hasSize(1);
    assertThat(trades.ticks.get(0).values()).containsOnlyKeys(MdField.LAST, MdField.LAST_SIZE);
  }

  @Test
  void unsubscribe_stopsDelivery() {
    CapturingListener listener = new CapturingListener();
    String subId = feed.subscribe(FIGI, Set.of(MdField.LAST), listener);
    feed.emit(FIGI, Map.of(MdField.LAST, 1L), 1L);

    assertThat(feed.unsubscribe(subId)).isTrue();
    feed.emit(FIGI, Map.of(MdField.LAST, 2L), 2L);

    assertThat(listener.ticks).hasSize(1);
    assertThat(feed.unsubscribe(subId)).as("second unsubscribe is unknown").isFalse();
  }

  @Test
  void healthTransitions_notifyListenersAndReflectInHealth() {
    List<FeedHealth> seen = new ArrayList<>();
    feed.addHealthListener((feedId, health) -> seen.add(health));

    assertThat(seen).as("listener receives current state on registration").hasSize(1);
    assertThat(seen.get(0).status()).isEqualTo(FeedHealth.Status.DOWN);

    feed.start();
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.UP);
    feed.setHealth(FeedHealth.Status.DEGRADED, "gap detected", 5_000L);
    feed.close();

    assertThat(seen)
        .extracting(FeedHealth::status)
        .containsExactly(
            FeedHealth.Status.DOWN,
            FeedHealth.Status.UP,
            FeedHealth.Status.DEGRADED,
            FeedHealth.Status.DOWN);
    assertThat(feed.health().detail()).isEqualTo("closed");
  }

  @Test
  void start_isIdempotent_noDuplicateHealthEvent() {
    List<FeedHealth> seen = new ArrayList<>();
    feed.start();
    feed.addHealthListener((feedId, health) -> seen.add(health));
    feed.start();

    assertThat(seen).hasSize(1);
  }

  @Test
  void subscriptionError_surfacesToInstrumentSubscribersOnly() {
    CapturingListener affected = new CapturingListener();
    CapturingListener unaffected = new CapturingListener();
    feed.subscribe(FIGI, Set.of(MdField.BID), affected);
    feed.subscribe(OTHER_FIGI, Set.of(MdField.BID), unaffected);

    feed.failSubscription(FIGI, "ENTITLEMENT", "No real-time permission for exchange");

    assertThat(affected.errors)
        .containsExactly(FIGI + ":ENTITLEMENT:No real-time permission for exchange");
    assertThat(unaffected.errors).isEmpty();
  }

  @Test
  void close_dropsSubscriptions() {
    CapturingListener listener = new CapturingListener();
    feed.start();
    feed.subscribe(FIGI, Set.of(MdField.LAST), listener);
    feed.close();

    feed.start();
    feed.emit(FIGI, Map.of(MdField.LAST, 9L), 9L);

    assertThat(listener.ticks).isEmpty();
  }
}
