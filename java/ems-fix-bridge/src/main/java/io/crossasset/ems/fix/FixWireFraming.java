/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import java.io.IOException;
import java.io.InputStream;

/**
 * SOH-delimited FIX message framing off a raw byte stream: shared by every place that reads FIX
 * directly from a socket ({@link io.crossasset.ems.fix.sim.FixSimulatorMain}, {@link
 * io.crossasset.ems.fix.venue.VenueGatewayMain}, and their tests) so the framing rule lives in
 * exactly one place instead of drifting across copies.
 */
public final class FixWireFraming {

  private FixWireFraming() {}

  @FunctionalInterface
  public interface FrameHandler {
    void onFrame(String rawFix);
  }

  /**
   * Blocks the calling thread reading {@code in} until EOF, invoking {@code onFrame} once per
   * complete message -- a message ends with the checksum field {@code "10=NNN<SOH>"}.
   */
  public static void readFrames(InputStream in, FrameHandler onFrame) throws IOException {
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
        onFrame.onFrame(buffer.toString());
        buffer.setLength(0);
      }
    }
  }
}
