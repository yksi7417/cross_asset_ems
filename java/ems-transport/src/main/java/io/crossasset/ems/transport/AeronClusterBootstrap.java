/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * 3-node Aeron Cluster (Raft) bootstrap for integration testing.
 *
 * <p>Launches three {@link ClusteredMediaDriver} + {@link ClusteredServiceContainer} instances in
 * the same JVM (different threads, separate Aeron dirs, non-overlapping UDP port ranges). Members
 * run a real Raft election — no {@code appointedLeaderId} — so the test exercises actual
 * distributed consensus.
 *
 * <p>Port layout (BASE_PORT=29200, one block of 10 per member):
 *
 * <pre>
 *   Member 0: ingress=29200  member=29201  log=29202  transfer=29203  archive=29204  replicate=29205
 *   Member 1: ingress=29210  member=29211  log=29212  transfer=29213  archive=29214  replicate=29215
 *   Member 2: ingress=29220  member=29221  log=29222  transfer=29223  archive=29224  replicate=29225
 *   Client egress: 29250
 * </pre>
 *
 * <p>Task 2.5 — 3-node Raft bootstrap (opus — distributed consensus).
 */
public final class AeronClusterBootstrap implements AutoCloseable {

  public static final int CLUSTER_SIZE = 3;

  // Port layout: BASE_PORT + (memberId * PORTS_PER_MEMBER) + offset
  static final int BASE_PORT = 29200;
  static final int PORTS_PER_MEMBER = 10;
  static final int INGRESS_OFFSET = 0;
  static final int MEMBER_OFFSET = 1;
  static final int LOG_OFFSET = 2;
  static final int TRANSFER_OFFSET = 3;
  static final int ARCHIVE_OFFSET = 4;
  static final int REPLICATION_OFFSET = 5;
  static final int EGRESS_PORT = 29250;

  /** Cluster members descriptor used by ConsensusModule and client. */
  public static final String CLUSTER_MEMBERS_STR = buildClusterMembersStr();

  /** All ingress endpoints listed for client discovery; format: "id=host:port,..." */
  public static final String CLIENT_INGRESS_ENDPOINTS = buildClientIngressEndpoints();

  // ── Port helpers ──────────────────────────────────────────────────────────

  public static int ingressPort(final int memberId) {
    return BASE_PORT + memberId * PORTS_PER_MEMBER + INGRESS_OFFSET;
  }

  public static int memberPort(final int memberId) {
    return BASE_PORT + memberId * PORTS_PER_MEMBER + MEMBER_OFFSET;
  }

  public static int logPort(final int memberId) {
    return BASE_PORT + memberId * PORTS_PER_MEMBER + LOG_OFFSET;
  }

  public static int transferPort(final int memberId) {
    return BASE_PORT + memberId * PORTS_PER_MEMBER + TRANSFER_OFFSET;
  }

  public static int archivePort(final int memberId) {
    return BASE_PORT + memberId * PORTS_PER_MEMBER + ARCHIVE_OFFSET;
  }

  public static int replicationPort(final int memberId) {
    return BASE_PORT + memberId * PORTS_PER_MEMBER + REPLICATION_OFFSET;
  }

  // ── Static constructors ───────────────────────────────────────────────────

