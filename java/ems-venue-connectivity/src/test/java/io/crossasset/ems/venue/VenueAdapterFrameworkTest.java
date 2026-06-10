/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Framework tests for the venue adapter layer (task 11.1). Per arch-venue-connectivity.md. */
class VenueAdapterFrameworkTest {

  private static final VenueRef MAX = new VenueRef("venue-marketaxess", "MAXX", Dialect.FIX);

  // ── registry + capability negotiation ───────────────────────────────────────

  @Test
  void register_andLookupByMicAndId() {
    var registry = new InMemoryVenueAdapterRegistry();
    var adapter = stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT), false);
    registry.register(adapter);

    assertThat(registry.byMic("MAXX")).containsSame(adapter);
    assertThat(registry.byVenueId("venue-marketaxess")).containsSame(adapter);
    assertThat(registry.all()).containsExactly(adapter);
  }

  @Test
  void unregister_removesFromBothIndices() {
    var registry = new InMemoryVenueAdapterRegistry();
    var adapter = stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT), false);
    registry.register(adapter);
    registry.unregister("venue-marketaxess");

    assertThat(registry.byMic("MAXX")).isEmpty();
    assertThat(registry.byVenueId("venue-marketaxess")).isEmpty();
    assertThat(registry.all()).isEmpty();
  }

  @Test
  void select_capabilityPresent_returnsSelected() {
    var registry = new InMemoryVenueAdapterRegistry();
    var adapter = stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT), false);
    registry.register(adapter);

    VenueSelection sel = registry.select("MAXX", Capability.SUPPORTS_LIMIT);
    assertThat(sel).isInstanceOf(VenueSelection.Selected.class);
    assertThat(((VenueSelection.Selected) sel).adapter()).isSameAs(adapter);
  }

  @Test
  void select_capabilityMissing_returnsUnsupportedWithRte1003() {
    var registry = new InMemoryVenueAdapterRegistry();
    registry.register(stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT), false));

    VenueSelection sel = registry.select("MAXX", Capability.SUPPORTS_RFQ);
    assertThat(sel).isInstanceOf(VenueSelection.Unsupported.class);
    var unsupported = (VenueSelection.Unsupported) sel;
    assertThat(unsupported.required()).isEqualTo(Capability.SUPPORTS_RFQ);
    assertThat(unsupported.rejectCode()).isEqualTo("EMS-RTE-1003");
  }

  @Test
  void select_unknownMic_returnsNotFound() {
    var registry = new InMemoryVenueAdapterRegistry();
    VenueSelection sel = registry.select("ZZZZ", Capability.SUPPORTS_LIMIT);
    assertThat(sel).isInstanceOf(VenueSelection.NotFound.class);
    assertThat(((VenueSelection.NotFound) sel).rejectCode()).isEqualTo("EMS-RTE-5003");
  }

  // ── AbstractVenueAdapter: state, capabilities, shadow ───────────────────────

  @Test
  void adapter_startsDisabled_andTransitions() {
    var adapter = stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT), false);
    assertThat(adapter.state()).isEqualTo(VenueState.DISABLED);
    adapter.connect();
    assertThat(adapter.state()).isEqualTo(VenueState.CONNECTED);
  }

  @Test
  void adapter_supportsReflectsCapabilities() {
    var adapter =
        stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_CANCEL), false);
    assertThat(adapter.supports(Capability.SUPPORTS_LIMIT)).isTrue();
    assertThat(adapter.supports(Capability.SUPPORTS_HIDDEN)).isFalse();
  }

  @Test
  void adapter_shadowMode_doesNotEmitOutbound() {
    var shadow = stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT), true);
    shadow.submit(new VenueRouteRequest("R1", "C1", "BBG1", 1, 100, 5000L));
    assertThat(shadow.submitted).isEmpty(); // shadow discards outbound
    assertThat(shadow.shadowFlag()).isTrue();
  }

  @Test
  void adapter_liveMode_recordsOutbound() {
    var live = stubAdapter(MAX, EnumSet.of(Capability.SUPPORTS_LIMIT), false);
    var req = new VenueRouteRequest("R1", "C1", "BBG1", 1, 100, null);
    live.submit(req);
    assertThat(live.submitted).containsExactly(req);
    assertThat(req.isMarket()).isTrue();
  }

  // ── RouteManagerVenueEventSink bridge ───────────────────────────────────────

  @Test
  void sink_forwardsEveryEventToRouteManager() {
    RouteManager rm = mock(RouteManager.class);
    var sink = new RouteManagerVenueEventSink(rm);

    sink.acknowledged("R1");
    sink.pendingNew("R1");
    sink.rejected("R1", "venue down");
    sink.partialFill("R1", 40, 5000, "x1");
    sink.filled("R1", 60, 5001, "x2");
    sink.canceled("R1");
    sink.cancelRejected("R1", 0);
    sink.replaced("R1");
    sink.replaceRejected("R1", 3);

    verify(rm).acknowledgeRoute("R1");
    verify(rm).pendingNewAtVenue("R1");
    verify(rm).rejectRoute("R1");
    verify(rm).partialFill("R1", 40, 5000, "x1");
    verify(rm).fullFill("R1", 60, 5001, "x2");
    verify(rm).canceledByVenue("R1");
    verify(rm).cancelRejectedByVenue("R1", 0);
    verify(rm).replacedByVenue("R1");
    verify(rm).replaceRejectedByVenue("R1", 3);
  }

  @Test
  void sink_routeManagerRejection_routedToAnomalyHandler() {
    RouteManager rm = mock(RouteManager.class);
    when(rm.fullFill("R1", 10, 100, "x"))
        .thenReturn(new RouteEventResult.Rejected("R1", "EMS-RTE-5002", "terminal"));
    AtomicReference<RouteEventResult.Rejected> seen = new AtomicReference<>();
    var sink = new RouteManagerVenueEventSink(rm, seen::set);

    sink.filled("R1", 10, 100, "x");

    assertThat(seen.get()).isNotNull();
    assertThat(seen.get().rejectCode()).isEqualTo("EMS-RTE-5002");
  }

  // ── test helpers ─────────────────────────────────────────────────────────────

  private static StubVenueAdapter stubAdapter(VenueRef ref, Set<Capability> caps, boolean shadow) {
    return new StubVenueAdapter(ref, caps, new RecordingSink(), shadow);
  }

  /** Minimal concrete adapter that records outbound calls (unless in shadow mode). */
  private static final class StubVenueAdapter extends AbstractVenueAdapter {
    final List<VenueRouteRequest> submitted = new ArrayList<>();
    final List<String> canceled = new ArrayList<>();

    StubVenueAdapter(VenueRef ref, Set<Capability> caps, VenueEventSink sink, boolean shadow) {
      super(ref, caps, sink, shadow);
    }

    void connect() {
      setState(VenueState.CONNECTED);
    }

    boolean shadowFlag() {
      return isShadow();
    }

    @Override
    public void submit(VenueRouteRequest request) {
      if (isShadow()) {
        return;
      }
      submitted.add(request);
    }

    @Override
    public void cancel(String routeId) {
      if (!isShadow()) {
        canceled.add(routeId);
      }
    }

    @Override
    public void replace(String routeId, String newClOrdId, long newQty, Long newPrice) {
      // no-op for the framework test
    }
  }

  /** A sink that records nothing meaningful — used where the adapter never calls back. */
  private static final class RecordingSink implements VenueEventSink {
    @Override
    public void acknowledged(String routeId) {}

    @Override
    public void pendingNew(String routeId) {}

    @Override
    public void rejected(String routeId, String venueReason) {}

    @Override
    public void partialFill(String routeId, long lastQty, long lastPx, String execId) {}

    @Override
    public void filled(String routeId, long lastQty, long lastPx, String execId) {}

    @Override
    public void canceled(String routeId) {}

    @Override
    public void cancelRejected(String routeId, int cxlRejReason) {}

    @Override
    public void replaced(String routeId) {}

    @Override
    public void replaceRejected(String routeId, int cxlRejReason) {}
  }
}
