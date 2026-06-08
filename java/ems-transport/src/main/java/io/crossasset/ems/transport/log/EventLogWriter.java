package io.crossasset.ems.transport.log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * EventLogWriter provides an append-only, durable event log implementation.
 *
 * <p>Following arch-event-sourcing.md, this writer ensures that all events are persisted to disk
 * with strict fsync discipline before they are considered "recorded".
 */
public class EventLogWriter implements AutoCloseable {
  private static final Logger logger = Logger.getLogger(EventLogWriter.class.getName());

  private final FileChannel channel;
  private final RandomAccessFile raf;
  private final AtomicLong globalSeq = new AtomicLong(0);

  // Track sequence numbers per stream for partitioning
  private final Map<StreamId, Long> streamSequences = new ConcurrentHashMap<>();

  public EventLogWriter(Path logFile) throws IOException {
    this.raf = new RandomAccessFile(logFile.toFile(), "rw");
    this.channel = raf.getChannel();
    this.globalSeq.set(recoverGlobalSeq());
    recoverStreamSequences();
  }

  private long recoverGlobalSeq() {
    // Skeleton: start at 0. Real impl would read trailer.
    return 0;
  }

  private void recoverStreamSequences() {
    // Skeleton: currently empty. Real impl would scan the log or read an index.
  }

  /**
   * Appends an SBE-encoded event to the log for a specific stream.
   *
   * @param streamId The stream this event belongs to.
   * @param buffer The buffer containing the SBE-encoded event.
   * @param offset The offset within the buffer to start reading.
   * @param length The length of the event in bytes.
   * @return The global sequence number assigned to this event.
   * @throws IOException if writing or syncing fails.
   */
  public synchronized long append(StreamId streamId, UnsafeBuffer buffer, int offset, int length)
      throws IOException {
    long gSeq = globalSeq.getAndIncrement();
    long sSeq = streamSequences.compute(streamId, (id, seq) -> (seq == null) ? 0L : seq + 1);

    // Record format:
    // [GlobalSeq(8)][StreamSeq(8)][StreamIdLen(4)][StreamId(N)][PayloadLen(4)][Payload(M)]
    byte[] streamIdBytes = streamId.value().getBytes(StandardCharsets.UTF_8);
    int recordSize = 8 + 8 + 4 + streamIdBytes.length + 4 + length;

    ByteBuffer record = ByteBuffer.allocate(recordSize);
    record.putLong(gSeq);
    record.putLong(sSeq);
    record.putInt(streamIdBytes.length);
    record.put(streamIdBytes);
    record.putInt(length);

    // Payload
    ByteBuffer payload = buffer.byteBuffer().slice(offset, length);
    record.put(payload);

    record.flip();
    while (record.hasRemaining()) {
      channel.write(record);
    }

    // Strict durability: force metadata and data to disk (fsync)
    channel.force(true);

    return gSeq;
  }

  @Override
  public void close() throws IOException {
    if (channel != null) channel.close();
    if (raf != null) raf.close();
  }

  public long getGlobalSeq() {
    return globalSeq.get();
  }
}
