/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.bloomberg;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.md.FeedHealth;
import io.crossasset.ems.md.MarketDataFeed;
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.MdTick;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Bloomberg adapter tests (task 18.13) against a scripted fake driver — the CI stand-in the task
 * prescribes (runtime needs a terminal). Covers the session FSM (CONNECTING → UP → DOWN →
 * re-subscribe on reconnect), FIGI → {@code /bbgid/} topics, mnemonic mapping both directions,
 * fixed-point price scaling, entitlement failures surfacing on both the subscription and feed
 * health, and unsubscribe/close semantics.
 */
class BloombergFeedTest {

  private static final String FIGI = "BBG000BLNNH6";

  /** Records subscribe/unsubscribe calls; the test fires driver events by hand. */
  private static final class FakeDriver implements BlpapiDriver {
    DriverEvents events;
    BloombergConfig config;
    final List<String> calls = new ArrayList<>();

    @Override
    public void connect(BloombergConfig config, DriverEvents events) {
      this.config = config;
      this.events = events;
      calls.add("connect:" + config.host() + ":" + config.port());
    }

    @Override
    public void disconnect() {
      calls.add("disconnect");
    }

    @Override
    public void subscribe(long correlationId, String security, List<String> mnemonics) {
      calls.add("subscribe:" + correlationId + ":" + security + ":" + String.join(",", mnemonics));
    }

    @Override
    public void unsubscribe(long correlationId) {
      calls.add("unsubscribe:" + correlationId);
    }
  }

  private static final class CapturingListener implements MarketDataFeed.Listener {
    final List<MdTick> ticks = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    @Override
    public void onTick(MdTick tick) {
      ticks.add(tick);
    }

    @Override
    public void onSubscriptionError(String figi, String code, String message) {
      errors.add(figi + ":" + code);
    }
  }

  private final FakeDriver driver = new FakeDriver();
  private final BloombergFeed feed = new BloombergFeed(BloombergConfig.desktop(), driver);
  private final CapturingListener listener = new CapturingListener();

  @Test
  void start_connectsAndReportsConnecting_thenUpOnSessionUp() {
    feed.start();
    assertThat(driver.calls).containsExactly("connect:127.0.0.1:8194");
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.CONNECTING);

