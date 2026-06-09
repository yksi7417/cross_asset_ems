/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.core.clock.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the local cache snapshot agent — the component that stages incoming snapshots and
 * promotes them atomically at message boundaries.
 *
 * <h3>Discriminating tests</h3>
 *
 * <ul>
 *   <li>{@code oneMessage_oneConfigView} — snapshot staged mid-message is NOT visible until {@link
 *       LocalCacheAgent#onMessageBoundary()} fires. This is the load-bearing guarantee: "one
 *       message → one config view."
 *   <li>{@code multipleStagesBeforeBoundary_latestWins} — only the last staged snapshot is promoted
 *       when the boundary fires. Proves last-write-wins semantics on the staged slot.
 *   <li>{@code publish_isImmediate} — {@link LocalCacheAgent#publish} honors the {@link
 *       ConfigService} contract; callers see the new snapshot on the very next read.
 * </ul>
 *
 * <p>Task 3.8 — local cache snapshot agent, atomic message-boundary swap.
 */
class LocalCacheAgentTest {

  private static final Timestamp T0 = Timestamp.ofEpochMillis(1_000L);
  private static final Timestamp T1 = Timestamp.ofEpochMillis(2_000L);

  private static final ConfigKey<Integer> RISK_LIMIT =
      ConfigKey.of("risk_limit_usd", Integer.class, 1_000_000);

  private LocalCacheAgent agent;

  @BeforeEach
  void setUp() {
    agent = new LocalCacheAgent(T0);
  }

  // ── initial state ─────────────────────────────────────────────────────────

  @Test
  void initialSnapshot_versionZero_returnsKeyDefault() {
    ConfigSnapshot snap = agent.currentSnapshot();

    assertNotNull(snap, "Initial snapshot must not be null");
    assertEquals(0L, snap.version(), "Bootstrap snapshot must have version 0");
    assertEquals(
        1_000_000,
        snap.getGlobal(RISK_LIMIT),
        "No override in bootstrap snapshot — must return key default");
  }

  // ── staging does not affect currentSnapshot ───────────────────────────────

  /**
   * Discriminating test: one message, one config view.
   *
   * <p>A snapshot staged in the middle of a message must not leak into the same message. Two reads
   * — before and after {@code stageSnapshot} — must return the same object.
   */
  @Test
  void oneMessage_oneConfigView() {
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1L, T1).set(RISK_LIMIT, ConfigScope.global(), 500_000).build();
    agent.publish(v1); // establish v1 as current before the "message" starts

    // "Message processing" begins — capture the config view at message start
    ConfigSnapshot atStart = agent.currentSnapshot();

    // Bus subscriber delivers v2 mid-message
    ConfigSnapshot v2 =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 250_000).build();
    agent.stageSnapshot(v2);

    // Second read inside the same message must still return v1
    ConfigSnapshot atEnd = agent.currentSnapshot();

    assertSame(
        atStart, atEnd, "currentSnapshot() must return the same reference throughout a message");
    assertEquals(500_000, atEnd.getGlobal(RISK_LIMIT), "Staged snapshot must not be visible yet");
  }

  @Test
  void stageSnapshot_noPromotion_beforeBoundary() {
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1L, T1).set(RISK_LIMIT, ConfigScope.global(), 500_000).build();
    agent.publish(v1);

    ConfigSnapshot v2 =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 999_000).build();
    agent.stageSnapshot(v2);

    // No boundary call — still v1
    assertEquals(500_000, agent.currentSnapshot().getGlobal(RISK_LIMIT));
  }

  // ── boundary promotion ────────────────────────────────────────────────────

  @Test
  void onMessageBoundary_promotesStaged() {
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1L, T1).set(RISK_LIMIT, ConfigScope.global(), 500_000).build();
    agent.publish(v1);

    ConfigSnapshot v2 =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 250_000).build();
    agent.stageSnapshot(v2);

    agent.onMessageBoundary();

    assertSame(v2, agent.currentSnapshot(), "After boundary, staged snapshot must be current");
    assertEquals(250_000, agent.currentSnapshot().getGlobal(RISK_LIMIT));
  }

  /**
   * Discriminating test: last-write-wins for staged snapshots.
   *
   * <p>If two snapshots are staged before a single boundary, only the latest survives. Proves the
   * agent does not serialize partial-update semantics.
   */
  @Test
  void multipleStagesBeforeBoundary_latestWins() {
    ConfigSnapshot v2 =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 200_000).build();
    ConfigSnapshot v3 =
        ConfigSnapshot.builder(3L, T1).set(RISK_LIMIT, ConfigScope.global(), 300_000).build();

    agent.stageSnapshot(v2);
    agent.stageSnapshot(v3); // v2 is overwritten before the boundary fires

    agent.onMessageBoundary();

    assertSame(v3, agent.currentSnapshot(), "Last staged snapshot must win on boundary promotion");
    assertEquals(300_000, agent.currentSnapshot().getGlobal(RISK_LIMIT));
  }

  @Test
  void onMessageBoundary_noop_whenNothingStaged() {
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1L, T1).set(RISK_LIMIT, ConfigScope.global(), 500_000).build();
    agent.publish(v1);

    agent.onMessageBoundary(); // nothing staged — must be a no-op

    assertSame(v1, agent.currentSnapshot(), "Boundary with nothing staged must be a no-op");
  }

  @Test
  void onMessageBoundary_clears_staged_slot() {
    ConfigSnapshot v2 =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 200_000).build();
    agent.stageSnapshot(v2);
    agent.onMessageBoundary(); // promotes v2 and clears staged

    // Second boundary with nothing new — no promotion, v2 stays current
    agent.onMessageBoundary();
    assertSame(v2, agent.currentSnapshot(), "Staged slot must be cleared after promotion");
  }

  // ── publish() contract ────────────────────────────────────────────────────

  /**
   * Discriminating test: publish() is immediate.
   *
   * <p>{@link ConfigService#publish} guarantees that subsequent {@link
   * ConfigService#currentSnapshot} calls return the published snapshot. No boundary call should be
   * required.
   */
  @Test
  void publish_isImmediate() {
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1L, T1).set(RISK_LIMIT, ConfigScope.global(), 750_000).build();
    agent.publish(v1);

    assertSame(
        v1,
        agent.currentSnapshot(),
        "publish() must be immediately visible without calling onMessageBoundary()");
    assertEquals(750_000, agent.currentSnapshot().getGlobal(RISK_LIMIT));
  }

  @Test
  void publish_multipleSequential_eachImmediatelyVisible() {
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1L, T1).set(RISK_LIMIT, ConfigScope.global(), 100_000).build();
    ConfigSnapshot v2 =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 200_000).build();
    ConfigSnapshot v3 =
        ConfigSnapshot.builder(3L, T1).set(RISK_LIMIT, ConfigScope.global(), 300_000).build();

    agent.publish(v1);
    assertSame(v1, agent.currentSnapshot());
    agent.publish(v2);
    assertSame(v2, agent.currentSnapshot());
    agent.publish(v3);
    assertSame(v3, agent.currentSnapshot());
  }

  // ── staged does not interfere with direct publish ─────────────────────────

  @Test
  void stageAndPublish_publishWins_untilNextBoundary() {
    ConfigSnapshot staged =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 200_000).build();
    ConfigSnapshot published =
        ConfigSnapshot.builder(3L, T1).set(RISK_LIMIT, ConfigScope.global(), 300_000).build();

    agent.stageSnapshot(staged);
    agent.publish(published); // direct publish wins immediately

    assertSame(
        published,
        agent.currentSnapshot(),
        "Direct publish must override regardless of staged snapshot");

    // When boundary fires, staged (v2) promotes — overwriting published (v3)
    agent.onMessageBoundary();
    assertSame(
        staged,
        agent.currentSnapshot(),
        "Boundary must promote the staged snapshot even if publish() was called after staging");
  }

  // ── sequence of messages ──────────────────────────────────────────────────

  @Test
  void multipleMessages_eachSeesStagedFromPriorBoundary() {
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1L, T1).set(RISK_LIMIT, ConfigScope.global(), 100_000).build();
    agent.publish(v1);

    // Message 1: stage v2 but don't fire boundary until after message ends
    ConfigSnapshot v2 =
        ConfigSnapshot.builder(2L, T1).set(RISK_LIMIT, ConfigScope.global(), 200_000).build();
    agent.stageSnapshot(v2);

    // End of message 1 — boundary fires
    agent.onMessageBoundary();
    assertSame(v2, agent.currentSnapshot(), "After first boundary, v2 must be current");

    // Message 2: stage v3 mid-message — still sees v2
    ConfigSnapshot v3 =
        ConfigSnapshot.builder(3L, T1).set(RISK_LIMIT, ConfigScope.global(), 300_000).build();
    agent.stageSnapshot(v3);
    assertSame(v2, agent.currentSnapshot(), "Mid-message: must still see v2");

    // End of message 2 — boundary fires, v3 promoted
    agent.onMessageBoundary();
    assertSame(v3, agent.currentSnapshot(), "After second boundary, v3 must be current");
  }
}
