package io.crossasset.ems.transport.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventLogWriterTest {

  @TempDir Path tempDir;

  @Test
  void testAppendAndPersistence() throws IOException {
    Path logFile = tempDir.resolve("event.log");
    try (EventLogWriter writer = new EventLogWriter(logFile)) {
      UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(100));
      buffer.putBytes(0, "event-1".getBytes());

      long seq1 = writer.append(buffer, 0, 7);
      assertThat(seq1).isEqualTo(0);

      buffer.putBytes(0, "event-2".getBytes());
      long seq2 = writer.append(buffer, 0, 7);
      assertThat(seq2).isEqualTo(1);

      assertThat(writer.getGlobalSeq()).isEqualTo(2);
    }
  }
}
