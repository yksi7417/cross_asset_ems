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
 * Tests for CorporateActionType, CorporateActionState, CorporateActionSource, Money,
 * CorporateAction, CorporateActionEvent hierarchy, and CorporateActionBridge.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
class CorporateActionTest {

  private static final long T0 = 1_700_000_000_000L;
  private static final long T1 = 1_700_086_400_000L;

  // ── CorporateActionType ───────────────────────────────────────────────────

  @Test
  void caType_fromWire_roundTrips() {
    for (CorporateActionType t : CorporateActionType.values()) {
      Optional<CorporateActionType> found = CorporateActionType.fromWire(t.wireCode);
      assertTrue(found.isPresent(), "fromWire must find " + t);
      assertEquals(t, found.get());
    }
  }

  @Test
  void caType_fromWire_unknownCode_returnsEmpty() {
    assertTrue(CorporateActionType.fromWire(999).isEmpty());
  }

  @Test
  void caType_allExpectedValues_present() {
    assertNotNull(CorporateActionType.CASH_DIVIDEND);
    assertNotNull(CorporateActionType.STOCK_SPLIT);
    assertNotNull(CorporateActionType.REVERSE_SPLIT);
    assertNotNull(CorporateActionType.SPIN_OFF);
    assertNotNull(CorporateActionType.MERGER_ACQUISITION);
    assertNotNull(CorporateActionType.NAME_CHANGE);
    assertNotNull(CorporateActionType.REDEMPTION);
    assertNotNull(CorporateActionType.CONVERSION);
  }

  // ── CorporateActionState ──────────────────────────────────────────────────

  @Test
  void caState_fromWire_roundTrips() {
    for (CorporateActionState s : CorporateActionState.values()) {
      assertEquals(s, CorporateActionState.fromWire(s.wireCode).orElseThrow());
    }
  }

  @Test
  void caState_terminalStates() {
    assertTrue(CorporateActionState.APPLIED.isTerminal());
    assertTrue(CorporateActionState.CANCELLED.isTerminal());
    assertFalse(CorporateActionState.ANNOUNCED.isTerminal());
    assertFalse(CorporateActionState.LOCKED.isTerminal());
  }

  @Test
  void caState_validTransitions() {
    assertTrue(CorporateActionState.ANNOUNCED.canTransitionTo(CorporateActionState.LOCKED));
    assertTrue(CorporateActionState.ANNOUNCED.canTransitionTo(CorporateActionState.CANCELLED));
    assertTrue(CorporateActionState.LOCKED.canTransitionTo(CorporateActionState.APPLIED));
    assertTrue(CorporateActionState.LOCKED.canTransitionTo(CorporateActionState.CANCELLED));
  }

  @Test
  void caState_invalidTransitions_returnFalse() {
    assertFalse(CorporateActionState.APPLIED.canTransitionTo(CorporateActionState.CANCELLED));
    assertFalse(CorporateActionState.CANCELLED.canTransitionTo(CorporateActionState.ANNOUNCED));
    assertFalse(CorporateActionState.ANNOUNCED.canTransitionTo(CorporateActionState.APPLIED));
  }

  // ── CorporateAction record ─────────────────────────────────────────────────

  @Test
  void corporateAction_withState_transitionsCorrectly() {
    CorporateAction ca = splitCa(CorporateActionState.ANNOUNCED);
    CorporateAction locked = ca.withState(CorporateActionState.LOCKED);
    assertEquals(CorporateActionState.LOCKED, locked.state());
    assertEquals(ca.caId(), locked.caId());

    CorporateAction applied = locked.withState(CorporateActionState.APPLIED);
    assertEquals(CorporateActionState.APPLIED, applied.state());
    assertTrue(applied.isApplied());
  }

  @Test
  void corporateAction_withState_invalidTransition_throws() {
    CorporateAction ca = splitCa(CorporateActionState.APPLIED);
    assertThrows(IllegalStateException.class, () -> ca.withState(CorporateActionState.CANCELLED));
  }

