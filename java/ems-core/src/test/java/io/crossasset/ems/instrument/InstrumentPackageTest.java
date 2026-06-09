/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for InstrumentPackage, Leg, PackageType, and Side domain types.
 *
 * <p>Task 4.18 — Package entity + Leg group schema.
 */
class InstrumentPackageTest {

  // ── PackageType enum ──────────────────────────────────────────────────────

  @Test
  void packageType_fromWire_roundTrips() {
    for (PackageType pt : PackageType.values()) {
      Optional<PackageType> decoded = PackageType.fromWire(pt.wireCode);
      assertTrue(decoded.isPresent(), "fromWire must find " + pt);
      assertEquals(pt, decoded.get());
    }
  }

  @Test
  void packageType_fromWire_unknownReturnsEmpty() {
    assertTrue(PackageType.fromWire(99).isEmpty());
  }

  @Test
  void packageType_wireCodesAreUnique() {
    long distinct =
        java.util.Arrays.stream(PackageType.values())
            .mapToInt(pt -> pt.wireCode)
            .distinct()
            .count();
    assertEquals(PackageType.values().length, distinct, "All PackageType wireCodes must be unique");
  }

  // ── Side enum ─────────────────────────────────────────────────────────────

  @Test
  void side_fromWire_roundTrips() {
    for (Side s : Side.values()) {
      Optional<Side> decoded = Side.fromWire(s.wireCode);
      assertTrue(decoded.isPresent(), "fromWire must find " + s);
      assertEquals(s, decoded.get());
    }
  }

  @Test
  void side_buyIs1_sellIs2_conformsToFix() {
    assertEquals(1, Side.BUY.wireCode, "BUY must be FIX tag 54 value 1");
    assertEquals(2, Side.SELL.wireCode, "SELL must be FIX tag 54 value 2");
    assertEquals(5, Side.SELL_SHORT.wireCode, "SELL_SHORT must be FIX tag 54 value 5");
  }

  // ── Leg record ────────────────────────────────────────────────────────────

  @Test
  void leg_nullOptionalOverrides_accepted() {
    Leg leg = new Leg(1, "BBG000B9XRY4", 1L, Side.BUY, new BigDecimal("100.00"), null);
    assertNull(leg.optionalOverrides());
    assertEquals(1, leg.legSeq());
    assertEquals("BBG000B9XRY4", leg.instrumentFigi());
    assertEquals(Side.BUY, leg.side());
    assertEquals(new BigDecimal("100.00"), leg.ratioOrQuantity());
  }

  @Test
  void leg_withOptionalOverrides_storedVerbatim() {
    byte[] overrides = {0x01, 0x02, 0x03};
    Leg leg = new Leg(2, "BBG000B9XRY4", 1L, Side.SELL, BigDecimal.ONE, overrides);
    assertArrayEquals(overrides, leg.optionalOverrides());
  }

  // ── InstrumentPackage record ──────────────────────────────────────────────

  @Test
  void instrumentPackage_singleLeg_isSingle() {
    Leg leg = new Leg(1, "BBG000B9XRY4", 1L, Side.BUY, new BigDecimal("100"), null);
    InstrumentPackage pkg =
        new InstrumentPackage(UUID.randomUUID(), PackageType.SINGLE, List.of(leg));
    assertTrue(pkg.isSingle());
    assertFalse(pkg.isMultiLeg());
    assertEquals(1, pkg.legCount());
  }

  @Test
  void instrumentPackage_multiLeg_twoLegs() {
    Leg near = new Leg(1, "BBG000B9XRY4", 1L, Side.BUY, BigDecimal.ONE, null);
    Leg far = new Leg(2, "BBG000B9XRY5", 1L, Side.SELL, BigDecimal.ONE, null);
    InstrumentPackage pkg =
        new InstrumentPackage(UUID.randomUUID(), PackageType.MULTI_LEG, List.of(near, far));
    assertFalse(pkg.isSingle());
    assertTrue(pkg.isMultiLeg());
    assertEquals(2, pkg.legCount());
  }

  @Test
  void instrumentPackage_paired_isMultiLeg() {
    Leg bond = new Leg(1, "BBG000B9XRY4", 1L, Side.BUY, new BigDecimal("1000000"), null);
    Leg repo = new Leg(2, "BBG000B9XRY6", 1L, Side.SELL, new BigDecimal("1000000"), null);
    InstrumentPackage pkg =
        new InstrumentPackage(UUID.randomUUID(), PackageType.PAIRED, List.of(bond, repo));
    assertTrue(pkg.isMultiLeg(), "PAIRED must be treated as multi-leg");
    assertEquals(2, pkg.legCount());
  }

  @Test
  void instrumentPackage_legCount_matchesLegsList() {
    List<Leg> legs =
        List.of(
            new Leg(1, "BBG000B9XRY4", 1L, Side.BUY, BigDecimal.ONE, null),
            new Leg(2, "BBG000B9XRY5", 1L, Side.SELL, BigDecimal.ONE, null),
            new Leg(3, "BBG000B9XRY6", 1L, Side.BUY, BigDecimal.ONE, null));
    InstrumentPackage pkg = new InstrumentPackage(UUID.randomUUID(), PackageType.BWIC_LIST, legs);
    assertEquals(legs.size(), pkg.legCount());
  }

  @Test
  void instrumentPackage_packageIdIsPreserved() {
    UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    InstrumentPackage pkg =
        new InstrumentPackage(
            id,
            PackageType.SINGLE,
            List.of(new Leg(1, "BBG000B9XRY4", 1L, Side.BUY, BigDecimal.ONE, null)));
    assertEquals(id, pkg.packageId());
  }
}
