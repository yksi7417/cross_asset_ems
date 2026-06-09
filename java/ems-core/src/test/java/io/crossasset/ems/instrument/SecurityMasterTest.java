/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for InstrumentVersioned, SecurityMasterEvent hierarchy, SecurityMasterSnapshot, and
 * InMemorySecurityMasterService.
 *
 * <p>Task 4.19 — Security master CRUD + supersession events.
 */
class SecurityMasterTest {

  private static final long T0 = 1_700_000_000_000L;
  private static final long T1 = 1_700_086_400_000L;
  private static final long T2 = 1_700_172_800_000L;

  // ── InstrumentVersioned ───────────────────────────────────────────────────

  @Test
  void instrumentVersioned_delegatesHelperMethodsToCore() {
    InstrumentVersioned iv = appleV1(T0);
    assertEquals("BBG000B9XRY4", iv.figi());
    assertEquals(1L, iv.versionSeq());
    assertTrue(iv.isActive());
    assertTrue(iv.isOpenEnded());
    assertEquals(AssetClass.EQUITY, iv.assetClass());
    assertEquals(CurrencyCode.USD, iv.currency());
    assertEquals(LifecycleStatus.ACTIVE, iv.lifecycleStatus());
    assertNull(iv.causedByCorporateActionEventId());
  }

  @Test
  void instrumentVersioned_withCausingEvent_storedVerbatim() {
    UUID causeId = UUID.randomUUID();
    InstrumentVersioned iv = new InstrumentVersioned(appleCore(1L, T0, Long.MAX_VALUE), causeId);
    assertEquals(causeId, iv.causedByCorporateActionEventId());
  }

  // ── SecurityMasterSnapshot — create ──────────────────────────────────────

  @Test
  void snapshot_initialEmpty_lookupReturnsEmpty() {
    assertTrue(SecurityMasterSnapshot.EMPTY.lookup("BBG000B9XRY4").isEmpty());
    assertEquals(0, SecurityMasterSnapshot.EMPTY.figiCount());
  }

  @Test
  void snapshot_applyCreated_lookupByFigiReturnsIt() {
    InstrumentVersioned iv = appleV1(T0);
    SecurityMasterSnapshot s =
        SecurityMasterSnapshot.EMPTY.apply(new SecurityMasterEvent.InstrumentCreated(iv, T0));
    Optional<InstrumentVersioned> found = s.lookup("BBG000B9XRY4");
    assertTrue(found.isPresent());
    assertEquals(1L, found.get().versionSeq());
  }

  @Test
  void snapshot_applyCreated_lookupByFigiAndVersionReturnsIt() {
    InstrumentVersioned iv = appleV1(T0);
    SecurityMasterSnapshot s =
        SecurityMasterSnapshot.EMPTY.apply(new SecurityMasterEvent.InstrumentCreated(iv, T0));
    Optional<InstrumentVersioned> pinned = s.lookup("BBG000B9XRY4", 1L);
    assertTrue(pinned.isPresent());
    assertEquals(iv, pinned.get());
  }

  @Test
  void snapshot_lookupUnknownVersion_returnsEmpty() {
    InstrumentVersioned iv = appleV1(T0);
    SecurityMasterSnapshot s =
        SecurityMasterSnapshot.EMPTY.apply(new SecurityMasterEvent.InstrumentCreated(iv, T0));
    assertTrue(s.lookup("BBG000B9XRY4", 99L).isEmpty());
  }

  // ── SecurityMasterSnapshot — supersede ───────────────────────────────────

