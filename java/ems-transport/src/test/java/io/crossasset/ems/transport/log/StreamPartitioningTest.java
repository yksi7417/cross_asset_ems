package io.crossasset.ems.transport.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamPartitioningTest {

  @TempDir Path tempDir;

  @Test
  void testStreamPartitioning() throws IOException {
    Path logFile = tempDir.resolve("partitioned.log");
    try (EventLogWriter writer = new EventLogWriter(logFile)) {
      UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(100));

      StreamId orderStream = StreamId.of("order", "ord-123");
      StreamId routeStream = StreamId.of("route", "rt-456");

      // Event 1: Order stream
      buffer.putBytes(0, "order-event-1".getBytes());
      long gSeq1 = writer.append(orderStream, buffer, 0, 13);
      assertThat(gSeq1).isEqualTo(0);

      // Event 2: Route stream
      buffer.putBytes(0, "route-event-1".getBytes());
      long gSeq2 = writer.append(routeStream, buffer, 0, 13);
      assertThat(gSeq2).isEqualTo(1);

      // Event 3: Order stream again
      buffer.putBytes(0, "order-event-2".getBytes());
      long gSeq3 = writer.append(orderStream, buffer, 0, 13);
      assertThat(gSeq3).isEqualTo(2);

      assertThat(writer.getGlobalSeq()).isEqualTo(3);
    }
  }
}
