/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.dropcopy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DropCopyServiceConcurrencyTest {

  @Test
  void concurrentExecutions_perSubscriberSeqIsContiguousNoDupNoGap() throws InterruptedException {
    DropCopyService service = new DropCopyService("EMS");
    ConcurrentLinkedQueue<Long> collectedSeqs = new ConcurrentLinkedQueue<>();
    String subId =
        service.subscribe(
            DropCopyService.ScopeKind.FIRM,
            "firm-demo",
            "RISK",
            (subscriptionId, seqNum, rawFix) -> collectedSeqs.add(seqNum));

    int n = 2_000;
    int threadCount = 8;
    CountDownLatch startLatch = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    try {
      // Submit exactly n single-execution tasks (NOT n per thread), so total onExecution
      // calls == n and the per-subscriber seq must be the contiguous set 1..n.
      for (int i = 0; i < n; i++) {
        final int idx = i;
        executor.submit(
            () -> {
              try {
                startLatch.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              service.onExecution(exec(idx));
            });
      }
      startLatch.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }

    List<Long> sortedSeqs = new ArrayList<>(collectedSeqs);
    sortedSeqs.sort(Long::compareTo);

    assertThat(sortedSeqs).hasSize(n);
    assertThat(sortedSeqs.get(0)).isEqualTo(1L);
    assertThat(sortedSeqs.get(n - 1)).isEqualTo((long) n);
    // Contiguous, no gap, no duplicate.
    for (int i = 0; i < n; i++) {
      assertThat(sortedSeqs.get(i)).isEqualTo(i + 1L);
    }
    assertThat(service.deliveredCount(subId)).isEqualTo(n);
  }

  private static DropCopyService.Execution exec(int i) {
    return new DropCopyService.Execution(
        "E" + i,
        "ORD-1",
        "ACC-A",
        "desk-1",
        "firm-demo",
        "BBG000B9XRY4",
        1,
        100L,
        1_824_500L,
        100L,
        400L,
        1_000L);
  }
}
