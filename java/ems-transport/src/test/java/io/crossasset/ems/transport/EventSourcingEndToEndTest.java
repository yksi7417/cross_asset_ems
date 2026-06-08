package io.crossasset.ems.transport;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.schemas.event.ActorKind;
import io.crossasset.ems.schemas.event.EventDecoder;
import io.crossasset.ems.schemas.event.EventEncoder;
import io.crossasset.ems.schemas.event.EventSource;
import io.crossasset.ems.schemas.event.MessageHeaderDecoder;
import io.crossasset.ems.schemas.event.MessageHeaderEncoder;
import io.crossasset.ems.schemas.event.StreamKind;
import io.crossasset.ems.transport.log.EventLogWriter;
import io.crossasset.ems.transport.log.StreamId;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end smoke test for Phase 2-3 features: - 2.4 SequenceRecoveryService: session logon and
 * gap detection - 3.1 Event SBE schema: encode/decode round-trip via generated EventEncoder/Decoder
 * - 3.2 EventLogWriter: append-only durable log - 3.3 Stream-id partitioning: per-stream
 * independent sequencing
 */
@Tag("smoke")
class EventSourcingEndToEndTest {

  @TempDir Path tempDir;

  @Test
  void smokeTest_sessionLogonAndEventLog() throws IOException {
    // 2.4 — session recovery service
    SequenceRecoveryService recovery = new SequenceRecoveryService();
    long sessionId = 42L;

    var logon = recovery.logon(sessionId, 1L);
    assertTrue(logon.success(), "session logon should succeed");

    // in-order messages should pass
    assertTrue(recovery.checkSequence(sessionId, 1L).isEmpty());
    assertTrue(recovery.checkSequence(sessionId, 2L).isEmpty());

    // gap detection
    assertTrue(recovery.checkSequence(sessionId, 10L).isPresent());

    // 3.2 + 3.3 — event log writer with stream partitioning
    Path logFile = tempDir.resolve("smoke.log");
    StreamId orderStream = StreamId.of("order", "ord-001");
    StreamId routeStream = StreamId.of("route", "rte-001");
    StreamId sessionStream = StreamId.of("session", String.valueOf(sessionId));

    UnsafeBuffer buf = new UnsafeBuffer(ByteBuffer.allocateDirect(256));

    try (EventLogWriter writer = new EventLogWriter(logFile)) {
      // Write events across three streams
      buf.putBytes(0, "OrderNew".getBytes(StandardCharsets.UTF_8));
      long g0 = writer.append(orderStream, buf, 0, 8);

      buf.putBytes(0, "RouteNew".getBytes(StandardCharsets.UTF_8));
      long g1 = writer.append(routeStream, buf, 0, 8);

      buf.putBytes(0, "SessionLogon".getBytes(StandardCharsets.UTF_8));
      long g2 = writer.append(sessionStream, buf, 0, 12);

      buf.putBytes(0, "OrderFill".getBytes(StandardCharsets.UTF_8));
      long g3 = writer.append(orderStream, buf, 0, 9);

      // Global sequence is monotonic across all streams
      assertEquals(0L, g0);
      assertEquals(1L, g1);
      assertEquals(2L, g2);
      assertEquals(3L, g3);

      assertEquals(4L, writer.getGlobalSeq());
    }

    // Verify on-disk layout: read back and check global seqs and stream seqs
    try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
      ByteBuffer readBuf = ByteBuffer.allocate((int) channel.size());
      channel.read(readBuf);
      readBuf.flip();

      // Record 0: order stream, gseq=0, sseq=0
      assertEquals(0L, readBuf.getLong(), "r0 globalSeq");
      assertEquals(0L, readBuf.getLong(), "r0 streamSeq — first event in order stream");
      skipRecord(readBuf);

      // Record 1: route stream, gseq=1, sseq=0
      assertEquals(1L, readBuf.getLong(), "r1 globalSeq");
      assertEquals(0L, readBuf.getLong(), "r1 streamSeq — first event in route stream");
      skipRecord(readBuf);

      // Record 2: session stream, gseq=2, sseq=0
      assertEquals(2L, readBuf.getLong(), "r2 globalSeq");
      assertEquals(0L, readBuf.getLong(), "r2 streamSeq — first event in session stream");
      skipRecord(readBuf);

      // Record 3: order stream, gseq=3, sseq=1 (second event in order stream)
      assertEquals(3L, readBuf.getLong(), "r3 globalSeq");
      assertEquals(1L, readBuf.getLong(), "r3 streamSeq — second event in order stream");
    }
  }

  @Test
  void smokeTest_sbeEventCodecRoundTrip() {
    // 3.1 — real SBE encode → decode round-trip using generated EventEncoder/EventDecoder.
    // Verifies that the event.xml schema produces working codecs, not just that the file exists.
    UnsafeBuffer buf = new UnsafeBuffer(ByteBuffer.allocateDirect(512));

    MessageHeaderEncoder headerEnc = new MessageHeaderEncoder();
    EventEncoder encoder = new EventEncoder();
    encoder.wrapAndApplyHeader(buf, 0, headerEnc);

    encoder
        .globalSeq(42L)
        .streamKind(StreamKind.ORDER)
        .streamSeq(7L)
        .payloadTemplateId(100)
        .payloadSchemaId(2)
        .payloadVersion(1)
        .occurredAt(1_000_000L)
        .recordedAt(2_000_000L)
        .source(EventSource.API)
        .actorKind(ActorKind.HUMAN)
        .traceFlags((short) 1)
        .streamId("order.ord-001")
        .putPayload("hello".getBytes(StandardCharsets.UTF_8), 0, 5);

    MessageHeaderDecoder headerDec = new MessageHeaderDecoder();
    EventDecoder decoder = new EventDecoder();
    headerDec.wrap(buf, 0);
    decoder.wrap(
        buf, MessageHeaderDecoder.ENCODED_LENGTH, headerDec.blockLength(), headerDec.version());

    assertEquals(42L, decoder.globalSeq(), "globalSeq round-trip");
    assertEquals(StreamKind.ORDER, decoder.streamKind(), "streamKind round-trip");
    assertEquals(7L, decoder.streamSeq(), "streamSeq round-trip");
    assertEquals(EventSource.API, decoder.source(), "source round-trip");
    assertEquals(ActorKind.HUMAN, decoder.actorKind(), "actorKind round-trip");
    assertEquals("order.ord-001", decoder.streamId(), "streamId round-trip");

    byte[] payloadOut = new byte[5];
    decoder.getPayload(payloadOut, 0, 5);
    assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), payloadOut, "payload round-trip");
  }

  /** Skip past the variable-length streamId and payload fields of a record. */
  private static void skipRecord(ByteBuffer buf) {
    int idLen = buf.getInt();
    buf.position(buf.position() + idLen);
    int payloadLen = buf.getInt();
    buf.position(buf.position() + payloadLen);
  }
}
