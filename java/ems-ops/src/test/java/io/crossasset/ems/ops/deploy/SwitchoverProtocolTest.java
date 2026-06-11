/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SwitchoverProtocol}: ordered handoff with position-precise lease release,
 * costless pre-check aborts, window awareness with logged emergency override, and the
 * standby-behind rollback path. Per arch-deployment.md, task 14.5.
 */
class SwitchoverProtocolTest {

  /** Single-pod fake lease: exclusive holder + position prerequisite + monotonic tokens. */
  private static final class FakeLease implements SwitchoverProtocol.LeaseService {
    String holder;
    long releasedAt = -1;
    long nextToken = 1;

    @Override
    public Optional<Long> acquire(String pod, String who, long atPosition) {
      if (holder != null || atPosition < releasedAt) {
        return Optional.empty();
      }
      holder = who;
      return Optional.of(nextToken++);
    }

    @Override
    public boolean release(String pod, String who, long atPosition) {
      if (!who.equals(holder)) {
        return false;
      }
      holder = null;
      releasedAt = atPosition;
      return true;
    }

    @Override
    public Optional<String> holder(String pod) {
      return Optional.ofNullable(holder);
    }
  }

  private static final class FakeLane implements SwitchoverProtocol.Lane {
    final String name;
    final List<String> calls = new ArrayList<>();
    boolean selfCheckOk = true;
    long snapshotAt = 1000;
    long replayedAt = 1000;
    long resumedWithToken = -1;

    FakeLane(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean selfCheck() {
      return selfCheckOk;
    }

    @Override
    public void drainNewSessions() {
      calls.add("drain");
    }

    @Override
    public void stopVenueEgress() {
      calls.add("egress-stop");
    }

    @Override
    public long snapshotPosition() {
      calls.add("snapshot");
      return snapshotAt;
    }

    @Override
    public long replayedPosition() {
      return replayedAt;
    }

    @Override
    public void resumeVenues(long fenceToken) {
      calls.add("resume");
      resumedWithToken = fenceToken;
    }

    @Override
    public void becomeWarmStandby() {
      calls.add("warm-standby");
    }
  }

  private final FakeLease lease = new FakeLease();
  private final List<String> traffic = new ArrayList<>();
  private final SwitchoverProtocol protocol =
      new SwitchoverProtocol(lease, () -> traffic.add("switched"));

  private FakeLane active() {
    FakeLane lane = new FakeLane("PROD-A1");
    lease.acquire("pod-a", lane.name(), 0); // active holds the lease before the switch
    return lane;
  }

  @Test
  void happyPath_orderedHandoff_newLaneFencedAndLive() {
    FakeLane old = active();
    FakeLane fresh = new FakeLane("PROD-A2");
    SwitchoverProtocol.SwitchResult result =
        protocol.execute("pod-a", old, fresh, () -> true, false);

    assertThat(result.switched()).isTrue();
    assertThat(old.calls).containsExactly("drain", "egress-stop", "snapshot", "warm-standby");
    assertThat(fresh.resumedWithToken).isEqualTo(result.fenceToken());
    assertThat(lease.holder).isEqualTo("PROD-A2");
    assertThat(traffic).containsExactly("switched");
    assertThat(result.transcript()).anyMatch(line -> line.startsWith("LEASE-RELEASED at=1000"));
  }

  @Test
  void windowClosed_abortsBeforeAnyDrain() {
    FakeLane old = active();
    FakeLane fresh = new FakeLane("PROD-A2");
    SwitchoverProtocol.SwitchResult result =
        protocol.execute("pod-a", old, fresh, () -> false, false);
    assertThat(result.switched()).isFalse();
    assertThat(old.calls).isEmpty(); // costless abort
    assertThat(lease.holder).isEqualTo("PROD-A1");
  }

  @Test
  void emergencyOverride_proceedsAndIsLogged() {
    FakeLane old = active();
    FakeLane fresh = new FakeLane("PROD-A2");
    SwitchoverProtocol.SwitchResult result =
        protocol.execute("pod-a", old, fresh, () -> false, true);
    assertThat(result.switched()).isTrue();
    assertThat(result.transcript()).anyMatch(line -> line.contains("OVERRIDE window-closed"));
  }

  @Test
  void failedSelfCheck_abortsBeforeAnyDrain() {
    FakeLane old = active();
    FakeLane fresh = new FakeLane("PROD-A2");
    fresh.selfCheckOk = false;
    SwitchoverProtocol.SwitchResult result =
        protocol.execute("pod-a", old, fresh, () -> true, false);
    assertThat(result.switched()).isFalse();
    assertThat(old.calls).isEmpty();
  }

  @Test
  void standbyBehindPosition_rollsBackToOldLane() {
    FakeLane old = active();
    FakeLane fresh = new FakeLane("PROD-A2");
    old.snapshotAt = 2000;
    fresh.replayedAt = 1500; // behind P
    SwitchoverProtocol.SwitchResult result =
        protocol.execute("pod-a", old, fresh, () -> true, false);

    assertThat(result.switched()).isFalse();
    assertThat(result.reason()).contains("rolled back");
    assertThat(lease.holder).isEqualTo("PROD-A1"); // old re-acquired
    assertThat(old.resumedWithToken).isEqualTo(result.fenceToken()); // resumed fenced
    assertThat(traffic).isEmpty(); // clients never moved
  }

  @Test
  void rollbackDirection_isTheSameProtocolReversed() {
    FakeLane old = active();
    FakeLane fresh = new FakeLane("PROD-A2");
    assertThat(protocol.execute("pod-a", old, fresh, () -> true, false).switched()).isTrue();
    // Anomaly on the new lane: run the protocol the other way (rollback = reverse switch).
    SwitchoverProtocol.SwitchResult back = protocol.execute("pod-a", fresh, old, () -> true, false);
    assertThat(back.switched()).isTrue();
    assertThat(lease.holder).isEqualTo("PROD-A1");
    assertThat(back.fenceToken()).isGreaterThan(1); // tokens stay monotonic across handoffs
  }
}
