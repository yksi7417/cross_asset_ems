package io.crossasset.ems.transport.log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
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

  public EventLogWriter(Path logFile) throws IOException {
    this.raf = new RandomAccessFile(logFile.toFile(), "rw");
    this.channel = raf.getChannel();
    this.globalSeq.set(recoverGlobalSeq());
  }

  private long recoverGlobalSeq() {
    return 0;
  }

  /**
   * Appends an SBE-encoded event to the log.
   *
   * @param buffer The buffer containing the SBE-encoded event.
   * @param offset The offset within the buffer to start reading.
   * @param length The length of the event in bytes.
   * @return The global sequence number assigned to this event.
   * @throws IOException if writing or syncing fails.
   */
  public synchronized long append(UnsafeBuffer buffer, int offset, int length) throws IOException {
    long seq = globalSeq.getAndIncrement();

    ByteBuffer slice = buffer.byteBuffer().slice(offset, length);
    while (slice.hasRemaining()) {
      channel.write(slice);
    }

    // Strict durability: force metadata and data to disk (fsync)
    channel.force(true);

    return seq;
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