  private static String buildClusterMembersStr() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < CLUSTER_SIZE; i++) {
      if (i > 0) sb.append('|');
      sb.append(i)
          .append(",localhost:")
          .append(ingressPort(i))
          .append(",localhost:")
          .append(memberPort(i))
          .append(",localhost:")
          .append(logPort(i))
          .append(",localhost:")
          .append(transferPort(i))
          .append(",localhost:")
          .append(archivePort(i));
    }
    return sb.toString();
  }

  private static String buildClientIngressEndpoints() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < CLUSTER_SIZE; i++) {
      if (i > 0) sb.append(',');
      sb.append(i).append("=localhost:").append(ingressPort(i));
    }
    return sb.toString();
  }

  // ── Instance state ────────────────────────────────────────────────────────

  private final File baseDir;
  private final ClusterMember[] members = new ClusterMember[CLUSTER_SIZE];

  public AeronClusterBootstrap(final File baseDir) {
    this.baseDir = baseDir;
  }

  /** Starts all three cluster members concurrently. Blocks until all containers are running. */
  public void start() {
    // Launch members concurrently — each blocks until its ConsensusModule is running.
    final Thread[] threads = new Thread[CLUSTER_SIZE];
    final Throwable[] errors = new Throwable[CLUSTER_SIZE];

    for (int i = 0; i < CLUSTER_SIZE; i++) {
      final int id = i;
      final File memberDir = new File(baseDir, "member-" + id);
      memberDir.mkdirs();
      members[i] = new ClusterMember(id, memberDir);
      threads[i] =
          Thread.ofVirtual()
              .name("cluster-start-" + id)
              .start(
                  () -> {
                    try {
                      members[id].start();
                    } catch (final Throwable t) {
                      errors[id] = t;
                    }
                  });
    }

    for (int i = 0; i < CLUSTER_SIZE; i++) {
      try {
        threads[i].join(30_000);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while starting member " + i, e);
      }
      if (errors[i] != null) {
        throw new RuntimeException("Member " + i + " failed to start", errors[i]);
      }
    }
  }

  /**
   * Polls until at least one member has LEADER role or the timeout elapses.
   *
   * @return true if a leader was found within the timeout
   */
  public boolean waitForLeaderElection(final long timeoutMs) throws InterruptedException {
    final long deadlineMs = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadlineMs) {
      for (final ClusterMember m : members) {
        if (m != null && m.role() == Cluster.Role.LEADER) {
          return true;
        }
      }
      Thread.sleep(100);
    }
    return false;
  }

  /** Returns the member ID of the current leader, or -1 if no leader found. */
  public int leaderId() {
    for (final ClusterMember m : members) {
      if (m != null && m.role() == Cluster.Role.LEADER) return m.id;
    }
    return -1;
  }

  /** Number of members currently in LEADER role (should be exactly 1 after election). */
  public long leaderCount() {
    long count = 0;
    for (final ClusterMember m : members) {
      if (m != null && m.role() == Cluster.Role.LEADER) count++;
    }
    return count;
  }

  /** Number of members currently in FOLLOWER role (should be exactly 2 after election). */
  public long followerCount() {
    long count = 0;
    for (final ClusterMember m : members) {
      if (m != null && m.role() == Cluster.Role.FOLLOWER) count++;
    }
    return count;
  }

  /**
   * Connects a client to the cluster (finds the leader automatically via all ingress endpoints),
   * sends {@code count} PING messages, and returns the round-trip times in nanoseconds.
   */
  public List<Long> runPingPong(final int count) {
    final List<Long> rttsNs = new ArrayList<>(count);
    final AtomicInteger pongsReceived = new AtomicInteger(0);
    final UnsafeBuffer pingBuffer = new UnsafeBuffer(new byte[4]);
    pingBuffer.putStringWithoutLengthAscii(0, "PING");

    // Use member-0's aeronDir — any Aeron client can be co-located with any member.
    final String aeronDirPath = new File(baseDir, "member-0/aeron").getAbsolutePath();

    final EgressListener egressListener =
        (clusterSessionId, timestamp, buffer, offset, length, header) -> {
          if (length == 4 && buffer.getStringWithoutLengthAscii(offset, 4).equals("PONG")) {
            pongsReceived.incrementAndGet();
          }
        };

    try (Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(aeronDirPath));
        AeronCluster client =
            AeronCluster.connect(
                new AeronCluster.Context()
                    .aeron(aeron)
                    .egressListener(egressListener)
                    .ingressChannel("aeron:udp?term-length=64k")
                    .ingressEndpoints(CLIENT_INGRESS_ENDPOINTS)
                    .egressChannel("aeron:udp?endpoint=localhost:" + EGRESS_PORT))) {

      for (int i = 0; i < count; i++) {
        final int pongsBefore = pongsReceived.get();
        final long startNs = System.nanoTime();

        while (client.offer(pingBuffer, 0, 4) < 0) {
          client.pollEgress();
        }

        final long offerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (pongsReceived.get() == pongsBefore) {
          client.pollEgress();
          if (System.nanoTime() > offerDeadline) {
            throw new IllegalStateException("Timed out waiting for PONG " + (i + 1));
          }
        }
        rttsNs.add(System.nanoTime() - startNs);
      }
    }
    return rttsNs;
  }

  /**
   * Returns true if the Archive directory for {@code memberId} contains at least one file,
   * indicating the Raft log has been recorded to disk on that member.
   */
  public boolean hasArchiveRecordings(final int memberId) {
    if (members[memberId] == null) return false;
    final File[] files = members[memberId].archiveDir.listFiles();
    return files != null && files.length > 0;
  }

  @Override
  public void close() {
    // Close in reverse launch order (containers before drivers).
    for (int i = CLUSTER_SIZE - 1; i >= 0; i--) {
      if (members[i] != null) {
        CloseHelper.quietClose(members[i]);
      }
    }
  }

  // ── ClusterMember (one per node) ──────────────────────────────────────────

  private static final class ClusterMember implements AutoCloseable {

    final int id;
    final File aeronDir;
    final File archiveDir;
    final File clusterDir;
    final RoleAwarePongService service = new RoleAwarePongService();

    ClusteredMediaDriver driver;
    ClusteredServiceContainer container;

    ClusterMember(final int id, final File memberDir) {
      this.id = id;
      this.aeronDir = new File(memberDir, "aeron");
      this.archiveDir = new File(memberDir, "archive");
      this.clusterDir = new File(memberDir, "cluster");
    }

    void start() {
      driver =
          ClusteredMediaDriver.launch(
              new MediaDriver.Context()
                  .aeronDirectoryName(aeronDir.getAbsolutePath())
                  .threadingMode(ThreadingMode.SHARED)
                  .dirDeleteOnStart(true)
                  .dirDeleteOnShutdown(true),
              new Archive.Context()
                  .aeronDirectoryName(aeronDir.getAbsolutePath())
                  .archiveDir(archiveDir)
                  .controlChannel("aeron:udp?endpoint=localhost:" + archivePort(id))
                  .localControlChannel("aeron:ipc?term-length=64k")
                  .replicationChannel("aeron:udp?endpoint=localhost:" + replicationPort(id))
                  .threadingMode(ArchiveThreadingMode.SHARED)
                  .deleteArchiveOnStart(true),
              new ConsensusModule.Context()
                  .aeronDirectoryName(aeronDir.getAbsolutePath())
                  .clusterDir(clusterDir)
                  .clusterMemberId(id)
                  // No appointedLeaderId — Raft election is the thing under test.
                  .clusterMembers(CLUSTER_MEMBERS_STR)
                  .ingressChannel("aeron:udp?term-length=64k")
                  .replicationChannel("aeron:udp?endpoint=localhost:" + replicationPort(id))
                  .deleteDirOnStart(true));

      container =
          ClusteredServiceContainer.launch(
              new ClusteredServiceContainer.Context()
                  .aeronDirectoryName(aeronDir.getAbsolutePath())
                  .clusterDir(clusterDir)
                  .clusteredService(service));
    }

    Cluster.Role role() {
      return service.role;
    }

    @Override
    public void close() {
      CloseHelper.quietCloseAll(container, driver);
    }
  }

  // ── RoleAwarePongService ──────────────────────────────────────────────────

  /**
   * ClusteredService that echoes PONG for each PING and exposes the current Raft role. Used by all
   * three members; role changes propagate via {@code onRoleChange()}.
   */
  static final class RoleAwarePongService implements ClusteredService {

    volatile Cluster.Role role = Cluster.Role.FOLLOWER;
    private Cluster cluster;
    private final UnsafeBuffer pongBuffer = new UnsafeBuffer(new byte[4]);

    RoleAwarePongService() {
      pongBuffer.putStringWithoutLengthAscii(0, "PONG");
    }

    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage) {
      this.cluster = cluster;
      this.role = cluster.role();
    }

    @Override
    public void onSessionOpen(final ClientSession session, final long timestamp) {}

    @Override
    public void onSessionClose(
        final ClientSession session, final long timestamp, final CloseReason closeReason) {}

    @Override
    public void onSessionMessage(
        final ClientSession session,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header) {
      if (length == 4 && buffer.getStringWithoutLengthAscii(offset, 4).equals("PING")) {
        while (session.offer(pongBuffer, 0, 4) < 0) {
          cluster.idleStrategy().idle();
        }
      }
    }

    @Override
    public void onTimerEvent(final long correlationId, final long timestamp) {}

    @Override
    public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {}

    @Override
    public void onRoleChange(final Cluster.Role newRole) {
      this.role = newRole;
    }

    @Override
    public void onTerminate(final Cluster cluster) {}
  }
}
