/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.deploy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The blue/green switchover protocol (task 14.5, arch-deployment § High-precision protocol): the
 * ordered, abort-safe handoff that guarantees no dual-live, no missed messages, no duplicates —
 * built on a position-precise lease release and fence-token venue credentials.
 *
 * <p>Sequence: pre-checks (window awareness + standby self-check, abort costless) → old lane drains
 * new sessions → stops venue egress → snapshots at Archive position P → releases the lease at P →
 * standby confirms replay ≥ P → acquires the lease (new fence token) → resumes venues fenced →
 * traffic switch → old lane becomes warm-standby. If the standby cannot reach P after the release,
 * the protocol <b>rolls back</b>: the old lane re-acquires at P and resumes — switchover is never a
 * one-way door.
 *
 * <p>The lease itself is the 14.6 cluster-of-clusters service; fence-token credential rotation is
 * 14.7. Every step lands in a deterministic transcript for the ops audit.
 */
public final class SwitchoverProtocol {

  /** The per-pod active lease (realized by the 14.6 cluster lease service). */
  public interface LeaseService {
    /** Acquire for {@code holder} if free and {@code atPosition} ≥ the last release position. */
    Optional<Long> acquire(String pod, String holder, long atPosition);

    /** Release by the current holder at the given Archive position. */
    boolean release(String pod, String holder, long atPosition);

    /** Current holder, if any. */
    Optional<String> holder(String pod);
  }

  /** One deployable lane (e.g. PROD-A1) — the protocol's view of a cluster. */
  public interface Lane {
    String name();

    /** Pre-switch verification: golden replay matches, FSM version matches. */
    boolean selfCheck();

    /** Step 1: refuse new client sessions (existing in-flight continues). */
    void drainNewSessions();

    /** Step 2: stop emitting new venue traffic. */
    void stopVenueEgress();

    /** Step 3: final snapshot; returns the Archive position P. */
    long snapshotPosition();

    /** Standby's replayed Archive position (must reach P before acquiring). */
    long replayedPosition();

    /** Step 7: resume venue connections with credentials carrying the fence token. */
    void resumeVenues(long fenceToken);

    /** Step 10 (or rollback): consume the other lane's Archive as warm-standby. */
    void becomeWarmStandby();
  }

  /** Outcome with the full step transcript. */
  public record SwitchResult(
      boolean switched, long fenceToken, List<String> transcript, String reason) {}

  private final LeaseService lease;
  private final Runnable trafficSwitch;

  public SwitchoverProtocol(LeaseService lease, Runnable trafficSwitch) {
    this.lease = Objects.requireNonNull(lease, "lease");
    this.trafficSwitch = Objects.requireNonNull(trafficSwitch, "trafficSwitch");
  }

  /**
   * Execute one switchover for {@code pod} from {@code active} to {@code standby}.
   *
   * @param windowOpen the asset-class maintenance-window check (arch-resilience-24x7 table)
   * @param emergencyOverride proceed outside the window — recorded on the transcript for review
   */
  public SwitchResult execute(
      String pod,
      Lane active,
      Lane standby,
      BooleanSupplier windowOpen,
      boolean emergencyOverride) {
    List<String> log = new ArrayList<>();

    // ── Pre-checks: abort while aborting is free. ──
    if (!windowOpen.getAsBoolean()) {
      if (!emergencyOverride) {
        log.add("ABORT window-closed");
        return new SwitchResult(false, -1, log, "Outside the asset's switchover window.");
      }
      log.add("OVERRIDE window-closed emergency=TRUE");
    }
    if (!standby.selfCheck()) {
      log.add("ABORT standby-self-check-failed " + standby.name());
      return new SwitchResult(false, -1, log, "Standby self-check failed (golden replay / FSM).");
    }
    log.add("PRECHECK ok standby=" + standby.name());

    // ── Drain and fence the old lane. ──
    active.drainNewSessions();
    log.add("DRAIN " + active.name());
    active.stopVenueEgress();
    log.add("EGRESS-STOP " + active.name());
    long position = active.snapshotPosition();
    log.add("SNAPSHOT position=" + position);
    if (!lease.release(pod, active.name(), position)) {
      log.add("ABORT lease-release-refused");
      return new SwitchResult(false, -1, log, "Lease release refused (not the holder?).");
    }
    log.add("LEASE-RELEASED at=" + position + " — " + active.name() + " FENCED");

    // ── Hand over at the exact position, or roll back. ──
    if (standby.replayedPosition() < position) {
      log.add("ROLLBACK standby-behind replayed=" + standby.replayedPosition() + " < " + position);
      long token =
          lease
              .acquire(pod, active.name(), position)
              .orElseThrow(() -> new IllegalStateException("rollback re-acquire must succeed"));
      active.resumeVenues(token);
      log.add("ROLLBACK-COMPLETE active=" + active.name() + " token=" + token);
      return new SwitchResult(
          false, token, log, "Standby behind snapshot position; rolled back to old lane.");
    }
    Optional<Long> token = lease.acquire(pod, standby.name(), standby.replayedPosition());
    if (token.isEmpty()) {
      log.add("ABORT lease-acquire-refused " + standby.name());
      return new SwitchResult(false, -1, log, "Lease acquire refused for standby.");
    }
    log.add("LEASE-ACQUIRED holder=" + standby.name() + " token=" + token.get());

    // ── Bring the new lane live. ──
    standby.resumeVenues(token.get());
    log.add("VENUES-RESUMED " + standby.name() + " token=" + token.get());
    trafficSwitch.run();
    log.add("TRAFFIC-SWITCHED to=" + standby.name());
    active.becomeWarmStandby();
    log.add("WARM-STANDBY " + active.name());

    return new SwitchResult(true, token.get(), log, "");
  }
}
