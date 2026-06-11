/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.bloomberg;

import io.crossasset.ems.md.MdField;
import java.util.Set;

/**
 * Manual desk smoke for the Bloomberg adapter (task 18.13) — run on a machine with a terminal (or
 * against a SAPI host) and the BLPAPI jar on the classpath:
 *
 * <pre>
 *   ./gradlew :ems-market-data:runBloombergFeed -PbbgArgs="desktop BBG000BLNNH6"
 *   ./gradlew :ems-market-data:runBloombergFeed -PbbgArgs="server sapi-host 8194 MYAPP BBG000BLNNH6"
 * </pre>
 *
 * Prints health transitions and ticks until interrupted. This is the runtime verification path the
 * CI fake cannot cover.
 */
public final class BloombergFeedMain {

  private BloombergFeedMain() {}

  public static void main(String[] args) throws InterruptedException {
    if (args.length < 2) {
      System.err.println("usage: desktop <figi...> | server <host> <port> <appName> <figi...>");
      System.exit(2);
    }
    BloombergConfig config;
    int figiStart;
    if ("server".equals(args[0])) {
      config = BloombergConfig.server(args[1], Integer.parseInt(args[2]), args[3]);
      figiStart = 4;
    } else {
      config = BloombergConfig.desktop();
      figiStart = 1;
    }

    BloombergFeed feed = new BloombergFeed(config, new ReflectiveBlpapiDriver());
    feed.addHealthListener(
        (feedId, health) ->
            System.out.printf("[health] %s %s — %s%n", feedId, health.status(), health.detail()));
    for (int i = figiStart; i < args.length; i++) {
      feed.subscribe(
          args[i],
          Set.of(MdField.BID, MdField.ASK, MdField.LAST, MdField.VOLUME),
          tick -> System.out.printf("[tick] %s %s%n", tick.figi(), tick.values()));
    }
    feed.start();
    Thread.currentThread().join();
  }
}
