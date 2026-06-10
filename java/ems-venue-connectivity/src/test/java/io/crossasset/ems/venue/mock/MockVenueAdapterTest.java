/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.mock;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.Dialect;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import io.crossasset.ems.venue.VenueState;
import io.crossasset.ems.venue.mock.MockVenueAdapter.FillBehavior;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the in-process mock venue adapter (task 11.2). */
class MockVenueAdapterTest {

  private static final VenueRef REF = new VenueRef("v-mock", "MAXX", Dialect.FIX);
  private static final VenueRouteRequest LIMIT_BUY_100 =
      new VenueRouteRequest("R1", "C1", "BBG-CORP", 1, 100, 9950L);

  @Test
  void ackThenFullFill_emitsAckThenFill() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.ACK_THEN_FULL_FILL, false);

    mock.submit(LIMIT_BUY_100);

    assertThat(sink.events).containsExactly("ack:R1", "fill:R1:100:9950:MOCK-EXEC-1");
  }

  @Test
  void ackOnly_emitsOnlyAck() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.ACK_ONLY, false);

    mock.submit(LIMIT_BUY_100);

    assertThat(sink.events).containsExactly("ack:R1");
  }

  @Test
  void ackThenPartialThenFull_splitsTheFill() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.ACK_THEN_PARTIAL_THEN_FULL, false);

    mock.submit(LIMIT_BUY_100);

    assertThat(sink.events)
        .containsExactly("ack:R1", "partial:R1:50:9950:MOCK-EXEC-1", "fill:R1:50:9950:MOCK-EXEC-2");
  }

  @Test
  void reject_emitsRejectNoAck() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.REJECT, false);

    mock.submit(LIMIT_BUY_100);

    assertThat(sink.events).containsExactly("reject:R1:mock venue reject");
  }

  @Test
  void marketOrder_fillsAtFallbackMark() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.ACK_THEN_FULL_FILL, false);

    mock.submit(new VenueRouteRequest("R2", "C2", "BBG-CORP", 1, 10, null));

    assertThat(sink.events).containsExactly("ack:R2", "fill:R2:10:10000:MOCK-EXEC-1");
  }

  @Test
  void shadowMode_emitsNothing() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.ACK_THEN_FULL_FILL, true);

    mock.submit(LIMIT_BUY_100);
    mock.cancel("R1");
    mock.replace("R1", "C9", 50, 9900L);

    assertThat(sink.events).isEmpty();
  }

  @Test
  void cancelAndReplace_emitCallbacks() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.ACK_ONLY, false);

    mock.cancel("R1");
    mock.replace("R1", "C9", 50, 9900L);

    assertThat(sink.events).containsExactly("cancel:R1", "replace:R1");
  }

  @Test
  void execIds_areMonotonicAcrossSubmits() {
    var sink = new RecordingSink();
    var mock = adapter(sink, FillBehavior.ACK_THEN_FULL_FILL, false);

    mock.submit(new VenueRouteRequest("R1", "C1", "BBG", 1, 10, 100L));
    mock.submit(new VenueRouteRequest("R2", "C2", "BBG", 1, 10, 100L));

    assertThat(sink.events)
        .containsExactly(
            "ack:R1", "fill:R1:10:100:MOCK-EXEC-1", "ack:R2", "fill:R2:10:100:MOCK-EXEC-2");
  }

  @Test
  void marketAxessFactory_isConnectedWithExpectedCaps() {
    var mock = MockVenueAdapter.marketAxess(new RecordingSink());
    assertThat(mock.state()).isEqualTo(VenueState.CONNECTED);
    assertThat(mock.venueRef().mic()).isEqualTo("MAXX");
    assertThat(mock.supports(Capability.SUPPORTS_LIMIT)).isTrue();
    assertThat(mock.supports(Capability.SUPPORTS_CANCEL)).isTrue();
    assertThat(mock.supports(Capability.SUPPORTS_RFQ)).isFalse();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static MockVenueAdapter adapter(
      VenueEventSink sink, FillBehavior behavior, boolean shadow) {
    return new MockVenueAdapter(
        REF, EnumSet.allOf(Capability.class), sink, shadow, behavior, 10000L);
  }

  /** Records the venue lifecycle as compact strings for order-sensitive assertions. */
  private static final class RecordingSink implements VenueEventSink {
    final List<String> events = new ArrayList<>();

    @Override
    public void acknowledged(String routeId) {
      events.add("ack:" + routeId);
    }

    @Override
    public void pendingNew(String routeId) {
      events.add("pendingNew:" + routeId);
    }

    @Override
    public void rejected(String routeId, String venueReason) {
      events.add("reject:" + routeId + ":" + venueReason);
    }

    @Override
    public void partialFill(String routeId, long lastQty, long lastPx, String execId) {
      events.add("partial:" + routeId + ":" + lastQty + ":" + lastPx + ":" + execId);
    }

    @Override
    public void filled(String routeId, long lastQty, long lastPx, String execId) {
      events.add("fill:" + routeId + ":" + lastQty + ":" + lastPx + ":" + execId);
    }

    @Override
    public void canceled(String routeId) {
      events.add("cancel:" + routeId);
    }

    @Override
    public void cancelRejected(String routeId, int cxlRejReason) {
      events.add("cancelReject:" + routeId + ":" + cxlRejReason);
    }

    @Override
    public void replaced(String routeId) {
      events.add("replace:" + routeId);
    }

    @Override
    public void replaceRejected(String routeId, int cxlRejReason) {
      events.add("replaceReject:" + routeId + ":" + cxlRejReason);
    }
  }
}
