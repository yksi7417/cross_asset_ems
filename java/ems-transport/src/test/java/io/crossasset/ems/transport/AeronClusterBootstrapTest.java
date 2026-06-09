/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for 3-node Aeron Cluster (Raft) bootstrap.
 *
 * <p>Verifies: (1) exactly one LEADER + two FOLLOWERs after real Raft election, (2) PING/PONG
 * round-trip through quorum commit, (3) Archive recordings on all three members (log replicated to
 * full quorum).
 *
 * <p>Tagged {@code cluster-integration} so it stays out of the fast unit-test lane. Run with:
 * {@code ./gradlew :ems-transport:test -Dtags="cluster-integration"} or include via Gradle tag
 * filter.
 *
 * <p>Task 2.5.
 */
@Tag("cluster-integration")
class AeronClusterBootstrapTest {

  /**
   * 3-node Raft bootstrap: start, elect, ping/pong, verify quorum replication.
   *
   * <p>Timeout of 60 s guards against hung election; the real election should complete in under 10
   * s.
   */
  @Test
  @Timeout(60)
  void threeNodeCluster_electsLeaderAndProcessesPingPong(@TempDir final File baseDir)
      throws Exception {
    try (AeronClusterBootstrap cluster = new AeronClusterBootstrap(baseDir)) {
      cluster.start();

      // ── 1. Wait for Raft leader election ──────────────────────────────────
      final boolean leaderFound = cluster.waitForLeaderElection(30_000);
      assertTrue(leaderFound, "No leader elected within 30 s");

      // ── 2. Exactly one leader, two followers ──────────────────────────────
      assertEquals(1L, cluster.leaderCount(), "Expected exactly one LEADER");
      assertEquals(2L, cluster.followerCount(), "Expected exactly two FOLLOWERs");

      // ── 3. PING/PONG through quorum commit ────────────────────────────────
      final List<Long> rttsNs = cluster.runPingPong(5);
      assertEquals(5, rttsNs.size(), "Expected 5 PONG replies");
      for (final long rttNs : rttsNs) {
        assertTrue(rttNs > 0, "RTT must be positive");
      }

      // ── 4. Archive recordings on all three members ────────────────────────
      // All three archive dirs must be non-empty: the Raft log was replicated to the full quorum.
      for (int i = 0; i < AeronClusterBootstrap.CLUSTER_SIZE; i++) {
        assertTrue(
            cluster.hasArchiveRecordings(i),
            "Member " + i + " archive dir is empty — log not replicated to full quorum");
      }
    }
  }

  /** Sanity-check: port helpers produce non-overlapping ranges. */
  @Test
  void portHelpers_areNonOverlapping() {
    for (int a = 0; a < AeronClusterBootstrap.CLUSTER_SIZE; a++) {
      for (int b = a + 1; b < AeronClusterBootstrap.CLUSTER_SIZE; b++) {
        assertNotEquals(
            AeronClusterBootstrap.ingressPort(a),
            AeronClusterBootstrap.ingressPort(b),
            "Members " + a + " and " + b + " share ingress port");
        assertNotEquals(
            AeronClusterBootstrap.archivePort(a),
            AeronClusterBootstrap.archivePort(b),
            "Members " + a + " and " + b + " share archive port");
      }
    }
  }

  /** Verify CLUSTER_MEMBERS_STR lists all three members separated by '|'. */
  @Test
  void clusterMembersStr_listsAllThreeMembers() {
    final String members = AeronClusterBootstrap.CLUSTER_MEMBERS_STR;
    assertEquals(
        AeronClusterBootstrap.CLUSTER_SIZE - 1,
        members.chars().filter(c -> c == '|').count(),
        "Expected " + (AeronClusterBootstrap.CLUSTER_SIZE - 1) + " '|' separators in: " + members);
    for (int i = 0; i < AeronClusterBootstrap.CLUSTER_SIZE; i++) {
      assertTrue(members.contains(i + ","), "Member " + i + " missing from CLUSTER_MEMBERS_STR");
    }
  }
}
