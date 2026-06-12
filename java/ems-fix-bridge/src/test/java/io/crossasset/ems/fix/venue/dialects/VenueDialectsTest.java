/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue.dialects;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.venue.FixVenueGateway;
import io.crossasset.ems.fix.venue.VenueDialect;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.Dialect;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 11.3–11.10: every venue dialect's distinguishing rules — order-type restrictions, quantity
 * conventions, mandatory tags — plus the gateway integration (validation rejects locally before the
 * wire; customization tags land on the outbound NewOrderSingle).
 */
class VenueDialectsTest {

  private static final VenueRouteRequest LIMIT =
      new VenueRouteRequest("RTE-1", "CL-1", "BBG00DEMOT35", 1, 1_000_000L, 98_7500L);
  private static final VenueRouteRequest MARKET =
      new VenueRouteRequest("RTE-2", "CL-2", "BBG00DEMOT35", 1, 1_000_000L, null);

  private static String render(VenueDialect dialect, VenueRouteRequest request) {
    FixMessage.Builder b = FixMessage.builder().field(35, "D").field(11, request.clOrdId());
    dialect.customize(b, request);
    return b.build();
  }

  @Test
  void tradeweb_priceFirst_settlementDated_rfqCapable() {
    VenueDialect tw = VenueDialects.tradeweb("20260613");
    assertThat(tw.validate(MARKET)).isPresent(); // FI venue: no market orders
    assertThat(tw.validate(LIMIT)).isEmpty();
    assertThat(render(tw, LIMIT)).contains("64=20260613");
    assertThat(tw.capabilities()).contains(Capability.SUPPORTS_RFQ);
    assertThat(tw.capabilities()).doesNotContain(Capability.SUPPORTS_MARKET);
  }

  @Test
  void brokerTec_limitOnly_millionFaceIncrements_noReplace() {
    VenueDialect btec = VenueDialects.brokerTec();
    assertThat(btec.validate(MARKET).orElseThrow()).contains("limit-order book");
    assertThat(
            btec.validate(new VenueRouteRequest("R", "C", "X", 1, 500_000L, 98_0000L))
                .orElseThrow())
        .contains("$1M-face");
    assertThat(
            btec.validate(new VenueRouteRequest("R", "C", "X", 1, 1_500_000L, 98_0000L))
                .orElseThrow())
        .contains("$1M-face"); // increments, not just minimum
    assertThat(btec.validate(LIMIT)).isEmpty();
    assertThat(btec.capabilities()).doesNotContain(Capability.SUPPORTS_REPLACE); // cancel/new
    assertThat(render(btec, LIMIT)).contains("59=0"); // DAY mandatory
  }

  @Test
  void ebs_baseCcyMillions_valueDated() {
    VenueDialect ebs = VenueDialects.ebs("20260616");
    assertThat(ebs.validate(new VenueRouteRequest("R", "C", "X", 1, 2_500_000L, 1_0842L)))
        .isPresent();
    assertThat(ebs.validate(new VenueRouteRequest("R", "C", "X", 1, 2_000_000L, 1_0842L)))
        .isEmpty();
    assertThat(render(ebs, LIMIT)).contains("64=20260616");
  }

  @Test
  void fxall_lastLookAcknowledged_rfqCapable() {
    VenueDialect fxall = VenueDialects.fxall("20260616");
    assertThat(fxall.validate(MARKET)).isPresent();
    String wire = render(fxall, LIMIT);
    assertThat(wire).contains("18=L"); // dealer last look acknowledged on every order
    assertThat(wire).contains("64=20260616");
    assertThat(fxall.capabilities()).contains(Capability.SUPPORTS_RFQ);
  }

