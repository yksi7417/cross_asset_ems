package io.crossasset.ems.transport.log;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventLogWriterDiskReadbackTest {

  @TempDir Path tempDir;

  @Test
  void testDiskPersistence() throws IOException {
    Path logFile = tempDir.resolve("eventlog.bin");
    try (EventLogWriter writer = new EventLogWriter(logFile)) {
      UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(128));
      buffer.putBytes(0, "event-1".getBytes(StandardCharsets.UTF_8));
      writer.append(StreamId.ADMIN, buffer, 0, 7);
      buffer.putBytes(0, "event-2".getBytes(StandardCharsets.UTF_8));
      writer.append(StreamId.ADMIN, buffer, 0, 7);
    }

    try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
      ByteBuffer readBuf = ByteBuffer.allocate((int) channel.size());
      channel.read(readBuf);
      readBuf.flip();

      assertEquals(0L, readBuf.getLong(), "first record: globalSeq=0");
      assertEquals(0L, readBuf.getLong(), "first record: streamSeq=0");
      int idLen = readBuf.getInt();
      byte[] idBytes = new byte[idLen];
      readBuf.get(idBytes);
      assertEquals("admin", new String(idBytes, StandardCharsets.UTF_8));
      int payloadLen = readBuf.getInt();
      byte[] payloadBytes = new byte[payloadLen];
      readBuf.get(payloadBytes);
      assertEquals("event-1", new String(payloadBytes, StandardCharsets.UTF_8));
    }
  }

  @Test
  void testMultiStreamDiskLayout() throws IOException {
    Path logFile = tempDir.resolve("multistream.bin");
    StreamId orderStream = StreamId.of("order", "123");
    StreamId routeStream = StreamId.of("route", "456");

    try (EventLogWriter writer = new EventLogWriter(logFile)) {
      UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(64));
      buffer.putBytes(0, "o1".getBytes());
      writer.append(orderStream, buffer, 0, 2);
      buffer.putBytes(0, "r1".getBytes());
      writer.append(routeStream, buffer, 0, 2);
      buffer.putBytes(0, "o2".getBytes());
      writer.append(orderStream, buffer, 0, 2);
    }

    try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
      ByteBuffer readBuf = ByteBuffer.allocate((int) channel.size());
      channel.read(readBuf);
      readBuf.flip();

      // Record 0: order stream, gseq=0, sseq=0
      assertEquals(0L, readBuf.getLong());
      assertEquals(0L, readBuf.getLong());
      int idLen0 = readBuf.getInt();
      readBuf.position(readBuf.position() + idLen0 + 4 + 2);

      // Record 1: route stream, gseq=1, sseq=0
      assertEquals(1L, readBuf.getLong());
      assertEquals(0L, readBuf.getLong());
      int idLen1 = readBuf.getInt();
      readBuf.position(readBuf.position() + idLen1 + 4 + 2);

      // Record 2: order stream again, gseq=2, sseq=1
      assertEquals(2L, readBuf.getLong());
      assertEquals(1L, readBuf.getLong());
    }
  }

  @Test
  void testFileSizeGrowsWithEachAppend() throws IOException {
    Path logFile = tempDir.resolve("growth.bin");
    UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(64));

    try (EventLogWriter writer = new EventLogWriter(logFile)) {
      long size0 = Files.size(logFile);
      buffer.putBytes(0, "data1".getBytes());
      writer.append(StreamId.ADMIN, buffer, 0, 5);
      long size1 = Files.size(logFile);
      buffer.putBytes(0, "data2".getBytes());
      writer.append(StreamId.ADMIN, buffer, 0, 5);
      long size2 = Files.size(logFile);

      assertTrue(size1 > size0, "file should grow after first append");
      assertTrue(size2 > size1, "file should grow after second append");
    }
  }

  @Test
  void testEmptyFileOnOpen() throws IOException {
    Path logFile = tempDir.resolve("empty.bin");
    try (EventLogWriter writer = new EventLogWriter(logFile)) {
      assertEquals(0L, writer.getGlobalSeq(), "no events appended yet");
      assertEquals(0L, Files.size(logFile), "no bytes written yet");
    }
  }
}
