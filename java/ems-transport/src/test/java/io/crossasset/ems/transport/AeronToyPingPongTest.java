/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for the single-node Aeron Cluster + Archive toy.
 *
 * <p>Boots a real media driver + archive + consensus module in-process — no mocks. Expect ~5–15s
 * for cluster startup and leader election.
 */
class AeronToyPingPongTest {

  @TempDir File tempDir;

  @Test
  @Timeout(60)
  void pingPongRoundTrip() {
    try (AeronToyPingPong toy = new AeronToyPingPong(tempDir)) {
      toy.start();
      final List<Long> rttsNs = toy.runPingPong(5);

      assertThat(rttsNs).as("received 5 PONG messages").hasSize(5);
      assertThat(rttsNs)
          .as("all RTTs are positive")
          .allMatch(rtt -> rtt > 0, "RTT must be positive");
    }
  }

  @Test
  @Timeout(60)
  void archiveRecordsClusterLog() {
    try (AeronToyPingPong toy = new AeronToyPingPong(tempDir)) {
      toy.start();
      toy.runPingPong(3);

      assertThat(toy.hasArchiveRecordings())
          .as("Archive dir must contain recordings after ping/pong")
          .isTrue();
    }
  }
}