  @Test
  void emsx_requiresBroker_forwardsStrategyParams() {
    VenueDialect noBroker = VenueDialects.emsx("", Map.of());
    assertThat(noBroker.validate(LIMIT).orElseThrow()).contains("named broker");

    VenueDialect emsx = VenueDialects.emsx("GS", Map.of("MaxPctVolume", "15"));
    assertThat(emsx.validate(LIMIT)).isEmpty();
    String wire = render(emsx, LIMIT);
    assertThat(wire).contains("76=GS"); // ExecBroker — the order's destination
    assertThat(wire).contains("957=1").contains("958=MaxPctVolume").contains("960=15");
    assertThat(emsx.capabilities()).contains(Capability.SUPPORTS_REPLACE);
  }

  @Test
  void bloombergSef_stampsDeterministicUti_andClearingHouse() {
    VenueDialect sef = VenueDialects.bloombergSef("LEI123", "LCH");
    String wire = render(sef, LIMIT);
    assertThat(wire).contains("20001=LEI123-"); // the UTI
    assertThat(wire).contains("20002=LCH");
    // Determinism: same clOrdId -> identical UTI; replay rebuilds the same identity.
    assertThat(render(sef, LIMIT)).isEqualTo(wire);
    assertThat(sef.validate(MARKET)).isPresent();
  }

  @Test
  void bloombergBridge_requiresRelationshipBank() {
    assertThat(VenueDialects.bloombergBridge("", "20260616").validate(LIMIT)).isPresent();
    VenueDialect bridge = VenueDialects.bloombergBridge("HSBC", "20260616");
    assertThat(bridge.validate(LIMIT)).isEmpty();
    assertThat(render(bridge, LIMIT)).contains("20004=HSBC").contains("64=20260616");
  }

  @Test
  void usEquityExchanges_regNmsConventions_perMic() {
    for (String mic : new String[] {"XNYS", "XNAS", "BZX"}) {
      VenueDialect exchange = VenueDialects.usEquityExchange(mic, "FIRM-STP-1", true);
      assertThat(exchange.mic()).isEqualTo(mic);
      String wire = render(exchange, LIMIT);
      assertThat(wire).contains("18=f"); // Intermarket Sweep Order
      assertThat(wire).contains("20003=FIRM-STP-1"); // self-trade prevention
      assertThat(wire).contains("1028=N"); // fully automated
      assertThat(exchange.capabilities())
          .contains(Capability.SUPPORTS_MARKET, Capability.SUPPORTS_REPLACE);
    }
    // Non-ISO flow omits the sweep flag.
    assertThat(render(VenueDialects.usEquityExchange("XNYS", "K", false), LIMIT))
        .doesNotContain("18=f");
  }

  @Test
  void gatewayIntegration_validationRejectsLocally_customTagsReachTheWire() {
    List<String> wire = new ArrayList<>();
    List<String> rejects = new ArrayList<>();
    VenueEventSink sink =
        new VenueEventSink() {
          @Override
          public void acknowledged(String routeId) {}

          @Override
          public void pendingNew(String routeId) {}

          @Override
          public void rejected(String routeId, String venueReason) {
            rejects.add(routeId + ": " + venueReason);
          }

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
        };
    FixVenueGateway gateway =
        new FixVenueGateway(
            new VenueRef("venue-btec", "BTEC", Dialect.FIX),
            EnumSet.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_CANCEL),
            sink,
            false,
            new SequenceRecoveryService(() -> 0L),
            7L,
            (sessionId, seq, rawFix) -> wire.add(rawFix),
            "EMS",
            "BTEC",
            30);
    gateway.connect(1);
    gateway.setDialect(VenueDialects.brokerTec());
    int wireBefore = wire.size();

    // A market order violates BrokerTec's book rules: rejected LOCALLY, nothing on the wire.
    gateway.submit(MARKET);
    assertThat(rejects).hasSize(1);
    assertThat(rejects.get(0)).contains("brokertec").contains("limit-order book");
    assertThat(wire).hasSize(wireBefore);

    // A conforming order goes out carrying the dialect's tags.
    gateway.submit(LIMIT);
    assertThat(wire).hasSize(wireBefore + 1);
    assertThat(wire.get(wire.size() - 1)).contains("35=D").contains("59=0");
  }
}
