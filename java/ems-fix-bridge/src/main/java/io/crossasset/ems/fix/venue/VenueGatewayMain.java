/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue;

import io.crossasset.ems.fix.OutboundSink;
import io.crossasset.ems.fix.venue.dialects.VenueDialects;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.venue.Dialect;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

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
 * <p>{@code -Pdialect} selects a {@link VenueDialects} factory by its {@link VenueDialect#id()}:
 * {@code us-equity} (default, MIC XNAS), {@code brokertec}, {@code tradeweb}. All three take
 * limit/RFQ orders only except us-equity, so the default order is a limit at 100.00.
 */
public final class VenueGatewayMain {

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

      FixVenueGateway gateway =
          new FixVenueGateway(
              new VenueRef(dialect.id(), micFor(dialectId), Dialect.FIX),
              EnumSet.copyOf(dialect.capabilities()),
              loggingSink(),
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

      Thread.sleep(1_500); // let the venue's async ExecutionReport arrive and print
      // socket.close() (try-with-resources) ends the reader's blocking read next
    }
  }

  /** Frame SOH-terminated FIX messages off the socket, same convention as FixSimulatorMain. */
  private static void readInbound(Socket socket, FixVenueGateway gateway) {
    try {
      InputStream in = socket.getInputStream();
      StringBuilder buffer = new StringBuilder();
      int c;
      while ((c = in.read()) >= 0) {
        buffer.append((char) c);
        int len = buffer.length();
        if (c == '\u0001'
            && len >= 7
            && buffer.charAt(len - 7) == '1'
            && buffer.charAt(len - 6) == '0'
            && buffer.charAt(len - 5) == '=') {
          gateway.onInbound(buffer.toString());
          buffer.setLength(0);
        }
      }
    } catch (IOException e) {
      System.out.println("[venue-gw] reader ended: " + e.getMessage());
    }
  }

  private static VenueDialect dialectById(String id) {
    return switch (id) {
      case "brokertec" -> VenueDialects.brokerTec();
      case "tradeweb" -> VenueDialects.tradeweb("2026-07-16");
      case "us-equity" -> VenueDialects.usEquityExchange("XNAS", "STP-1", false);
      default -> throw new IllegalArgumentException("unknown -Pdialect=" + id);
    };
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

  private static VenueEventSink loggingSink() {
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
      }

      @Override
      public void partialFill(String routeId, long lastQty, long lastPx, String execId) {
        System.out.println(
            "[venue-gw] PARTIAL_FILL " + routeId + " qty=" + lastQty + " px=" + lastPx);
      }

      @Override
      public void filled(String routeId, long lastQty, long lastPx, String execId) {
        System.out.println("[venue-gw] FILLED " + routeId + " qty=" + lastQty + " px=" + lastPx);
      }

      @Override
      public void canceled(String routeId) {
        System.out.println("[venue-gw] CANCELED " + routeId);
      }

      @Override
      public void cancelRejected(String routeId, int cxlRejReason) {
        System.out.println("[venue-gw] CANCEL_REJECTED " + routeId + " reason=" + cxlRejReason);
      }

      @Override
      public void replaced(String routeId) {
        System.out.println("[venue-gw] REPLACED " + routeId);
      }

      @Override
      public void replaceRejected(String routeId, int cxlRejReason) {
        System.out.println("[venue-gw] REPLACE_REJECTED " + routeId + " reason=" + cxlRejReason);
      }
    };
  }
}
