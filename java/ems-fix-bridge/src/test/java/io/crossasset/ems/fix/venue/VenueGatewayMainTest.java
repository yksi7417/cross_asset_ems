/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fix.FixWireFraming;
import io.crossasset.ems.fix.sim.FixVenueSimulator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * End-to-end over a REAL socket (not the in-process wire the unit tests use): a background thread
 * plays the venue side (same SOH-framing {@link io.crossasset.ems.fix.sim.FixSimulatorMain} uses)
 * while {@link VenueGatewayMain#main} runs the client with a real {@link
 * io.crossasset.ems.fix.venue.dialects.VenueDialects} entry installed. Proves the dialect catalogue
 * produces wire-valid FIX against a real TCP peer, closing the loop the "unwired" finding flagged.
 */
class VenueGatewayMainTest {

  /** main() captures the process-wide System.out; serialize against anything else that does. */
  @Test
  @ResourceLock(Resources.SYSTEM_OUT)
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
      FixWireFraming.readFrames(socket.getInputStream(), simulator::onInbound);
    } catch (IOException e) {
      // client closed the socket once its terminal event landed -- expected teardown
    }
  }
}
