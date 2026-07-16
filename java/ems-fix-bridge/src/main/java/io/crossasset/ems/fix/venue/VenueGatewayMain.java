/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue;

import io.crossasset.ems.fix.FixWireFraming;
import io.crossasset.ems.fix.OutboundSink;
import io.crossasset.ems.fix.venue.dialects.VenueDialects;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.venue.Dialect;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Standalone TCP client for {@link FixVenueGateway} (tasks 11.3–11.10, wired 2026-07-14): connects
 * to a real venue or {@link io.crossasset.ems.fix.sim.FixVenueSimulator} over a socket with one of
 * the {@link VenueDialects} catalogue entries installed, submits one order, and logs every venue
 * event. The client-side sibling of {@link io.crossasset.ems.fix.sim.FixSimulatorMain} -- proof the
 * dialect catalogue produces wire-valid FIX against a real TCP peer, not just in-process tests.
 *
 * <pre>{@code
 * # venue side, one terminal
 * ./gradlew :ems-fix-bridge:runFixSimulator -PsimPort=9876
 *
 * # client side, another terminal -- submits one BrokerTec order, prints fills
 * ./gradlew :ems-fix-bridge:runVenueGateway -PsimPort=9876 -Pdialect=brokertec
 * }</pre>
 *
 * <p>{@code -Pdialect} selects which {@link VenueDialects} factory to install: {@code us-equity}
 * (default -- {@link VenueDialects#usEquityExchange}, whose own id is {@code us-equity-xnas}),
 * {@code brokertec}, {@code tradeweb}. All three take limit/RFQ orders only except us-equity, so
 * the default order is a limit at 100.00.
 */
public final class VenueGatewayMain {

  private static final long TERMINAL_WAIT_S = 5;

  private VenueGatewayMain() {}

  public static void main(String[] args) throws IOException, InterruptedException {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 9876;
    String dialectId = args.length > 1 ? args[1] : "us-equity";
    VenueDialect dialect = dialectById(dialectId);

    try (Socket socket = new Socket("127.0.0.1", port)) {
      System.out.println("[venue-gw] connected to 127.0.0.1:" + port + " dialect=" + dialectId);
      OutputStream out = socket.getOutputStream();
      OutboundSink wire =
          (sessionId, seq, rawFix) -> {
            try {
              out.write(rawFix.getBytes(StandardCharsets.US_ASCII));
              out.flush();
            } catch (IOException e) {
              throw new RuntimeException("wire write failed", e);
            }
          };

      CountDownLatch terminal = new CountDownLatch(1);
      FixVenueGateway gateway =
          new FixVenueGateway(
              new VenueRef(dialect.id(), micFor(dialectId), Dialect.FIX),
              EnumSet.copyOf(dialect.capabilities()),
              loggingSink(terminal),
              false,
              new SequenceRecoveryService(() -> 0L),
              1L,
              wire,
              "EMS",
              "SIMX",
              30);
      gateway.setDialect(dialect);
      gateway.connect(1);

      Thread reader = new Thread(() -> readInbound(socket, gateway), "venue-gw-reader");
      reader.setDaemon(true);
      reader.start();

      VenueRouteRequest request = sampleOrder(dialectId);
      gateway.submit(request);
      System.out.println("[venue-gw] submitted " + request);

      // Bounded wait for a terminal venue event (FILLED/REJECTED/...) instead of a fixed
      // sleep: exits the instant the fill (or synchronous local-validation reject) lands,
      // and still terminates deterministically if the venue only ever partial-fills.
      if (!terminal.await(TERMINAL_WAIT_S, TimeUnit.SECONDS)) {
        System.out.println(
            "[venue-gw] no terminal event within " + TERMINAL_WAIT_S + "s (partial fills only?)");
      }
      // socket.close() (try-with-resources) ends the reader's blocking read next
    }
  }

  /** Frame SOH-terminated FIX messages off the socket and feed them to the gateway. */
  private static void readInbound(Socket socket, FixVenueGateway gateway) {
    try {
      FixWireFraming.readFrames(socket.getInputStream(), gateway::onInbound);
    } catch (IOException e) {
      System.out.println("[venue-gw] reader ended: " + e.getMessage());
    }
  }

  private static VenueDialect dialectById(String id) {
    return switch (id) {
      case "brokertec" -> VenueDialects.brokerTec();
      case "tradeweb" -> VenueDialects.tradeweb(tomorrowLocalMktDate());
      case "us-equity" -> VenueDialects.usEquityExchange("XNAS", "STP-1", false);
      default -> throw new IllegalArgumentException("unknown -Pdialect=" + id);
    };
  }

  /** SettlDate (tag 64) is a FIX LocalMktDate, {@code yyyyMMdd} -- never a dashed ISO date. */
  private static String tomorrowLocalMktDate() {
    return LocalDate.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
  }

  /** One valid order per dialect's own local rules (BrokerTec/EBS: $1M-face increments). */
  private static VenueRouteRequest sampleOrder(String dialectId) {
    long qty = dialectId.equals("brokertec") ? 1_000_000L : 100L;
    return new VenueRouteRequest("R-1", "CL-1", "BBG000BLNNH6", 1, qty, 100_00L);
  }

  private static String micFor(String dialectId) {
    return switch (dialectId) {
      case "brokertec" -> "BTEC";
      case "tradeweb" -> "TWEB";
      default -> "XNAS";
    };
  }

  /**
   * Logs every venue event; counts down {@code terminal} on the events that end this one order's
   * lifecycle so the caller can stop waiting the instant one arrives.
   */
  private static VenueEventSink loggingSink(CountDownLatch terminal) {
    return new VenueEventSink() {
      @Override
      public void acknowledged(String routeId) {
        System.out.println("[venue-gw] ACK " + routeId);
      }

      @Override
      public void pendingNew(String routeId) {
        System.out.println("[venue-gw] PENDING_NEW " + routeId);
      }

      @Override
      public void rejected(String routeId, String venueReason) {
        System.out.println("[venue-gw] REJECTED " + routeId + " " + venueReason);
        terminal.countDown();
      }

      @Override
      public void partialFill(String routeId, long lastQty, long lastPx, String execId) {
        System.out.println(
            "[venue-gw] PARTIAL_FILL " + routeId + " qty=" + lastQty + " px=" + lastPx);
      }

      @Override
      public void filled(String routeId, long lastQty, long lastPx, String execId) {
        System.out.println("[venue-gw] FILLED " + routeId + " qty=" + lastQty + " px=" + lastPx);
        terminal.countDown();
      }

      @Override
      public void canceled(String routeId) {
        System.out.println("[venue-gw] CANCELED " + routeId);
        terminal.countDown();
      }

      @Override
      public void cancelRejected(String routeId, int cxlRejReason) {
        System.out.println("[venue-gw] CANCEL_REJECTED " + routeId + " reason=" + cxlRejReason);
        terminal.countDown();
      }

      @Override
      public void replaced(String routeId) {
        System.out.println("[venue-gw] REPLACED " + routeId);
        terminal.countDown();
      }

      @Override
      public void replaceRejected(String routeId, int cxlRejReason) {
        System.out.println("[venue-gw] REPLACE_REJECTED " + routeId + " reason=" + cxlRejReason);
        terminal.countDown();
      }
    };
  }
}