    driver.events.onSessionUp();
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.UP);
  }

  @Test
  void subscribeBeforeSessionUp_parksAndIssuesOnceUp() {
    feed.start();
    feed.subscribe(FIGI, Set.of(MdField.BID), listener);
    assertThat(driver.calls).noneMatch(c -> c.startsWith("subscribe"));

    driver.events.onSessionUp();
    assertThat(driver.calls).contains("subscribe:1:/bbgid/" + FIGI + ":BID");
  }

  @Test
  void subscribeWhileUp_issuesImmediately_withMappedMnemonics() {
    feed.start();
    driver.events.onSessionUp();
    feed.subscribe(FIGI, Set.of(MdField.LAST), listener);

    assertThat(driver.calls).contains("subscribe:1:/bbgid/" + FIGI + ":LAST_PRICE");
  }

  @Test
  void tick_mapsMnemonicsBack_andScalesPricesToFixedPoint() {
    feed.start();
    driver.events.onSessionUp();
    feed.subscribe(FIGI, Set.of(MdField.BID, MdField.LAST, MdField.LAST_SIZE), listener);

    driver.events.onTick(
        1, Map.of("BID", 100.25, "LAST_PRICE", 100.5, "SIZE_LAST_TRADE", 300.0), 9_000L);

    assertThat(listener.ticks).hasSize(1);
    MdTick tick = listener.ticks.get(0);
    assertThat(tick.feedId()).isEqualTo("bloomberg-dapi");
    assertThat(tick.figi()).isEqualTo(FIGI);
    assertThat(tick.atMillis()).isEqualTo(9_000L);
    assertThat(tick.values())
        .containsOnly(
            Map.entry(MdField.BID, 1_002_500L),
            Map.entry(MdField.LAST, 1_005_000L),
            Map.entry(MdField.LAST_SIZE, 300L));
  }

  @Test
  void tick_dropsUnrequestedMnemonics_andSkipsEmptyTicks() {
    feed.start();
    driver.events.onSessionUp();
    feed.subscribe(FIGI, Set.of(MdField.BID), listener);

    driver.events.onTick(1, Map.of("ASK", 101.0, "VOLUME", 5_000.0), 1L);

    assertThat(listener.ticks).isEmpty();
  }

  @Test
  void unknownCorrelation_ignored() {
    feed.start();
    driver.events.onSessionUp();
    driver.events.onTick(99, Map.of("BID", 1.0), 1L);

    assertThat(listener.ticks).isEmpty();
  }

  @Test
  void sessionBounce_reportsDown_thenResubscribesEverythingOnReconnect() {
    feed.start();
    driver.events.onSessionUp();
    feed.subscribe(FIGI, Set.of(MdField.BID), listener);
    feed.subscribe("BBG000B9XRY4", Set.of(MdField.ASK), listener);
    driver.calls.clear();

    driver.events.onSessionDown("SessionConnectionDown");
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.DOWN);
    assertThat(feed.health().detail()).isEqualTo("SessionConnectionDown");

    driver.events.onSessionUp();
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.UP);
    assertThat(driver.calls)
        .containsExactly(
            "subscribe:1:/bbgid/" + FIGI + ":BID", "subscribe:2:/bbgid/BBG000B9XRY4:ASK");
  }

  @Test
  void entitlementFailure_surfacesOnSubscription_andDegradesFeed_thenRecovers() {
    feed.start();
    driver.events.onSessionUp();
    feed.subscribe(FIGI, Set.of(MdField.BID), listener);

    driver.events.onSubscriptionFailure(1, "SubscriptionFailure", "no permission");
    assertThat(listener.errors).containsExactly(FIGI + ":SubscriptionFailure");
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.DEGRADED);
    assertThat(feed.health().detail()).contains("1 subscription(s) failing");

    driver.events.onSubscriptionStarted(1);
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.UP);
  }

  @Test
  void unsubscribe_cancelsAtDriver_andStopsDelivery() {
    feed.start();
    driver.events.onSessionUp();
    String subId = feed.subscribe(FIGI, Set.of(MdField.BID), listener);

    assertThat(feed.unsubscribe(subId)).isTrue();
    assertThat(driver.calls).contains("unsubscribe:1");
    driver.events.onTick(1, Map.of("BID", 1.0), 1L);

    assertThat(listener.ticks).isEmpty();
    assertThat(feed.unsubscribe(subId)).isFalse();
  }

  @Test
  void close_disconnects_dropsSubscriptions_andGoesDown() {
    feed.start();
    driver.events.onSessionUp();
    feed.subscribe(FIGI, Set.of(MdField.BID), listener);

    feed.close();
    assertThat(driver.calls).contains("disconnect");
    assertThat(feed.health().status()).isEqualTo(FeedHealth.Status.DOWN);

    // A late driver event for the dropped sub is ignored.
    driver.events.onTick(1, Map.of("BID", 1.0), 1L);
    assertThat(listener.ticks).isEmpty();
  }

  @Test
  void lateSessionDownAfterClose_doesNotResurrectHealthChatter() {
    List<FeedHealth> seen = new ArrayList<>();
    feed.start();
    driver.events.onSessionUp();
    feed.close();
    feed.addHealthListener((feedId, health) -> seen.add(health));

    driver.events.onSessionDown("SessionTerminated");

    assertThat(seen).hasSize(1); // registration replay only; no new transition after close
    assertThat(feed.health().detail()).isEqualTo("closed");
  }

  @Test
  void serverConfig_carriesAuthOptionsAndSapiFeedId() {
    BloombergConfig config = BloombergConfig.server("sapi.example.com", 8194, "EMS_APP");
    assertThat(config.feedId()).isEqualTo("bloomberg-sapi");
    assertThat(config.authOptions())
        .isEqualTo(
            "AuthenticationMode=APPLICATION_ONLY;"
                + "ApplicationAuthenticationType=APPNAME_AND_KEY;"
                + "ApplicationName=EMS_APP");

    FakeDriver sapiDriver = new FakeDriver();
    new BloombergFeed(config, sapiDriver).start();
    assertThat(sapiDriver.config.authOptions()).contains("EMS_APP");
  }

  @Test
  void desktopConfig_defaultsToLocalTerminal() {
    BloombergConfig config = BloombergConfig.desktop();
    assertThat(config.host()).isEqualTo("127.0.0.1");
    assertThat(config.port()).isEqualTo(8194);
    assertThat(config.authOptions()).isEmpty();
    assertThat(config.feedId()).isEqualTo("bloomberg-dapi");
    assertThat(config.priceScale()).isEqualTo(10_000L);
  }
}
