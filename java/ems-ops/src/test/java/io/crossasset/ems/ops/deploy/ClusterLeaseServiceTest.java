/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ClusterLeaseService}: exclusive holding, heartbeat retention with timeout
 * expiry, position-gated acquisition, monotonic fence tokens, stale-token rejection. Per
 * arch-deployment.md, task 14.6.
 */
class ClusterLeaseServiceTest {

  private final long[] now = {0L};
  private final ClusterLeaseService leases = new ClusterLeaseService(() -> now[0], 10_000L);

  @Test
  void exactlyOneHolderPerPod() {
    assertThat(leases.acquire("pod-a", "PROD-A1", 0)).isPresent();
    assertThat(leases.acquire("pod-a", "PROD-A2", 0)).isEmpty();
    assertThat(leases.acquire("pod-b", "PROD-B1", 0)).isPresent(); // pods independent
    assertThat(leases.holder("pod-a")).contains("PROD-A1");
  }

  @Test
  void positionGate_enforcedAfterRelease() {
    long token = leases.acquire("pod-a", "PROD-A1", 0).orElseThrow();
    assertThat(token).isEqualTo(1);
    assertThat(leases.release("pod-a", "PROD-A1", 5_000)).isTrue();
    assertThat(leases.acquire("pod-a", "PROD-A2", 4_999)).isEmpty(); // behind P
    assertThat(leases.acquire("pod-a", "PROD-A2", 5_000)).contains(2L); // at P, token bumped
  }

  @Test
  void fenceTokens_monotonicAcrossHandoffs() {
    long t1 = leases.acquire("pod-a", "A1", 0).orElseThrow();
    leases.release("pod-a", "A1", 10);
    long t2 = leases.acquire("pod-a", "A2", 10).orElseThrow();
    leases.release("pod-a", "A2", 20);
    long t3 = leases.acquire("pod-a", "A1", 20).orElseThrow();
    assertThat(t1).isLessThan(t2);
    assertThat(t2).isLessThan(t3);
  }

  @Test
  void heartbeat_retains_timeoutReleases() {
    leases.acquire("pod-a", "A1", 0);
    now[0] = 9_000L;
    assertThat(leases.heartbeat("pod-a", "A1")).isTrue(); // retained, clock reset
    now[0] = 18_000L; // 9s since last beat — still alive
    assertThat(leases.holder("pod-a")).contains("A1");
    now[0] = 19_000L; // 10s since last beat — expired
    assertThat(leases.holder("pod-a")).isEmpty();
    // Failure-path handoff: the standby (caught up past the last explicit release) acquires.
    assertThat(leases.acquire("pod-a", "A2", 0)).isPresent();
  }

  @Test
  void heartbeat_byNonHolder_false() {
    leases.acquire("pod-a", "A1", 0);
    assertThat(leases.heartbeat("pod-a", "A2")).isFalse();
    assertThat(leases.heartbeat("pod-nope", "A1")).isFalse();
  }

  @Test
  void release_byNonHolder_refused() {
    leases.acquire("pod-a", "A1", 0);
    assertThat(leases.release("pod-a", "A2", 100)).isFalse();
    assertThat(leases.holder("pod-a")).contains("A1");
  }

  @Test
  void staleTokenRejected_currentTokenAccepted() {
    long t1 = leases.acquire("pod-a", "A1", 0).orElseThrow();
    assertThat(leases.isCurrentToken("pod-a", t1)).isTrue();
    leases.release("pod-a", "A1", 10);
    long t2 = leases.acquire("pod-a", "A2", 10).orElseThrow();
    assertThat(leases.isCurrentToken("pod-a", t1)).isFalse(); // runaway old cluster fenced
    assertThat(leases.isCurrentToken("pod-a", t2)).isTrue();
  }

  @Test
  void worksAsTheSwitchoverProtocolLease() {
    leases.acquire("pod-a", "A1", 0);
    SwitchoverProtocol.LeaseService asSeam = leases;
    assertThat(asSeam.holder("pod-a")).contains("A1");
  }
}
