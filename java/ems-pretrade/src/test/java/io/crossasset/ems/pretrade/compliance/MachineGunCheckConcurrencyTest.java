/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import io.crossasset.ems.pretrade.compliance.ComplianceCheck.Finding;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation.Kind;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MachineGunCheckConcurrencyTest {

  @Test
  void concurrentRoutes_admitExactlyMaxRoutesPerWindow() throws InterruptedException {
    long[] now = {0L};
    LongSupplier clock = () -> now[0];

    var policy = new MachineGunCheck.Policy(60_000L, 50, 1_000_000_000_000L, 20);

    var check = new MachineGunCheck(policy, clock);

    int n = 500;
    int threadCount = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger admitted = new AtomicInteger(0);
    AtomicInteger blocked = new AtomicInteger(0);

    for (int i = 0; i < n; i++) {
      pool.submit(
          () -> {
            try {
              startLatch.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
            Optional<Finding> result = check.evaluate(route());
            if (result.isEmpty()) {
              admitted.incrementAndGet();
            } else if (result.get().outcome() == ComplianceOutcome.BLOCK) {
              blocked.incrementAndGet();
            }
          });
    }

    try {
      startLatch.countDown();

      pool.shutdown();
      Assertions.assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

      Assertions.assertThat(admitted.get()).isEqualTo(50);
      Assertions.assertThat(blocked.get()).isEqualTo(n - 50);
    } finally {
      pool.shutdownNow();
    }

  private static ComplianceOperation route() {
    return new ComplianceOperation(
        Kind.ROUTE,
        1L,
        "firm-a",
        "desk-1",
        "trader-1",
        "EMS-ORD-1",
        "BBG000BLNNH6",
        1,
        100L,
        null,
        "acc-1");
  }
}
