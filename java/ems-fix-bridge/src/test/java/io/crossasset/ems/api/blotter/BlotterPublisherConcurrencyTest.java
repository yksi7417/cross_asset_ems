/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.oms.OrderSubState;
import io.crossasset.ems.oms.StagedOrder;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * L4-6 regression: {@link BlotterPublisher#averagePx} used to read {@code acc[0]}/{@code acc[1]}
 * non-atomically from a {@code long[]} that {@code accumulate} mutated in place, so a fill landing
 * between the two reads produced a torn weighted-average price (qty from before an update, notional
 * from after). The fix makes {@code accumulate} copy-on-write, so every published reference is an
 * immutable, consistent (cumQty, cumNotional) pair.
 *
 * <p>Detector: if every execution is booked at the SAME price P, the weighted average must ALWAYS
 * read exactly P — a torn read (qty and notional taken from different committed states) yields an
 * average other than P. We hammer {@code recordExecution} from many threads while a reader thread
 * repeatedly {@code publishOrder}s and asserts the published {@code avgPx} is always P.
 */
final class BlotterPublisherConcurrencyTest {

  private static final long PX = 100_0000L; // $100.0000, fixed-point 1e4 — the single booked price
  private static final Pattern AVG_PX = Pattern.compile("\"avgPx\":(-?\\d+)");

  @Test
  void averagePx_isNeverTornUnderConcurrentFills() throws InterruptedException {
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    BlotterPublisher publisher = new BlotterPublisher(subscriptions, () -> 1_000L);
    StagedOrder order = order("O-1", 7L, "ACC-1");

    // Any published avgPx that is neither absent nor exactly PX is a torn read — capture the first.
    AtomicReference<Long> tornValue = new AtomicReference<>(null);
    AtomicLong published = new AtomicLong();
    subscriptions.subscribe(
        7L,
        BlotterPublisher.TOPIC_ORDERS,
        0L,
        (sessionId, subscriptionId, event) -> {
          Matcher m = AVG_PX.matcher(event.payload());
          if (m.find()) {
            long avg = Long.parseLong(m.group(1));
            published.incrementAndGet();
            if (avg != PX) {
              tornValue.compareAndSet(null, avg);
            }
          }
        });

    int writers = 6;
    int fillsPerWriter = 4_000;
    ExecutorService pool = Executors.newFixedThreadPool(writers + 1);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(writers);

    for (int w = 0; w < writers; w++) {
      pool.submit(
          () -> {
            awaitQuietly(start);
            for (int i = 0; i < fillsPerWriter; i++) {
              publisher.recordExecution("O-1", "R-1", 1L, PX);
            }
            done.countDown();
          });
    }
    // Reader: publish continuously while the write storm runs, so a torn read has every chance to
    // surface through the published avgPx.
    pool.submit(
        () -> {
          awaitQuietly(start);
          while (done.getCount() > 0
              && tornValue.get() == null
              && !Thread.currentThread().isInterrupted()) {
            publisher.publishOrder(order);
            Thread.onSpinWait();
          }
          for (int i = 0; i < 50; i++) {
            publisher.publishOrder(order); // drain a few after writers finish
          }
        });

    start.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

    assertThat(tornValue.get())
        .as("published avgPx must always equal the single booked price %s (no torn read)", PX)
        .isNull();
    assertThat(published.get()).as("reader actually observed published rows").isPositive();

    // And the settled average is exactly the booked price.
    publisher.publishOrder(order);
    assertThat(tornValue.get()).isNull();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static StagedOrder order(String orderId, long sessionId, String account) {
    return new StagedOrder(
        orderId,
        "CL-" + orderId,
        sessionId,
        OrderFsmState.NEW,
        new OrderFsmContext(
            orderId,
            "CL-" + orderId,
            null,
            "BBG000BLNNH6",
            1,
            100L,
            PX,
            0L,
            100L,
            account,
            0,
            "CL-" + orderId,
            orderId,
            1L,
            null,
            null),
        OrderSubState.READY,
        Set.of(),
        1_000L);
  }
}
