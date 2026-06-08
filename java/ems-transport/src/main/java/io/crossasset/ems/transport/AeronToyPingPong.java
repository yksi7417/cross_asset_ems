/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import io.aeron.Aeron;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Single-node Aeron Cluster + Archive toy demonstrating ping/pong round-trips.
 *
 * <p>Launches a self-elected single-member cluster (quorum of 1, {@code appointedLeaderId=0}),
 * connects a client, exchanges PING/PONG messages, and verifies the Archive recorded the log.
 *
 * <p>Run: {@code ./gradlew :ems-transport:run} or use from tests.
 *
 * <p>Blocks: 2.5 (3-node Raft consensus), 0.10 (CI smoke test).
 */
public final class AeronToyPingPong implements AutoCloseable {

  /** Single member; self-elected via {@code appointedLeaderId}. */
  static final int MEMBER_ID = 0;

  static final int INGRESS_PORT = 29110;
  static final int MEMBER_PORT = 29111;
  static final int LOG_PORT = 29112;
  static final int TRANSFER_PORT = 29113;
  static final int ARCHIVE_PORT = 29114;
  static final int REPLICATION_PORT = 29115;
  static final int EGRESS_PORT = 29120;

  /**
   * Cluster member string: memberId, clientIngress, memberIngress, log, transfer, archiveControl.
   * The archive control endpoint MUST match {@code Archive.Context.controlChannel()}.
   */
  static final String CLUSTER_MEMBERS =
      MEMBER_ID
          + ",localhost:"
          + INGRESS_PORT
          + ",localhost:"
          + MEMBER_PORT
          + ",localhost:"
          + LOG_PORT
          + ",localhost:"
          + TRANSFER_PORT
          + ",localhost:"
          + ARCHIVE_PORT;

  private final File aeronDir;
  private final File archiveDir;
  private final File clusterDir;

  private ClusteredMediaDriver clusteredMediaDriver;
  private ClusteredServiceContainer serviceContainer;

  public AeronToyPingPong(final File baseDir) {
    this.aeronDir = new File(baseDir, "aeron");
    this.archiveDir = new File(baseDir, "archive");
    this.clusterDir = new File(baseDir, "cluster");
  }

  /**
   * Launches the single-node cluster and waits for the service to be ready. The cluster is
   * self-electing; {@code AeronCluster.connect()} will block until ingress is available.
   */
  public void start() {
    clusteredMediaDriver =
        ClusteredMediaDriver.launch(
            new MediaDriver.Context()
                .aeronDirectoryName(aeronDir.getAbsolutePath())
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true),
            new Archive.Context()
                .aeronDirectoryName(aeronDir.getAbsolutePath())
                .archiveDir(archiveDir)
                .controlChannel("aeron:udp?endpoint=localhost:" + ARCHIVE_PORT)
                .localControlChannel("aeron:ipc?term-length=64k")
                .replicationChannel("aeron:udp?endpoint=localhost:" + REPLICATION_PORT)
                .threadingMode(ArchiveThreadingMode.SHARED)
                .deleteArchiveOnStart(true),
            new ConsensusModule.Context()
                .aeronDirectoryName(aeronDir.getAbsolutePath())
                .clusterDir(clusterDir)
                .clusterMemberId(MEMBER_ID)
                .appointedLeaderId(MEMBER_ID)
                .clusterMembers(CLUSTER_MEMBERS)
                .ingressChannel("aeron:udp?term-length=64k")
                .replicationChannel("aeron:udp?endpoint=localhost:" + REPLICATION_PORT)
                .deleteDirOnStart(true));

    serviceContainer =
        ClusteredServiceContainer.launch(
            new ClusteredServiceContainer.Context()
                .aeronDirectoryName(aeronDir.getAbsolutePath())
                .clusterDir(clusterDir)
                .clusteredService(new PongService()));
  }

  /**
   * Sends {@code count} PING messages and collects RTT measurements (nanoseconds). Connects a
   * fresh client for this run; the client is closed when done.
   */
  public List<Long> runPingPong(final int count) {
    final List<Long> rttsNs = new ArrayList<>(count);
    final AtomicInteger pongsReceived = new AtomicInteger(0);
    final UnsafeBuffer pingBuffer = new UnsafeBuffer(new byte[4]);
    pingBuffer.putStringWithoutLengthAscii(0, "PING");

    final EgressListener egressListener =
        (clusterSessionId, timestamp, buffer, offset, length, header) -> {
          if (length == 4 && buffer.getStringWithoutLengthAscii(offset, 4).equals("PONG")) {
            pongsReceived.incrementAndGet();
          }
        };

    try (Aeron aeron =
            Aeron.connect(
                new Aeron.Context().aeronDirectoryName(aeronDir.getAbsolutePath()));
        AeronCluster client =
            AeronCluster.connect(
                new AeronCluster.Context()
                    .aeron(aeron)
                    .egressListener(egressListener)
                    .ingressChannel("aeron:udp?term-length=64k")
                    .ingressEndpoints(MEMBER_ID + "=localhost:" + INGRESS_PORT)
                    .egressChannel("aeron:udp?endpoint=localhost:" + EGRESS_PORT))) {

      for (int i = 0; i < count; i++) {
        final int pongsBefore = pongsReceived.get();
        final long startNs = System.nanoTime();

        while (client.offer(pingBuffer, 0, 4) < 0) {
          client.pollEgress();
        }

        while (pongsReceived.get() == pongsBefore) {
          client.pollEgress();
        }

        rttsNs.add(System.nanoTime() - startNs);
      }
    }

    return rttsNs;
  }

  /** Returns true if the Archive directory contains at least one file (log was recorded). */
  public boolean hasArchiveRecordings() {
    final File[] files = archiveDir.listFiles();
    return files != null && files.length > 0;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(serviceContainer, clusteredMediaDriver);
  }

  public static void main(final String[] args) throws Exception {
    // Fixed dir so you can inspect the archive after the toy exits.
    // Override with: java ... AeronToyPingPong /your/path
    final File baseDir =
        args.length > 0 ? new File(args[0]) : new File(System.getProperty("java.io.tmpdir"), "ems-aeron-demo");
    baseDir.mkdirs();
    System.out.println("Base dir : " + baseDir.getAbsolutePath());
    System.out.println("Archive  : " + new File(baseDir, "archive").getAbsolutePath());

    try (AeronToyPingPong toy = new AeronToyPingPong(baseDir)) {
      toy.start();
      final int rounds = 10;
      final List<Long> rtts = toy.runPingPong(rounds);
      System.out.println("=== Aeron Cluster + Archive toy ===");
      for (int i = 0; i < rtts.size(); i++) {
        System.out.printf("  PING %2d → PONG  RTT = %,d μs%n", i + 1, rtts.get(i) / 1_000);
      }
      final long avgNs = rtts.stream().mapToLong(Long::longValue).sum() / rtts.size();
      System.out.printf("  Avg RTT : %,d μs%n", avgNs / 1_000);
      System.out.printf("  Archive : %s%n", toy.hasArchiveRecordings() ? "recorded ✓" : "EMPTY ✗");
      System.out.println();
      System.out.println("Press Enter to close (inspect archive files before closing)...");
      System.in.read();
    }
    System.out.println("Done.");
  }
}