  @Test
  void snapshot_applySuperseded_latestLookupReturnsNewVersion() {
    SecurityMasterSnapshot s0 =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(appleV1(T0), T0));
    InstrumentVersioned v2 = appleV2(T1);
    SecurityMasterSnapshot s1 =
        s0.apply(new SecurityMasterEvent.InstrumentSuperseded("BBG000B9XRY4", 1L, v2, T1));
    Optional<InstrumentVersioned> latest = s1.lookup("BBG000B9XRY4");
    assertTrue(latest.isPresent());
    assertEquals(2L, latest.get().versionSeq(), "Latest must return the superseding version");
  }

  @Test
  void snapshot_applySuperseded_priorVersionStillPinnable() {
    SecurityMasterSnapshot s0 =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(appleV1(T0), T0));
    SecurityMasterSnapshot s1 =
        s0.apply(new SecurityMasterEvent.InstrumentSuperseded("BBG000B9XRY4", 1L, appleV2(T1), T1));
    Optional<InstrumentVersioned> pinned = s1.lookup("BBG000B9XRY4", 1L);
    assertTrue(pinned.isPresent(), "Prior version must remain pinnable for replay");
    assertEquals(1L, pinned.get().versionSeq());
  }

  @Test
  void snapshot_figiCount_incrementsOnEachNewFigi() {
    SecurityMasterSnapshot s0 =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(appleV1(T0), T0));
    assertEquals(1, s0.figiCount());
    InstrumentVersioned bond = bondV1(T0);
    SecurityMasterSnapshot s1 = s0.apply(new SecurityMasterEvent.InstrumentCreated(bond, T0));
    assertEquals(2, s1.figiCount());
  }

  // ── SecurityMasterSnapshot — retire ──────────────────────────────────────

  @Test
  void snapshot_applyRetired_lifecycleStatusIsTerminal() {
    SecurityMasterSnapshot s0 =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(appleV1(T0), T0));
    SecurityMasterSnapshot s1 =
        s0.apply(
            new SecurityMasterEvent.InstrumentRetired(
                "BBG000B9XRY4", 1L, LifecycleStatus.EXPIRED, T1));
    Optional<InstrumentVersioned> retired = s1.lookup("BBG000B9XRY4", 1L);
    assertTrue(retired.isPresent());
    assertEquals(LifecycleStatus.EXPIRED, retired.get().lifecycleStatus());
    assertFalse(retired.get().isActive());
    assertFalse(
        retired.get().isOpenEnded(), "Retired instrument must have effectiveTo == effectiveFrom");
  }

  @Test
  void snapshot_applyRetired_unknownFigiThrows() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SecurityMasterSnapshot.EMPTY.apply(
                new SecurityMasterEvent.InstrumentRetired(
                    "BBG000UNKNOWN", 1L, LifecycleStatus.EXPIRED, T0)));
  }

  @Test
  void snapshot_isImmutable_applyDoesNotMutatePriorSnapshot() {
    SecurityMasterSnapshot s0 = SecurityMasterSnapshot.EMPTY;
    SecurityMasterSnapshot s1 =
        s0.apply(new SecurityMasterEvent.InstrumentCreated(appleV1(T0), T0));
    assertEquals(0, s0.figiCount(), "Original snapshot must not be mutated by apply");
    assertEquals(1, s1.figiCount());
  }

  // ── InMemorySecurityMasterService ─────────────────────────────────────────

  @Test
  void service_initialSnapshot_isEmpty() {
    InMemorySecurityMasterService svc = new InMemorySecurityMasterService();
    assertEquals(0, svc.currentSnapshot().figiCount());
  }

  @Test
  void service_publish_replacesCurrentSnapshot() {
    InMemorySecurityMasterService svc = new InMemorySecurityMasterService();
    SecurityMasterSnapshot snap =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(appleV1(T0), T0));
    svc.publish(snap);
    assertEquals(1, svc.currentSnapshot().figiCount());
    assertTrue(svc.currentSnapshot().lookup("BBG000B9XRY4").isPresent());
  }

  // ── SecurityMasterEvent pattern matching ──────────────────────────────────

  @Test
  void event_sealedHierarchy_exhaustiveSwitchCompiles() {
    SecurityMasterEvent event = new SecurityMasterEvent.InstrumentCreated(appleV1(T0), T0);
    String kind =
        switch (event) {
          case SecurityMasterEvent.InstrumentCreated e -> "created:" + e.instrument().figi();
          case SecurityMasterEvent.InstrumentSuperseded e -> "superseded:" + e.figi();
          case SecurityMasterEvent.InstrumentRetired e -> "retired:" + e.figi();
        };
    assertEquals("created:BBG000B9XRY4", kind);
  }

  // ── Fixtures ──────────────────────────────────────────────────────────────

  private static InstrumentVersioned appleV1(long occurredAt) {
    return new InstrumentVersioned(appleCore(1L, occurredAt, Long.MAX_VALUE), null);
  }

  private static InstrumentVersioned appleV2(long occurredAt) {
    return new InstrumentVersioned(appleCore(2L, occurredAt, Long.MAX_VALUE), UUID.randomUUID());
  }

  private static InstrumentCore appleCore(long version, long effectiveFrom, long effectiveTo) {
    return new InstrumentCore(
        "BBG000B9XRY4",
        "EMS:FIRM1:0001",
        "BBG000B9Y5X2",
        "BBG001S5N8V8",
        AssetClass.EQUITY,
        InstrumentType.COMMON_STOCK,
        "AAPL",
        "Apple Inc.",
        "HWUPKR0MPOU8FGXBT394",
        CurrencyCode.USD,
        "US",
        "US",
        Fungibility.FUNGIBLE,
        SettlementConvention.T_PLUS_1,
        0,
        LifecycleStatus.ACTIVE,
        effectiveFrom,
        effectiveTo,
        version,
        null,
        T0,
        T0);
  }

  private static InstrumentVersioned bondV1(long occurredAt) {
    InstrumentCore core =
        new InstrumentCore(
            "BBG000BPH459",
            "EMS:FIRM1:0002",
            null,
            null,
            AssetClass.FIXED_INCOME,
            InstrumentType.CORPORATE_SENIOR,
            "AAPL 3.85 05/04/43",
            "Apple Inc 3.85% Senior Notes due 2043",
            "HWUPKR0MPOU8FGXBT394",
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.ACTIVE,
            occurredAt,
            Long.MAX_VALUE,
            1L,
            null,
            occurredAt,
            occurredAt);
    return new InstrumentVersioned(core, null);
  }
}
