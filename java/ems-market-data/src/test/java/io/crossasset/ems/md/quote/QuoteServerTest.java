/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 9.1 + 9.2 + 9.3: topic/glob delivery and throttling, the who-is-on-this-topic compliance answer
 * with heartbeat pruning, and the multicast-tail / unicast-replay split with the fell-off-retention
 * re-image signal.
 */
class QuoteServerTest {

  private static QuoteServer.QuoteUpdate l1(String figi, String payload, long at) {
    return new QuoteServer.QuoteUpdate("quote." + figi + ".l1", figi, payload, at);
  }

  @Test
  void topicAndGlobSubscriptions_deliverInRegistrationOrder() {
    QuoteServer server = new QuoteServer(new SubscriberRegistry());
    List<String> exact = new ArrayList<>();
    List<String> glob = new ArrayList<>();
    server.subscribe(
        "sor", "quote.AAPL.l1", QuoteServer.Qos.BEST_EFFORT, 0, u -> exact.add(u.payload()), 0);
    server.subscribe(
        "desktop", "quote.AAPL.*", QuoteServer.Qos.GUARANTEED, 0, u -> glob.add(u.topic()), 0);

    server.publish(l1("AAPL", "bid=182.40", 1_000));
    server.publish(new QuoteServer.QuoteUpdate("quote.AAPL.trade", "AAPL", "px=182.45", 1_001));
    server.publish(l1("MSFT", "bid=415.00", 1_002)); // neither subscription

    assertThat(exact).containsExactly("bid=182.40");
    assertThat(glob).containsExactly("quote.AAPL.l1", "quote.AAPL.trade");
    assertThat(server.snapshot("quote.AAPL.l1").orElseThrow().payload()).isEqualTo("bid=182.40");
    assertThat(server.snapshot("quote.GOOG.l1")).isEmpty();
  }

  @Test
  void throttle_downsamplesByEventTimeSecond_neverWallClock() {
    QuoteServer server = new QuoteServer(new SubscriberRegistry());
    List<Long> seen = new ArrayList<>();
    server.subscribe(
        "slow-ui", "quote.AAPL.l1", QuoteServer.Qos.BEST_EFFORT, 2, u -> seen.add(u.atMillis()), 0);

    for (long t = 0; t < 5; t++) {
      server.publish(l1("AAPL", "tick" + t, 1_000 + t)); // 5 ticks inside one second
    }
    server.publish(l1("AAPL", "next-window", 2_500)); // next event-time second

    assertThat(seen).containsExactly(1_000L, 1_001L, 2_500L); // 2/sec then the window resets
  }

  @Test
  void registry_answersWhoIsOn_andPrunesMissedHeartbeats() {
    SubscriberRegistry registry = new SubscriberRegistry();
    String alive = registry.register("sor", "quote.AAPL.l1", QuoteServer.Qos.BEST_EFFORT, 0, 0);
    registry.register("stale-ui", "quote.AAPL.*", QuoteServer.Qos.BEST_EFFORT, 0, 0);

    // Multicast has no per-subscriber socket: the registry is the only "who sees this?" answer.
    assertThat(registry.whoIsOn("quote.AAPL.l1")).hasSize(2);

    registry.heartbeat(alive, 9_000);
    List<String> pruned = registry.prune(10_000, 5_000); // stale-ui last beat at 0
    assertThat(pruned).hasSize(1);
    assertThat(registry.whoIsOn("quote.AAPL.l1")).hasSize(1);
    assertThat(registry.whoIsOn("quote.AAPL.l1").get(0).subscriberId()).isEqualTo("sor");
  }

  @Test
  void multicastTail_lossIsNormal_unicastReplayIsTheRemedy() {
    QuoteMulticast.LoopbackTail tail = new QuoteMulticast.LoopbackTail();
    List<String> received = new ArrayList<>();
    tail.attach(u -> received.add(u.payload()));
    QuoteMulticast multicast = new QuoteMulticast(tail, 100);

    multicast.publish(l1("AAPL", "q1", 1));
    tail.setDropping(true); // the network eats packets — best-effort means best-effort
    multicast.publish(l1("AAPL", "q2", 2));
    multicast.publish(l1("AAPL", "q3", 3));
    tail.setDropping(false);
    multicast.publish(l1("AAPL", "q4", 4));

    assertThat(received).containsExactly("q1", "q4"); // gapped
    assertThat(multicast.currentSeq("quote.AAPL.l1")).isEqualTo(4);

    // The subscriber detects the gap (saw seq 1, stream is at 4) and replays from 2.
    List<QuoteServer.QuoteUpdate> replayed = multicast.replay("quote.AAPL.l1", 2).orElseThrow();
    assertThat(replayed)
        .extracting(QuoteServer.QuoteUpdate::payload)
        .containsExactly("q2", "q3", "q4");
  }

  @Test
  void replayPastRetention_saysReImageLoudly() {
    QuoteMulticast multicast = new QuoteMulticast(new QuoteMulticast.LoopbackTail(), 2);
    for (int i = 1; i <= 5; i++) {
      multicast.publish(l1("AAPL", "q" + i, i));
    }
    // Retention holds seqs 4..5; a gap back to 2 fell off — empty = snapshot re-image required.
    assertThat(multicast.replay("quote.AAPL.l1", 2)).isEmpty();
    assertThat(multicast.replay("quote.AAPL.l1", 4).orElseThrow()).hasSize(2);
  }

  @Test
  void aeronChannelConfig_isTheRealUriShape_andStreamIdsAreDeterministic() {
    assertThat(QuoteMulticast.aeronChannel("224.10.9.7", 40456, "eth0", 4))
        .isEqualTo("aeron:udp?endpoint=224.10.9.7:40456|interface=eth0|ttl=4");
    assertThat(QuoteMulticast.streamIdFor("quote.AAPL.l1"))
        .isEqualTo(QuoteMulticast.streamIdFor("quote.AAPL.l1"));
    assertThat(QuoteMulticast.streamIdFor("quote.AAPL.l1")).isGreaterThanOrEqualTo(1_000);
  }
}
