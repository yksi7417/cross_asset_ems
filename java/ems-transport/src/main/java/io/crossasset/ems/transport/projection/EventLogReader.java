/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

import io.crossasset.ems.transport.log.StreamId;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reads {@link LogRecord}s from a log file written by {@link
 * io.crossasset.ems.transport.log.EventLogWriter}.
 *
 * <p>Records are read sequentially. The on-disk framing uses big-endian byte order (Java {@link
 * java.nio.ByteBuffer} default), matching the writer. Truncated or corrupt records stop iteration
 * with a warning rather than throwing.
 *
 * <p>Task 3.4 — Projection framework.
 */
public class EventLogReader implements AutoCloseable {

  private static final Logger LOGGER = Logger.getLogger(EventLogReader.class.getName());

  private static final int MAX_STREAM_ID_LEN = 512;
  private static final int MAX_PAYLOAD_LEN = 16 * 1024 * 1024; // 16 MiB sanity cap

  private final FileChannel channel;

  public EventLogReader(Path logFile) throws IOException {
    this.channel = FileChannel.open(logFile, StandardOpenOption.READ);
  }

  /**
   * Returns all records in the log in globalSeq order.
   *
   * @throws IOException on I/O failure
   */
  public List<LogRecord> readAll() throws IOException {
    channel.position(0);
    return readFrom(Long.MIN_VALUE);
  }

  /**
   * Returns records whose {@code globalSeq >= minGlobalSeq}. Scans from the start of the file
   * (there is no index — this is O(n) in the total record count).
   *
   * @param minGlobalSeq inclusive lower bound
   * @throws IOException on I/O failure
   */
  public List<LogRecord> readFrom(long minGlobalSeq) throws IOException {
    channel.position(0);
    List<LogRecord> records = new ArrayList<>();
    while (channel.position() < channel.size()) {
      LogRecord record = readNextRecord();
      if (record == null) break;
      if (record.globalSeq() >= minGlobalSeq) {
        records.add(record);
      }
    }
    return records;
  }

  private LogRecord readNextRecord() throws IOException {
    // Fixed header: globalSeq(8) + streamSeq(8) + streamIdLen(4) = 20 bytes
    ByteBuffer header = ByteBuffer.allocate(20);
    int headerRead = readFully(header);
    if (headerRead == 0) return null; // clean EOF
    if (headerRead < 20) {
      LOGGER.warning("Truncated record header at channel position " + channel.position());
      return null;
    }
    header.flip();
    long globalSeq = header.getLong();
    long streamSeq = header.getLong();
    int streamIdLen = header.getInt();

    if (streamIdLen <= 0 || streamIdLen > MAX_STREAM_ID_LEN) {
      LOGGER.warning("Invalid streamIdLen=" + streamIdLen + " at seq=" + globalSeq);
      return null;
    }

    ByteBuffer streamIdBuf = ByteBuffer.allocate(streamIdLen);
    if (readFully(streamIdBuf) < streamIdLen) {
      LOGGER.warning("Truncated streamId at seq=" + globalSeq);
      return null;
    }
    streamIdBuf.flip();
    String streamIdValue = StandardCharsets.UTF_8.decode(streamIdBuf).toString();

    ByteBuffer payloadLenBuf = ByteBuffer.allocate(4);
    if (readFully(payloadLenBuf) < 4) {
      LOGGER.warning("Truncated payloadLen at seq=" + globalSeq);
      return null;
    }
    payloadLenBuf.flip();
    int payloadLen = payloadLenBuf.getInt();

    if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_LEN) {
      LOGGER.warning("Invalid payloadLen=" + payloadLen + " at seq=" + globalSeq);
      return null;
    }

    byte[] payload = new byte[payloadLen];
    ByteBuffer payloadBuf = ByteBuffer.wrap(payload);
    if (readFully(payloadBuf) < payloadLen) {
      LOGGER.warning("Truncated payload at seq=" + globalSeq);
      return null;
    }

    return new LogRecord(globalSeq, streamSeq, new StreamId(streamIdValue), payload);
  }

  /** Reads until {@code buf} is full or EOF. Returns bytes actually read. */
  private int readFully(ByteBuffer buf) throws IOException {
    int total = 0;
    while (buf.hasRemaining()) {
      int n = channel.read(buf);
      if (n == -1) break;
      total += n;
    }
    return total;
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
