/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.sim;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Standalone TCP wrapper for the {@link FixVenueSimulator} (task 11.15): accepts one FIX initiator
 * at a time and bridges SOH-framed messages to the engine. For manual conformance runs against the
 * venue-facing gateway or any FIX client:
 *
 * <pre>{@code ./gradlew :ems-fix-bridge:runFixSimulator -PsimPort=9876}</pre>
 *
 * <p>All protocol behavior lives in {@link FixVenueSimulator}; this class only does sockets.
 * Single-threaded, one session, full-fill model at mark 100.00 (4dp) — edit here or extend with
 * flags as conformance needs grow.
 */
public final class FixSimulatorMain {

  private FixSimulatorMain() {}

  public static void main(String[] args) throws IOException {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 9876;
    try (ServerSocket server = new ServerSocket(port)) {
      System.out.println("[fix-sim] listening on " + port + " (SIMX <- EMS, full-fill model)");
      while (true) {
        try (Socket socket = server.accept()) {
          System.out.println("[fix-sim] session from " + socket.getRemoteSocketAddress());
          serve(socket);
        } catch (IOException e) {
          System.out.println("[fix-sim] session ended: " + e.getMessage());
        }
      }
    }
  }

  private static void serve(Socket socket) throws IOException {
    OutputStream out = socket.getOutputStream();
    FixVenueSimulator simulator =
        new FixVenueSimulator(
            "SIMX",
            "EMS",
            FixVenueSimulator.ExecutionModel.fullFill(1_000_000L),
            raw -> {
              try {
                out.write(raw.getBytes(StandardCharsets.US_ASCII));
                out.flush();
              } catch (IOException e) {
                throw new RuntimeException("wire write failed", e);
              }
            });

    InputStream in = socket.getInputStream();
    StringBuilder buffer = new StringBuilder();
    int c;
    while ((c = in.read()) >= 0) {
      buffer.append((char) c);
      // A FIX message ends with the checksum field: "10=NNN<SOH>".
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
  }
}