  @Test
  void corporateAction_instrumentsAffected_isImmutable() {
    CorporateAction ca = splitCa(CorporateActionState.ANNOUNCED);
    assertThrows(UnsupportedOperationException.class, () -> ca.instrumentsAffected().add("HACK"));
  }

  @Test
  void corporateAction_nullInstrumentsAffected_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CorporateAction(
                UUID.randomUUID(),
                CorporateActionType.STOCK_SPLIT,
                CorporateActionSource.DTCC,
                "REF-001",
                null,
                T0,
                T0,
                T0,
                T0,
                BigDecimal.valueOf(2),
                null,
                null,
                null,
                CorporateActionState.ANNOUNCED));
  }

  // ── Money ─────────────────────────────────────────────────────────────────

  @Test
  void money_of_roundTrips() {
    Money m = Money.of(BigDecimal.valueOf(1_25, 2), CurrencyCode.USD);
    assertEquals(BigDecimal.valueOf(1_25, 2), m.amount());
    assertEquals(CurrencyCode.USD, m.currency());
  }

  @Test
  void money_nullAmount_throws() {
    assertThrows(IllegalArgumentException.class, () -> new Money(null, CurrencyCode.USD));
  }

  // ── CorporateActionEvent hierarchy ────────────────────────────────────────

  @Test
  void caEvent_sealedHierarchy_exhaustiveSwitchCompiles() {
    UUID caId = UUID.randomUUID();
    CorporateActionEvent event =
        new CorporateActionEvent.Announced(
            caId, CorporateActionSource.DTCC, splitCa(CorporateActionState.ANNOUNCED), T0);
    String kind =
        switch (event) {
          case CorporateActionEvent.Announced e -> "announced";
          case CorporateActionEvent.Updated e -> "updated";
          case CorporateActionEvent.Locked e -> "locked";
          case CorporateActionEvent.Applied e -> "applied";
          case CorporateActionEvent.Cancelled e -> "cancelled";
          case CorporateActionEvent.Discrepancy e -> "discrepancy";
        };
    assertEquals("announced", kind);
  }

  @Test
  void caEvent_applied_fieldsAccessible() {
    UUID caId = UUID.randomUUID();
    CorporateActionEvent.Applied ev = new CorporateActionEvent.Applied(caId, T1, 3, T1);
    assertEquals(caId, ev.caId());
    assertEquals(T1, ev.appliedAt());
    assertEquals(3, ev.affectedPositionsCount());
  }

  @Test
  void caEvent_updated_fieldsListIsImmutable() {
    UUID caId = UUID.randomUUID();
    CorporateActionEvent.Updated ev =
        new CorporateActionEvent.Updated(
            caId, List.of("ratio", "exDate"), CorporateActionSource.BLOOMBERG_CACS, T0);
    assertThrows(UnsupportedOperationException.class, () -> ev.fieldsChanged().add("hack"));
  }

  // ── CorporateActionBridge ─────────────────────────────────────────────────

  @Test
  void bridge_toSupersession_producesCorrectEvent() {
    UUID caId = UUID.randomUUID();
    CorporateAction ca = splitCa(CorporateActionState.APPLIED, caId);
    InstrumentVersioned newV = appleV2(T1, caId);

    SecurityMasterEvent.InstrumentSuperseded event =
        CorporateActionBridge.toSupersession(ca, "BBG000B9XRY4", 1L, newV, T1);

    assertEquals("BBG000B9XRY4", event.figi());
    assertEquals(1L, event.priorVersionSeq());
    assertEquals(newV, event.newVersion());
    assertEquals(T1, event.occurredAt());
  }

  @Test
  void bridge_toSupersession_notApplied_throws() {
    UUID caId = UUID.randomUUID();
    CorporateAction ca = splitCa(CorporateActionState.LOCKED, caId);
    InstrumentVersioned newV = appleV2(T1, caId);
    assertThrows(
        IllegalStateException.class,
        () -> CorporateActionBridge.toSupersession(ca, "BBG000B9XRY4", 1L, newV, T1));
  }

  @Test
  void bridge_toSupersession_causedByMismatch_throws() {
    UUID caId = UUID.randomUUID();
    UUID wrongId = UUID.randomUUID();
    CorporateAction ca = splitCa(CorporateActionState.APPLIED, caId);
    InstrumentVersioned newV = appleV2(T1, wrongId);
    assertThrows(
        IllegalStateException.class,
        () -> CorporateActionBridge.toSupersession(ca, "BBG000B9XRY4", 1L, newV, T1));
  }

  @Test
  void bridge_toRetirement_producesCorrectEvent() {
    UUID caId = UUID.randomUUID();
    CorporateAction ca =
        new CorporateAction(
            caId,
            CorporateActionType.REDEMPTION,
            CorporateActionSource.MANUAL,
            "REDM-001",
            List.of("BBG000BPH459"),
            T0,
            T0,
            T1,
            T1,
            null,
            Money.of(BigDecimal.valueOf(100), CurrencyCode.USD),
            null,
            null,
            CorporateActionState.APPLIED);

    SecurityMasterEvent.InstrumentRetired event =
        CorporateActionBridge.toRetirement(ca, "BBG000BPH459", 1L, LifecycleStatus.MATURED, T1);

    assertEquals("BBG000BPH459", event.figi());
    assertEquals(1L, event.versionSeq());
    assertEquals(LifecycleStatus.MATURED, event.terminalStatus());
  }

  @Test
  void bridge_toRetirement_activeStatus_throws() {
    UUID caId = UUID.randomUUID();
    CorporateAction ca = splitCa(CorporateActionState.APPLIED, caId);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CorporateActionBridge.toRetirement(ca, "BBG000B9XRY4", 1L, LifecycleStatus.ACTIVE, T1));
  }

  @Test
  void bridge_integrationFlow_supersessionAppearsInSnapshot() {
    UUID caId = UUID.randomUUID();
    CorporateAction ca = splitCa(CorporateActionState.APPLIED, caId);
    InstrumentVersioned v1 = appleV1(T0);
    InstrumentVersioned v2 = appleV2(T1, caId);

    SecurityMasterSnapshot s0 =
        SecurityMasterSnapshot.EMPTY.apply(new SecurityMasterEvent.InstrumentCreated(v1, T0));
    SecurityMasterEvent.InstrumentSuperseded superseded =
        CorporateActionBridge.toSupersession(ca, "BBG000B9XRY4", 1L, v2, T1);
    SecurityMasterSnapshot s1 = s0.apply(superseded);

    assertEquals(2L, s1.lookup("BBG000B9XRY4").orElseThrow().versionSeq());
    assertEquals(caId, s1.lookup("BBG000B9XRY4").orElseThrow().causedByCorporateActionEventId());
    assertTrue(s1.lookup("BBG000B9XRY4", 1L).isPresent(), "Prior version must still be pinnable");
  }

  // ── Fixtures ──────────────────────────────────────────────────────────────

  private static CorporateAction splitCa(CorporateActionState state) {
    return splitCa(state, UUID.randomUUID());
  }

  private static CorporateAction splitCa(CorporateActionState state, UUID caId) {
    return new CorporateAction(
        caId,
        CorporateActionType.STOCK_SPLIT,
        CorporateActionSource.DTCC,
        "DTCC-SPLIT-001",
        List.of("BBG000B9XRY4"),
        T0,
        T0,
        T0,
        T0,
        BigDecimal.valueOf(2),
        null,
        null,
        null,
        state);
  }

  private static InstrumentVersioned appleV1(long t) {
    return new InstrumentVersioned(appleCore(1L, t, Long.MAX_VALUE), null);
  }

  private static InstrumentVersioned appleV2(long t, UUID caId) {
    return new InstrumentVersioned(appleCore(2L, t, Long.MAX_VALUE), caId);
  }

  private static InstrumentCore appleCore(long version, long from, long to) {
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
        from,
        to,
        version,
        null,
        T0,
        T0);
  }
}
