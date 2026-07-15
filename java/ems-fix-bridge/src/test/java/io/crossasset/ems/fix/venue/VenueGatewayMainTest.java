/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fix.sim.FixVenueSimulator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * End-to-end over a REAL socket (not the in-process wire the unit tests use): a background thread
 * plays the venue side (same SOH-framing {@link io.crossasset.ems.fix.sim.FixSimulatorMain} uses)
 * while {@link VenueGatewayMain#main} runs the client with a real {@link
 * io.crossasset.ems.fix.venue.dialects.VenueDialects} entry installed. Proves the dialect catalogue
 * produces wire-valid FIX against a real TCP peer, closing the loop the "unwired" finding flagged.
 */
class VenueGatewayMainTest {

  @org.junit.jupiter.api.Test
  @org.junit.jupiter.api.parallel.ResourceLock(org.junit.jupiter.api.parallel.Resources.SYSTEM_OUT)
  void brokerTecOrderFillsOverARealSocket() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      int port = server.getLocalPort();
      Thread venue = new Thread(() -> serveOneSession(server), "test-venue-sim");
      venue.setDaemon(true);
      venue.start();

      ByteArrayOutputStream captured = new ByteArrayOutputStream();
      PrintStream original = System.out;
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      try {
        VenueGatewayMain.main(new String[] {String.valueOf(port), "brokertec"});
      } finally {
        System.setOut(original);
      }

      String log = captured.toString(StandardCharsets.UTF_8);
      assertThat(log).contains("connected to 127.0.0.1:" + port + " dialect=brokertec");
      assertThat(log).contains("[venue-gw] ACK R-1");
      assertThat(log).contains("[venue-gw] FILLED R-1");
    }
  }

  /** Venue side of one session: full-fill execution model, same framing as FixSimulatorMain. */
  private static void serveOneSession(ServerSocket server) {
    try (Socket socket = server.accept()) {
      OutputStream out = socket.getOutputStream();
      FixVenueSimulator simulator =
          new FixVenueSimulator(
              "SIMX",
              "EMS",
              FixVenueSimulator.ExecutionModel.fullFill(100_00L),
              raw -> {
                try {
                  out.write(raw.getBytes(StandardCharsets.US_ASCII));
                  out.flush();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
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
          simulator.onInbound(buffer.toString());
          buffer.setLength(0);
        }
      }
    } catch (IOException e) {
      // client closed the socket at the end of the 1.5s window -- expected teardown
    }
  }
}
