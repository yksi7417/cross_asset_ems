/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

import io.crossasset.ems.transport.log.StreamId;

/**
 * A single record parsed from the event log.
 *
 * <p>The framing layout mirrors {@link io.crossasset.ems.transport.log.EventLogWriter}:
 *
 * <pre>
 * [globalSeq(8)][streamSeq(8)][streamIdLen(4)][streamId(N)][payloadLen(4)][payload(M)]
 * </pre>
 *
 * All integer/long fields are big-endian (Java {@link java.nio.ByteBuffer} default).
 *
 * <p>Task 3.4 — Projection framework.
 */
public record LogRecord(long globalSeq, long streamSeq, StreamId streamId, byte[] payload) {}
