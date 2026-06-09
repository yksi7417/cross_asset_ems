/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Recording and replay operations over {@link AeronArchive}.
 *
 * <p>Wraps the three core operations the EMS depends on:
 *
 * <ol>
 *   <li><b>Record:</b> attach a recording to a live Aeron channel/stream.
 *   <li><b>Find:</b> locate the recording ID for a channel/stream after it starts.
 *   <li><b>Replay:</b> play back a recording from an arbitrary byte-position, returning a ready
 *       {@link Subscription}.
 * </ol>
 *
 * <p>Callers are responsible for closing the {@link Subscription} returned by {@link
 * #replayAll(long, long, String, int)} when done. The service does not own the {@link AeronArchive}
 * handle passed at construction.
 *
 * <p>Task 2.6 — Aeron Archive recording + replay APIs (sonnet).
 */
public final class AeronArchiveReplayService {

  /** Descriptor for a persisted recording: ID and the byte-range it covers. */
  public record RecordingInfo(long recordingId, long startPosition, long stopPosition) {}

  private final AeronArchive archive;

  public AeronArchiveReplayService(final AeronArchive archive) {
    this.archive = archive;
  }

  /**
   * Starts recording all messages published locally on {@code channel}/{@code streamId}.
   *
   * @return subscriptionId — pass to {@link #stopRecording(long)} to end the recording
   */
  public long startLocalRecording(final String channel, final int streamId) {
    return archive.startRecording(channel, streamId, SourceLocation.LOCAL);
  }

  /** Stops a recording identified by its {@code subscriptionId}. */
  public void stopRecording(final long subscriptionId) {
    archive.stopRecording(subscriptionId);
  }

  /**
   * Polls the Archive until a recording matching {@code streamId} appears or the timeout elapses.
   *
   * <p>Uses {@code listRecordings} (full scan, no URI filter) because {@code
   * findLastMatchingRecording} returns {@link Aeron#NULL_VALUE} even for valid matches in Aeron
   * 1.51 when the recording ID is 0.
   *
   * @return the most recent matching {@link RecordingInfo}, or empty if none found within timeout
   */
  public Optional<RecordingInfo> findLatestRecording(
      final String channel, final int streamId, final long timeoutMs) throws InterruptedException {
    final long deadlineMs = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadlineMs) {
      final RecordingInfo[] best = {null};
      archive.listRecordings(
          0,
          Integer.MAX_VALUE,
          (ctrlSess,
              corr,
              recordingId,
              startTs,
              stopTs,
              startPos,
              stopPos,
              initTermId,
              segLen,
              termLen,
              mtu,
              sessId,
              strmId,
              stripped,
              original,
              src) -> {
            if (strmId == streamId && (best[0] == null || recordingId > best[0].recordingId())) {
              best[0] = new RecordingInfo(recordingId, startPos, stopPos);
            }
          });
      if (best[0] != null) {
        return Optional.of(best[0]);
      }
      Thread.sleep(50);
    }
    return Optional.empty();
  }

  /**
   * Replays a recording from {@code startPosition} to the end of the current recording, returning a
   * connected {@link Subscription}.
   *
   * <p>The Archive sends frames to {@code replayChannel}/{@code replayStreamId}. The caller polls
   * the subscription until all expected messages are received, then closes it.
   *
   * @param recordingId the recording to replay
   * @param startPosition byte position to start from (0 = beginning)
   * @param replayChannel Aeron channel to deliver replayed frames on (e.g. {@code aeron:ipc})
   * @param replayStreamId stream ID for the replayed frames
   * @return a {@link Subscription} connected to the replay stream
   */
  public Subscription replayAll(
      final long recordingId,
      final long startPosition,
      final String replayChannel,
      final int replayStreamId) {
    return archive.replay(
        recordingId, startPosition, AeronArchive.NULL_LENGTH, replayChannel, replayStreamId);
  }

  /**
   * Polls until the recording position advances past {@code targetPosition} or the timeout elapses.
   * Useful for confirming that messages written before calling this have been recorded to disk.
   *
   * @return true if the position was reached, false on timeout
   */
  public boolean awaitRecordingPosition(
      final long recordingId, final long targetPosition, final long timeoutMs)
      throws InterruptedException {
    final long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    while (System.nanoTime() < deadlineNs) {
      final long pos = archive.getRecordingPosition(recordingId);
      if (pos != AeronArchive.NULL_POSITION && pos >= targetPosition) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }
}
