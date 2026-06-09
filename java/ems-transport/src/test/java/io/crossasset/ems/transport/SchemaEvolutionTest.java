/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.schemas.evolution.MessageHeaderDecoder;
import io.crossasset.ems.schemas.evolution.MessageHeaderEncoder;
import io.crossasset.ems.schemas.evolution.SessionLogonDecoder;
import io.crossasset.ems.schemas.evolution.SessionLogonEncoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies SBE schema evolution (forward compatibility) for the {@code evolution-v2.xml} fixture.
 *
 * <p>The schema defines {@code SessionLogon} at version 2, where {@code correlationId} carries
 * {@code sinceVersion="2"}. The tests confirm:
 *
 * <ul>
 *   <li>A v2 writer encodes all three fields correctly.
 *   <li>A v1 reader ({@code actingVersion=1}) decodes the v1 fields correctly and receives the
 *       SBE-mandated {@code nullValue} ({@code 0xFFFFFFFFFFFFFFFFL}) for the absent v2 field.
 *   <li>A v2 reader ({@code actingVersion=2}) decodes all three fields correctly.
 * </ul>
 *
 * <p>Task 2.7 — Schema evolution test: old reader + new writer.
 */
class SchemaEvolutionTest {

  private static final int BUFFER_CAPACITY = 64;

  private UnsafeBuffer buffer;
  private MessageHeaderEncoder headerEncoder;
  private MessageHeaderDecoder headerDecoder;
  private SessionLogonEncoder encoder;

  @BeforeEach
  void setUp() {
    buffer = new UnsafeBuffer(new byte[BUFFER_CAPACITY]);
    headerEncoder = new MessageHeaderEncoder();
    headerDecoder = new MessageHeaderDecoder();
    encoder = new SessionLogonEncoder();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** Encode a v2 SessionLogon at buffer offset 0. Returns the end offset (header + body). */
  private int encodeV2(final long sessionId, final long seqNum, final long correlationId) {
    encoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
    headerEncoder.version(SessionLogonDecoder.SCHEMA_VERSION); // = 2

    encoder.sessionId(sessionId).seqNum(seqNum).correlationId(correlationId);

    return MessageHeaderEncoder.ENCODED_LENGTH + SessionLogonEncoder.BLOCK_LENGTH;
  }

  /** Wrap a decoder at offset 0 with the given actingVersion. */
  private SessionLogonDecoder decodeAt(final int actingVersion) {
    headerDecoder.wrap(buffer, 0);
    return new SessionLogonDecoder()
        .wrap(
            buffer,
            MessageHeaderDecoder.ENCODED_LENGTH,
            headerDecoder.blockLength(),
            actingVersion);
  }

  // ── tests ─────────────────────────────────────────────────────────────────

  /** v2 writer + v2 reader: all three fields decode at their written values. */
  @Test
  void v2Writer_v2Reader_allFieldsDecode() {
    encodeV2(42L, 7L, 999L);

    final SessionLogonDecoder decoder = decodeAt(2);
    assertEquals(42L, decoder.sessionId());
    assertEquals(7L, decoder.seqNum());
    assertEquals(999L, decoder.correlationId());
  }

  /**
   * v2 writer + v1 reader: v1 fields decode correctly. This is the forward-compatibility guarantee
   * from {@code arch-sbe-aeron-transport.md}.
   */
  @Test
  void v2Writer_v1Reader_v1FieldsDecodeCorrectly() {
    encodeV2(100L, 200L, 300L);

    final SessionLogonDecoder decoder = decodeAt(1);
    assertEquals(100L, decoder.sessionId(), "sessionId must decode for v1 reader");
    assertEquals(200L, decoder.seqNum(), "seqNum must decode for v1 reader");
  }

  /**
   * v2 writer + v1 reader: the sinceVersion=2 field returns nullValue when actingVersion < 2. Old
   * readers MUST NOT crash — they receive a defined sentinel instead.
   */
  @Test
  void v2Writer_v1Reader_v2FieldReturnsNullValue() {
    encodeV2(1L, 2L, 12345L);

    final SessionLogonDecoder decoder = decodeAt(1);
    assertEquals(
        SessionLogonDecoder.correlationIdNullValue(),
        decoder.correlationId(),
        "correlationId must return nullValue (0xFFFF…) when actingVersion < sinceVersion");
  }

  /**
   * Verifies the MessageHeader written by the v2 encoder carries version=2, so downstream consumers
   * can detect the schema version at decode time.
   */
  @Test
  void v2Writer_headerVersionField_isTwo() {
    encodeV2(0L, 0L, 0L);

    headerDecoder.wrap(buffer, 0);
    assertEquals(
        2,
        headerDecoder.version(),
        "MessageHeader.version must equal schema version 2 after v2 encode");
  }

  /**
   * Block length on-wire matches the v2 schema's BLOCK_LENGTH (3 × uint64 = 24 bytes). A v1 reader
   * advances past the full block even though it only reads 16 bytes of it.
   */
  @Test
  void v2Writer_blockLengthOnWire_is24() {
    encodeV2(0L, 0L, 0L);

    headerDecoder.wrap(buffer, 0);
    assertEquals(
        SessionLogonDecoder.BLOCK_LENGTH,
        headerDecoder.blockLength(),
        "blockLength on wire must be the v2 BLOCK_LENGTH (24) so v1 readers skip correctly");
  }
}
