/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.core.clock.Timestamp;
import org.junit.jupiter.api.Test;

/**
 * Tests for the configuration service domain model: cascade resolution, archival discipline, and
 * golden-replay equivalence.
 *
 * <h3>Discriminating tests</h3>
 *
 * <ul>
 *   <li>{@code cascade_deskShadowsFirm} — desk-level value overrides firm default; proves most-
 *       specific-wins ordering, not just "a get returns something".
 *   <li>{@code cascade_deskRemoved_fallsThroughToFirm} — when desk-level value is absent, the
 *       resolver falls through to firm. Proves sparse-level fall-through.
 *   <li>{@code archival_returnsLockedDefault_andRejectsNewWrites} — archived key always returns its
 *       locked default; {@link ConfigSnapshot.Builder#set} rejects archived keys. The
 *       never-undefined replay contract must hold.
 * </ul>
 *
 * <p>Task 3.7 — Configuration service.
 */
class ConfigServiceTest {

  private static final Timestamp T0 = Timestamp.ofEpochMillis(1_000L);

  private static final ConfigKey<Integer> RISK_LIMIT =
      ConfigKey.of("risk_limit_usd", Integer.class, 1_000_000);

  // ── cascade resolution ────────────────────────────────────────────────────────

  /**
   * Discriminating cascade test: a desk-level override (firmA / deskB = 500_000) shadows the
   * firm-level default (firmA = 800_000). If the resolver checks DESK first this returns 500_000; a
   * reversed or flat implementation returns 800_000 and fails.
   */
  @Test
  void cascade_deskShadowsFirm() {
    ConfigSnapshot snap =
        ConfigSnapshot.builder(1, T0)
            .set(RISK_LIMIT, ConfigScope.firm("firmA"), 800_000)
            .set(RISK_LIMIT, ConfigScope.desk("firmA", "deskB"), 500_000)
            .build();

    ResolutionContext ctx = ResolutionContext.builder().firmId("firmA").deskId("deskB").build();

    assertEquals(
        500_000,
        snap.get(RISK_LIMIT, ctx),
        "DESK scope must shadow FIRM scope (most-specific wins)");
  }

  /**
   * Sparse fall-through: only the firm-level value is set. A desk context falls through to firm
   * because desk is absent. Verifies that the resolver doesn't stop at the first non-matching
   * level.
   */
  @Test
  void cascade_deskAbsent_fallsThroughToFirm() {
    ConfigSnapshot snap =
        ConfigSnapshot.builder(2, T0)
            .set(RISK_LIMIT, ConfigScope.firm("firmA"), 800_000)
            // No desk-level entry
            .build();

    ResolutionContext ctx = ResolutionContext.builder().firmId("firmA").deskId("deskB").build();

    assertEquals(
        800_000, snap.get(RISK_LIMIT, ctx), "Absent DESK scope must fall through to FIRM scope");
  }

  @Test
  void cascade_noScopedValue_returnsKeyDefault() {
    ConfigSnapshot snap = ConfigSnapshot.builder(3, T0).build(); // empty snapshot

    ResolutionContext ctx = ResolutionContext.builder().firmId("firmA").build();

    assertEquals(
        1_000_000,
        snap.get(RISK_LIMIT, ctx),
        "No scoped value in snapshot must return the key's default");
  }

  @Test
  void cascade_globalScopeOverridesKeyDefault() {
    ConfigSnapshot snap =
        ConfigSnapshot.builder(4, T0).set(RISK_LIMIT, ConfigScope.global(), 750_000).build();

    // Context has no other qualifiers, so only GLOBAL matches
    assertEquals(750_000, snap.getGlobal(RISK_LIMIT));
    // Full context also resolves via GLOBAL when no higher scope is set
    ResolutionContext ctx = ResolutionContext.builder().firmId("firmX").build();
    assertEquals(750_000, snap.get(RISK_LIMIT, ctx));
  }

  @Test
  void cascade_fullHierarchy_mostSpecificWins() {
    ConfigKey<String> mode = ConfigKey.of("trading_mode", String.class, "paper");
    ConfigSnapshot snap =
        ConfigSnapshot.builder(5, T0)
            .set(mode, ConfigScope.global(), "live")
            .set(mode, ConfigScope.environment("prod"), "live-prod")
            .set(mode, ConfigScope.firm("firmA"), "live-firmA")
            .set(mode, ConfigScope.desk("firmA", "deskB"), "live-deskB")
            .set(mode, ConfigScope.user("firmA", "deskB", "trader1"), "paper-override")
            .build();

    ResolutionContext ctx =
        ResolutionContext.builder()
            .environment("prod")
            .firmId("firmA")
            .deskId("deskB")
            .userId("trader1")
            .build();

    assertEquals(
        "paper-override", snap.get(mode, ctx), "USER scope must win over all lower scopes");
  }

