/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import static org.junit.jupiter.api.Assertions.*;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link AeronArchiveReplayService}: recording + byte-precise replay over Aeron Archive.
 *
 * <p>Uses a standalone MediaDriver + Archive (no Cluster) with IPC control channels to stay
 * in-process and avoid port conflicts. Two streams are used to keep recordings separate:
 *
 * <ul>
 *   <li>Stream 2001: publication/recording channel
 *   <li>Stream 2002: replay delivery channel
 * </ul>
 *
 * <p>Task 2.6.
 */
@Tag("archive-integration")
@Timeout(30)
class AeronArchiveReplayServiceTest {

  private static final String CHANNEL = "aeron:ipc";
  private static final int RECORD_STREAM = 2001;
  private static final int REPLAY_STREAM = 2002;

  @TempDir File baseDir;

  private MediaDriver driver;
  private Archive archive;
  private Aeron aeron;
  private AeronArchive aeronArchive;
  private AeronArchiveReplayService service;

  @BeforeEach
  void setUp() {
    final File aeronDir = new File(baseDir, "aeron");
    final File archiveDir = new File(baseDir, "archive");

    driver =
        MediaDriver.launch(
            new MediaDriver.Context()
                .aeronDirectoryName(aeronDir.getAbsolutePath())
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));

    archive =
        Archive.launch(
            new Archive.Context()
                .aeronDirectoryName(aeronDir.getAbsolutePath())
                .archiveDir(archiveDir)
                .controlChannel("aeron:udp?endpoint=localhost:29260")
                // localControlChannel default is "aeron:ipc?term-length=64k"; keep it so the
                // AeronArchive client's default controlRequestChannel matches exactly.
                .replicationChannel("aeron:udp?endpoint=localhost:29261")
                .threadingMode(ArchiveThreadingMode.SHARED)
                .deleteArchiveOnStart(true));

    aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(aeronDir.getAbsolutePath()));

    aeronArchive =
        AeronArchive.connect(
            new AeronArchive.Context()
                .aeron(aeron)
                // Match Archive.Context.localControlChannel default ("aeron:ipc?term-length=64k").
                .controlRequestChannel("aeron:ipc?term-length=64k")
                .controlResponseChannel("aeron:ipc?term-length=64k"));

    service = new AeronArchiveReplayService(aeronArchive);
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(aeronArchive, aeron, archive, driver);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static UnsafeBuffer msg(final String text) {
    final byte[] bytes = text.getBytes();
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[bytes.length]);
    buf.putBytes(0, bytes);
    return buf;
  }

  private static void offerAll(final ExclusivePublication pub, final String... messages) {
    for (final String text : messages) {
      final UnsafeBuffer buf = msg(text);
      long result;
      do {
        result = pub.offer(buf, 0, buf.capacity());
      } while (result < 0);
    }
  }

  private List<String> pollReplay(final Subscription sub, final int expected, final long deadlineMs)
      throws InterruptedException {
    final List<String> received = new ArrayList<>();
    while (received.size() < expected && System.currentTimeMillis() < deadlineMs) {
      sub.poll(
          (buf, offset, len, header) -> received.add(buf.getStringWithoutLengthAscii(offset, len)),
          10);
      if (received.size() < expected) {
        Thread.sleep(5);
      }
    }
    return received;
  }

  // ── Tests ─────────────────────────────────────────────────────────────────

  /**
   * Write 5 messages to a recorded stream, then replay all from position 0 and verify all 5
   * messages are received in order.
   */
  @Test
  void recordAndReplayFromStart_recoversAllMessages() throws Exception {
    final long subscriptionId = service.startLocalRecording(CHANNEL, RECORD_STREAM);

    try (ExclusivePublication pub = aeron.addExclusivePublication(CHANNEL, RECORD_STREAM)) {
      // Wait for recording subscription to connect to the publication.
      final long connectDeadline = System.currentTimeMillis() + 5_000;
      while (!pub.isConnected() && System.currentTimeMillis() < connectDeadline) {
        Thread.sleep(10);
      }
      assertTrue(pub.isConnected(), "Publication not connected to Archive recording");

      offerAll(pub, "msg-0", "msg-1", "msg-2", "msg-3", "msg-4");

      // Confirm all 5 messages have been recorded before stopping.
      final long targetPos = pub.position();
      final AeronArchiveReplayService.RecordingInfo info =
          service.findLatestRecording(CHANNEL, RECORD_STREAM, 10_000).orElseThrow();
      assertTrue(
          service.awaitRecordingPosition(info.recordingId(), targetPos, 10_000),
          "Archive did not record all messages within timeout");
    }

    service.stopRecording(subscriptionId);

    // Find the completed recording.
    final AeronArchiveReplayService.RecordingInfo info =
        service.findLatestRecording(CHANNEL, RECORD_STREAM, 5_000).orElseThrow();

    // Replay from the start.
    try (Subscription replaySub =
        service.replayAll(info.recordingId(), info.startPosition(), CHANNEL, REPLAY_STREAM)) {

      final List<String> received = pollReplay(replaySub, 5, System.currentTimeMillis() + 10_000);

      assertEquals(5, received.size(), "Expected 5 replayed messages");
      assertEquals(List.of("msg-0", "msg-1", "msg-2", "msg-3", "msg-4"), received);
    }
  }

  /**
   * Write 10 messages, capture the byte-position after the first 5, then replay only from that
   * mid-position. Verifies byte-precise seeking — the foundation for time-replay (task 3.6).
   */
  @Test
  void replayFromMidPosition_returnsOnlyTailMessages() throws Exception {
    final long subscriptionId = service.startLocalRecording(CHANNEL, RECORD_STREAM);
    long midPosition;

    try (ExclusivePublication pub = aeron.addExclusivePublication(CHANNEL, RECORD_STREAM)) {
      final long connectDeadline = System.currentTimeMillis() + 5_000;
      while (!pub.isConnected() && System.currentTimeMillis() < connectDeadline) {
        Thread.sleep(10);
      }
      assertTrue(pub.isConnected(), "Publication not connected");

      // First half.
      offerAll(pub, "a-0", "a-1", "a-2", "a-3", "a-4");
      midPosition = pub.position();

      // Second half.
      offerAll(pub, "b-0", "b-1", "b-2", "b-3", "b-4");

      final long endPosition = pub.position();
      final AeronArchiveReplayService.RecordingInfo info =
          service.findLatestRecording(CHANNEL, RECORD_STREAM, 10_000).orElseThrow();
      assertTrue(
          service.awaitRecordingPosition(info.recordingId(), endPosition, 10_000),
          "Archive did not record all messages");
    }

    service.stopRecording(subscriptionId);

    final AeronArchiveReplayService.RecordingInfo info =
        service.findLatestRecording(CHANNEL, RECORD_STREAM, 5_000).orElseThrow();

    // Replay only the second half by seeking to midPosition.
    try (Subscription replaySub =
        service.replayAll(info.recordingId(), midPosition, CHANNEL, REPLAY_STREAM)) {

      final List<String> received = pollReplay(replaySub, 5, System.currentTimeMillis() + 10_000);

      assertEquals(5, received.size(), "Expected 5 tail messages from mid-position replay");
      assertEquals(List.of("b-0", "b-1", "b-2", "b-3", "b-4"), received);
    }
  }
}
