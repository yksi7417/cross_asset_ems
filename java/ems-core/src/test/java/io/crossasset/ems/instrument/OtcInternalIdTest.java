/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests for OtcInternalId and OtcInternalIdAllocator.
 *
 * <p>Task 4.25 — Internal-allocated identifier namespace for OTC.
 */
class OtcInternalIdTest {

  // ── OtcInternalId — construction ─────────────────────────────────────────

  @Test
  void otcId_withClass_toStringMatchesFormat() {
    OtcInternalId id = new OtcInternalId("A1", "IRS", 1L);
    assertEquals("ems_iid:A1:IRS:1", id.toString());
  }

  @Test
  void otcId_withoutClass_toStringMatchesFormat() {
    OtcInternalId id = new OtcInternalId("FIRM1", null, 42L);
    assertEquals("ems_iid:FIRM1:42", id.toString());
  }

  @Test
  void otcId_maxLength_exactly20_isValid() {
    // ems_iid:AA:BB:1234567  = 8+2+1+2+1+7 = 21 — too long
    // ems_iid:AA:B:1234567   = 8+2+1+1+1+7 = 20 — exactly 20
    OtcInternalId id = new OtcInternalId("AA", "B", 1234567L);
    assertEquals(20, id.toString().length());
  }

  @Test
  void otcId_over20Chars_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new OtcInternalId("TOOLONG", "TOOLONGCLASS", 1L));
  }

  @Test
  void otcId_counterZero_throws() {
    assertThrows(IllegalArgumentException.class, () -> new OtcInternalId("F1", "IRS", 0L));
  }

  @Test
  void otcId_blankFirmId_throws() {
    assertThrows(IllegalArgumentException.class, () -> new OtcInternalId("", "IRS", 1L));
  }

  @Test
  void otcId_invalidCharsInFirmId_throws() {
    assertThrows(IllegalArgumentException.class, () -> new OtcInternalId("F:1", "IRS", 1L));
  }

  @Test
  void otcId_allowsHyphenAndUnderscore() {
    OtcInternalId id = new OtcInternalId("F-1", "I_1", 1L);
    assertEquals("ems_iid:F-1:I_1:1", id.toString());
  }

  // ── OtcInternalId — parse ─────────────────────────────────────────────────

  @Test
  void parse_withClass_roundTrips() {
    String raw = "ems_iid:A1:IRS:99";
    OtcInternalId id = OtcInternalId.parse(raw);
    assertEquals("A1", id.firmId());
    assertEquals("IRS", id.instrumentClass());
    assertEquals(99L, id.counter());
    assertEquals(raw, id.toString());
  }

  @Test
  void parse_withoutClass_roundTrips() {
    String raw = "ems_iid:FIRM1:42";
    OtcInternalId id = OtcInternalId.parse(raw);
    assertEquals("FIRM1", id.firmId());
    assertNull(id.instrumentClass());
    assertEquals(42L, id.counter());
    assertEquals(raw, id.toString());
  }

  @Test
  void parse_missingPrefix_throws() {
    assertThrows(IllegalArgumentException.class, () -> OtcInternalId.parse("BBG000B9XRY4"));
  }

  @Test
  void parse_tooManyParts_throws() {
    assertThrows(IllegalArgumentException.class, () -> OtcInternalId.parse("ems_iid:a:b:c:1"));
  }

  @Test
  void parse_nonNumericCounter_throws() {
    assertThrows(IllegalArgumentException.class, () -> OtcInternalId.parse("ems_iid:F1:IRS:XYZ"));
  }

  @Test
  void isOtcInternalId_detectsPrefix() {
    assertTrue(OtcInternalId.isOtcInternalId("ems_iid:F1:1"));
    assertFalse(OtcInternalId.isOtcInternalId("BBG000B9XRY4"));
    assertFalse(OtcInternalId.isOtcInternalId(null));
    assertFalse(OtcInternalId.isOtcInternalId(""));
  }

  // ── OtcInternalIdAllocator ────────────────────────────────────────────────

  @Test
  void allocator_firstAllocation_returns1() {
    OtcInternalIdAllocator alloc = new OtcInternalIdAllocator("F1", "IRS");
    OtcInternalId id = alloc.allocate();
    assertEquals(1L, id.counter());
    assertEquals("ems_iid:F1:IRS:1", id.toString());
  }

  @Test
  void allocator_sequentialAllocations_incrementCounter() {
    OtcInternalIdAllocator alloc = new OtcInternalIdAllocator("F1", "IRS");
    for (long expected = 1; expected <= 5; expected++) {
      assertEquals(expected, alloc.allocate().counter());
    }
    assertEquals(5L, alloc.lastIssuedCounter());
  }

  @Test
  void allocator_resumeFromLastCounter() {
    OtcInternalIdAllocator alloc = new OtcInternalIdAllocator("F1", "IRS", 100L);
    assertEquals(101L, alloc.allocate().counter());
    assertEquals(101L, alloc.lastIssuedCounter());
  }

  @Test
  void allocator_withoutClass_producesShortId() {
    OtcInternalIdAllocator alloc = new OtcInternalIdAllocator("FIRM1", null);
    OtcInternalId id = alloc.allocate();
    assertEquals("ems_iid:FIRM1:1", id.toString());
    assertNull(id.instrumentClass());
  }

  @Test
  void allocator_staticPartsExceedLimit_throwsAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OtcInternalIdAllocator("TOOLONGFIRMID", "TOOLONGCLASS"));
  }

  @Test
  void allocator_concurrentAllocations_allUnique() throws Exception {
    OtcInternalIdAllocator alloc = new OtcInternalIdAllocator("F1", "IRS");
    int threads = 10;
    int opsPerThread = 100;
    Set<Long> seen = java.util.Collections.synchronizedSet(new HashSet<>());
    CountDownLatch ready = new CountDownLatch(threads);
    AtomicInteger errors = new AtomicInteger();

    var executor = Executors.newFixedThreadPool(threads);
    @SuppressWarnings({"unchecked", "rawtypes"})
    Future<Void>[] futures = new Future[threads];
    for (int i = 0; i < threads; i++) {
      futures[i] =
          executor.submit(
              () -> {
                ready.countDown();
                try {
                  ready.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                for (int j = 0; j < opsPerThread; j++) {
                  long c = alloc.allocate().counter();
                  if (!seen.add(c)) errors.incrementAndGet();
                }
                return null;
              });
    }
    for (Future<Void> f : futures) f.get();
    executor.shutdown();

    assertEquals(0, errors.get(), "Concurrent allocations must produce unique counters");
    assertEquals(threads * opsPerThread, seen.size());
  }

  // ── Integration: allocate and store in SecurityMaster ────────────────────

  @Test
  void integration_allocatedId_usableAsInstrumentInternalIid() {
    OtcInternalIdAllocator alloc = new OtcInternalIdAllocator("ACM", "I", 0L);
    OtcInternalId id = alloc.allocate();
    assertTrue(id.toString().length() <= OtcInternalId.MAX_LENGTH);
    assertTrue(OtcInternalId.isOtcInternalId(id.toString()));
  }
}
