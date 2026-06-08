/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cluster-side service: receives PING messages and echoes PONG. */
final class PongService implements ClusteredService {

  private static final Logger log = LoggerFactory.getLogger(PongService.class);

  private final UnsafeBuffer pongBuffer = new UnsafeBuffer(new byte[4]);
  private Cluster cluster;

  PongService() {
    pongBuffer.putStringWithoutLengthAscii(0, "PONG");
  }

  @Override
  public void onStart(final Cluster cluster, final Image snapshotImage) {
    this.cluster = cluster;
    log.info("PongService started, role={}", cluster.role());
  }

  @Override
  public void onSessionOpen(final ClientSession session, final long timestamp) {
    log.debug("Client session opened: id={}", session.id());
  }

  @Override
  public void onSessionClose(
      final ClientSession session, final long timestamp, final CloseReason closeReason) {
    log.debug("Client session closed: id={} reason={}", session.id(), closeReason);
  }

  @Override
  public void onSessionMessage(
      final ClientSession session,
      final long timestamp,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final Header header) {
    if (length == 4 && buffer.getStringWithoutLengthAscii(offset, 4).equals("PING")) {
      // timestamp = cluster logical time in ms (same unit as Cluster.timeUnit(), defaults to ms).
      // This is the wall-clock time at which the ConsensusModule committed this message to the
      // Raft log. All cluster members see the same value for the same message — deterministic.
      log.info("PING committed at cluster time {}ms (session={})", timestamp, session.id());
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
    log.info("PongService role changed to {}", newRole);
  }

  @Override
  public void onTerminate(final Cluster cluster) {}
}