  // ── archival discipline ───────────────────────────────────────────────────────

  /**
   * Archival test: once a key is archived with a locked default, {@code get()} returns that locked
   * default (no snapshot values are set — archive implies no new writes). Attempting to set a value
   * on the archived key in a new builder throws, enforcing the never-delete contract.
   */
  @Test
  void archival_returnsLockedDefault_andRejectsNewWrites() {
    ConfigKey<Integer> fatFinger = ConfigKey.of("fat_finger_bps", Integer.class, 500).archive(500);

    assertTrue(fatFinger.archived());

    // Reading from an empty snapshot returns the locked default
    ConfigSnapshot snap = ConfigSnapshot.builder(10, T0).build();
    assertEquals(
        500,
        snap.get(fatFinger, ResolutionContext.globalOnly()),
        "Archived key must return its locked default when no snapshot value exists");

    // New writes are rejected
    assertThrows(
        IllegalArgumentException.class,
        () -> ConfigSnapshot.builder(11, T0).set(fatFinger, ConfigScope.global(), 999),
        "Setting a value on an archived key must throw");
  }

  @Test
  void archival_nullLockedDefault_throws() {
    ConfigKey<Integer> key = ConfigKey.of("some_key", Integer.class, 42);
    assertThrows(
        NullPointerException.class,
        () -> key.archive(null),
        "archive(null) must throw — a null locked default violates the never-undefined contract");
  }

  @Test
  void archival_activeKeyNull_throws() {
    assertThrows(
        NullPointerException.class,
        () -> ConfigKey.of("k", Integer.class, null),
        "ConfigKey.of with null defaultValue must throw");
  }

  // ── snapshot version and timestamp ───────────────────────────────────────────

  @Test
  void snapshot_versionAndTimestamp_roundtrip() {
    Timestamp ts = Timestamp.ofEpochMillis(9_999L);
    ConfigSnapshot snap = ConfigSnapshot.builder(42L, ts).build();

    assertEquals(42L, snap.version());
    assertEquals(ts, snap.effectiveAt());
  }

  // ── golden-replay equivalence ─────────────────────────────────────────────────

  /**
   * Golden-replay guarantee: two services that receive the same published snapshot sequence must
   * resolve identical values for all keys — the prerequisite for byte-identical replay.
   */
  @Test
  void golden_twoServices_samePublishSequence_identicalResolution() {
    ConfigKey<Integer> limit = ConfigKey.of("position_limit", Integer.class, 10_000);
    ResolutionContext ctx = ResolutionContext.builder().firmId("firmA").deskId("deskC").build();

    InMemoryConfigService svc1 = new InMemoryConfigService(T0);
    InMemoryConfigService svc2 = new InMemoryConfigService(T0);

    // Both services receive the same two published snapshots
    for (InMemoryConfigService svc : new InMemoryConfigService[] {svc1, svc2}) {
      ConfigSnapshot v1 =
          ConfigSnapshot.builder(1, T0).set(limit, ConfigScope.firm("firmA"), 50_000).build();
      svc.publish(v1);

      ConfigSnapshot v2 =
          ConfigSnapshot.builder(2, T0)
              .set(limit, ConfigScope.firm("firmA"), 50_000)
              .set(limit, ConfigScope.desk("firmA", "deskC"), 25_000)
              .build();
      svc.publish(v2);
    }

    assertEquals(
        svc1.currentSnapshot().get(limit, ctx),
        svc2.currentSnapshot().get(limit, ctx),
        "Two services with identical publish sequence must resolve the same value");
  }

  // ── ConfigService publish ─────────────────────────────────────────────────────

  @Test
  void inMemoryService_publish_replacesSnapshot() {
    InMemoryConfigService svc = new InMemoryConfigService(T0);

    ConfigKey<String> mode = ConfigKey.of("mode", String.class, "paper");
    ConfigSnapshot v1 =
        ConfigSnapshot.builder(1, T0).set(mode, ConfigScope.global(), "live").build();
    svc.publish(v1);

    assertEquals("live", svc.currentSnapshot().getGlobal(mode));

    ConfigSnapshot v2 = ConfigSnapshot.builder(2, T0).build(); // no overrides
    svc.publish(v2);

    assertEquals(
        "paper",
        svc.currentSnapshot().getGlobal(mode),
        "After publishing an empty snapshot the key default must be returned");
  }
}
