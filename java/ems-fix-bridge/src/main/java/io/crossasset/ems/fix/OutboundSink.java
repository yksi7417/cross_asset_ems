/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

/**
 * Delivers an outbound FIX message to a client session's transport. Injected into {@link
 * FixGateway} so the gateway stays transport-agnostic and deterministically testable: tests supply
 * a recording sink, production supplies the real socket/Aeron writer.
 *
 * <p>The {@code outboundSeq} is the sequence number the gateway assigned via the session's resend
 * buffer; it is passed alongside the wire so the transport (and tests) can correlate without
 * re-parsing.
 */
@FunctionalInterface
public interface OutboundSink {

  /** Deliver {@code rawFix} (already sequenced and checksummed) for the given session. */
  void deliver(long sessionId, long outboundSeq, String rawFix);
}
