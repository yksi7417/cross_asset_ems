/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for RefDataRecord, HolidayCalendar, DayCountConvention, TickSizeEntry, TickSizeRegime,
 * RefDataEvent hierarchy, RefDataSnapshot, and InMemoryRefDataService.
 *
 * <p>Task 4.21 — Reference data service.
 */
class RefDataServiceTest {

  private static final long T0 = 1_700_000_000_000L;
  private static final long T1 = 1_700_086_400_000L;

  // ── RefDataRecord ─────────────────────────────────────────────────────────

  @Test
  void refDataRecord_isActive_andOpenEnded() {
    RefDataRecord<String> r = activeRecord("cal", "USD", "USD holiday calendar", T0);
    assertTrue(r.isActive());
    assertTrue(r.isOpenEnded());
  }

  @Test
  void refDataRecord_amend_incrementsVersion() {
    RefDataRecord<String> r = activeRecord("cal", "USD", "v1", T0);
    RefDataRecord<String> r2 = r.amend("v2", T1, "ops", T1, "updated holidays");
    assertEquals(2, r2.version());
    assertEquals("v2", r2.value());
    assertEquals(RefDataStatus.ACTIVE, r2.status());
  }

  @Test
  void refDataRecord_retire_setsRetiredStatus() {
    RefDataRecord<String> r = activeRecord("cal", "USD", "v1", T0);
    RefDataRecord<String> retired = r.retire(T1, "ops", T1, "calendar replaced");
    assertEquals(RefDataStatus.RETIRED, retired.status());
    assertEquals(T1, retired.expiryDate());
  }

  @Test
  void refDataRecord_nullValue_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefDataRecord<>(
                "cal",
                "USD",
                null,
                1,
                T0,
                Long.MAX_VALUE,
                RefDataStatus.ACTIVE,
                "sys",
                T0,
                "initial"));
  }

  // ── HolidayCalendar ───────────────────────────────────────────────────────

  @Test
  void holidayCalendar_isHoliday_detectsKnownHoliday() {
    long newYears2025 = LocalDate.of(2025, 1, 1).toEpochDay();
    HolidayCalendar cal = HolidayCalendar.of("USD", "US Federal", Set.of(newYears2025), Set.of());
    assertTrue(cal.isHoliday(newYears2025));
    assertFalse(cal.isHoliday(newYears2025 + 1));
  }

  @Test
  void holidayCalendar_isWeekend_detectsSaturdayAndSunday() {
    // 2025-01-04 is Saturday; 2025-01-05 is Sunday; 2025-01-06 is Monday
    long saturday = LocalDate.of(2025, 1, 4).toEpochDay();
    long sunday = LocalDate.of(2025, 1, 5).toEpochDay();
    long monday = LocalDate.of(2025, 1, 6).toEpochDay();
    HolidayCalendar cal = HolidayCalendar.of("USD", "US Federal", Set.of(), Set.of());
    assertTrue(cal.isWeekend(saturday));
    assertTrue(cal.isWeekend(sunday));
    assertFalse(cal.isWeekend(monday));
  }

  @Test
  void holidayCalendar_isBusinessDay_falseOnHolidayAndWeekend() {
    long newYears2025 = LocalDate.of(2025, 1, 1).toEpochDay(); // Wednesday
    long saturday = LocalDate.of(2025, 1, 4).toEpochDay();
    long tuesday = LocalDate.of(2025, 1, 7).toEpochDay();
    HolidayCalendar cal = HolidayCalendar.of("USD", "US Federal", Set.of(newYears2025), Set.of());
    assertFalse(cal.isBusinessDay(newYears2025), "Holiday is not a business day");
    assertFalse(cal.isBusinessDay(saturday), "Saturday is not a business day");
    assertTrue(cal.isBusinessDay(tuesday), "Tuesday Jan 7 is a business day");
  }

  @Test
  void holidayCalendar_nextBusinessDay_skipsHolidayAndWeekend() {
    // Friday Dec 31 2021 → skip Sat Jan 1 (holiday), Sun Jan 2, Mon Jan 3 (holiday)
    // 2021-12-31 is Friday
    long fri = LocalDate.of(2021, 12, 31).toEpochDay();
    long sat = LocalDate.of(2022, 1, 1).toEpochDay();
    long sun = LocalDate.of(2022, 1, 2).toEpochDay();
    long mon = LocalDate.of(2022, 1, 3).toEpochDay(); // assume holiday too
    long tue = LocalDate.of(2022, 1, 4).toEpochDay();
    HolidayCalendar cal =
        HolidayCalendar.of(
            "USD", "US Federal", Set.of(sat, mon), Set.of()); // Sat + Mon are holidays
    // next business day AFTER Friday (Friday itself is a business day, so start from Fri)
    assertEquals(fri, cal.nextBusinessDay(fri), "Friday should be a business day itself");
    assertEquals(
        tue, cal.nextBusinessDay(sat), "Next business day after holiday Sat should skip to Tue");
  }

  @Test
  void holidayCalendar_isHalfDay() {
    long halfDay = LocalDate.of(2025, 12, 24).toEpochDay();
    HolidayCalendar cal = HolidayCalendar.of("USD", "US Federal", Set.of(), Set.of(halfDay));
    assertTrue(cal.isHalfDay(halfDay));
    assertFalse(cal.isHalfDay(halfDay + 1));
  }

  @Test
  void holidayCalendar_setIsImmutable() {
    long day = LocalDate.of(2025, 1, 1).toEpochDay();
    HolidayCalendar cal = HolidayCalendar.of("USD", "US Federal", Set.of(day), Set.of());
    assertThrows(UnsupportedOperationException.class, () -> cal.holidayEpochDays().add(99L));
  }

  // ── DayCountConvention ────────────────────────────────────────────────────

  @Test
  void dayCountConvention_fields_accessible() {
    DayCountConvention dcc =
        new DayCountConvention(
            "ACT/360",
            "Actual/360",
            1,
            "ACT_360",
            "Actual days / 360",
            "(d2 - d1) / 360",
            List.of("rates", "money_market"));
    assertEquals("ACT/360", dcc.code());
    assertEquals(1, dcc.fpmlId());
    assertEquals("ACT_360", dcc.fixmlName());
    assertTrue(dcc.useCases().contains("rates"));
  }

  @Test
  void dayCountConvention_useCases_isImmutable() {
    DayCountConvention dcc =
        new DayCountConvention(
            "ACT/360", "Actual/360", 1, "ACT_360", "desc", "formula", List.of("rates"));
    assertThrows(UnsupportedOperationException.class, () -> dcc.useCases().add("hack"));
  }

  // ── TickSizeEntry ─────────────────────────────────────────────────────────

  @Test
  void tickSizeEntry_contains_inRange() {
    TickSizeEntry e =
        new TickSizeEntry(BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("0.01"), 100);
    assertTrue(e.contains(BigDecimal.ONE));
    assertTrue(e.contains(new BigDecimal("5.00")));
    assertFalse(e.contains(BigDecimal.ZERO));
    assertFalse(e.contains(BigDecimal.TEN)); // exclusive upper
  }

  @Test
  void tickSizeEntry_openEndedTop_matchesAllAboveLow() {
    TickSizeEntry e = new TickSizeEntry(BigDecimal.TEN, null, new BigDecimal("0.05"), 10);
    assertTrue(e.contains(BigDecimal.TEN));
    assertTrue(e.contains(new BigDecimal("9999")));
    assertFalse(e.contains(new BigDecimal("9.99")));
  }

  @Test
  void tickSizeEntry_negativeTickSize_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TickSizeEntry(BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("-0.01"), 100));
  }

  // ── TickSizeRegime ────────────────────────────────────────────────────────

  @Test
  void tickSizeRegime_lookupTickSize_findsCorrectBand() {
    TickSizeRegime regime =
        new TickSizeRegime(
            1,
            "LSE Standard",
            List.of(
                new TickSizeEntry(BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("0.01"), 1),
                new TickSizeEntry(BigDecimal.TEN, null, new BigDecimal("0.05"), 1)));
    assertEquals(
        new BigDecimal("0.01"), regime.lookupTickSize(new BigDecimal("5.00")).orElseThrow());
    assertEquals(
        new BigDecimal("0.05"), regime.lookupTickSize(new BigDecimal("15.00")).orElseThrow());
  }

  @Test
  void tickSizeRegime_lookupTickSize_outsideAllBands_returnsEmpty() {
    TickSizeRegime regime =
        new TickSizeRegime(
            1,
            "Test",
            List.of(
                new TickSizeEntry(
                    BigDecimal.TEN, new BigDecimal("100"), new BigDecimal("0.01"), 1)));
    assertTrue(regime.lookupTickSize(new BigDecimal("5")).isEmpty());
    assertTrue(regime.lookupTickSize(new BigDecimal("100")).isEmpty()); // exclusive upper
  }

  @Test
  void tickSizeRegime_emptyEntries_throws() {
    assertThrows(IllegalArgumentException.class, () -> new TickSizeRegime(1, "Empty", List.of()));
  }

  // ── RefDataEvent + RefDataSnapshot ────────────────────────────────────────

  @Test
  void snapshot_empty_lookupReturnsEmpty() {
    RefDataSnapshot<String> snap = RefDataSnapshot.empty();
    assertTrue(snap.lookup("USD").isEmpty());
    assertEquals(0, snap.size());
  }

  @Test
  void snapshot_applyAdded_lookupReturnsRecord() {
    RefDataRecord<String> rec = activeRecord("cal", "USD", "USD calendar", T0);
    RefDataSnapshot<String> snap =
        RefDataSnapshot.<String>empty().apply(new RefDataEvent.Added<>(rec, T0));
    assertTrue(snap.lookup("USD").isPresent());
    assertEquals("USD calendar", snap.lookup("USD").get().value());
  }

  @Test
  void snapshot_applyAmended_replacesRecord() {
    RefDataRecord<String> rec = activeRecord("cal", "USD", "v1", T0);
    RefDataSnapshot<String> s0 =
        RefDataSnapshot.<String>empty().apply(new RefDataEvent.Added<>(rec, T0));
    RefDataRecord<String> amended = rec.amend("v2", T1, "ops", T1, "updated");
    RefDataSnapshot<String> s1 = s0.apply(new RefDataEvent.Amended<>(amended, T1));
    assertEquals("v2", s1.lookup("USD").get().value());
    assertEquals(2, s1.lookup("USD").get().version());
  }

  @Test
  void snapshot_applyRetired_setsRetiredStatus() {
    RefDataRecord<String> rec = activeRecord("cal", "USD", "v1", T0);
    RefDataSnapshot<String> s0 =
        RefDataSnapshot.<String>empty().apply(new RefDataEvent.Added<>(rec, T0));
    RefDataSnapshot<String> s1 = s0.apply(new RefDataEvent.Retired<>("cal", "USD", T1));
    assertEquals(RefDataStatus.RETIRED, s1.lookup("USD").get().status());
  }

  @Test
  void snapshot_applyRetired_unknownKey_throws() {
    assertThrows(
        IllegalStateException.class,
        () ->
            RefDataSnapshot.<String>empty()
                .apply(new RefDataEvent.Retired<>("cal", "UNKNOWN", T0)));
  }

  @Test
  void snapshot_isImmutable_applyDoesNotMutatePrior() {
    RefDataSnapshot<String> s0 = RefDataSnapshot.empty();
    RefDataRecord<String> rec = activeRecord("cal", "USD", "v1", T0);
    RefDataSnapshot<String> s1 = s0.apply(new RefDataEvent.Added<>(rec, T0));
    assertEquals(0, s0.size(), "Original snapshot must not be mutated");
    assertEquals(1, s1.size());
  }

  @Test
  void snapshot_sealedHierarchy_exhaustiveSwitchCompiles() {
    RefDataEvent<String> event = new RefDataEvent.Added<>(activeRecord("cal", "USD", "v1", T0), T0);
    String kind =
        switch (event) {
          case RefDataEvent.Added<String> e -> "added";
          case RefDataEvent.Amended<String> e -> "amended";
          case RefDataEvent.Retired<String> e -> "retired";
        };
    assertEquals("added", kind);
  }

  // ── InMemoryRefDataService ────────────────────────────────────────────────

  @Test
  void service_initialSnapshot_isEmpty() {
    InMemoryRefDataService<String> svc = new InMemoryRefDataService<>();
    assertEquals(0, svc.currentSnapshot().size());
  }

  @Test
  void service_publish_replacesSnapshot() {
    InMemoryRefDataService<String> svc = new InMemoryRefDataService<>();
    RefDataRecord<String> rec = activeRecord("cal", "USD", "v1", T0);
    RefDataSnapshot<String> snap =
        RefDataSnapshot.<String>empty().apply(new RefDataEvent.Added<>(rec, T0));
    svc.publish(snap);
    assertEquals(1, svc.currentSnapshot().size());
    assertTrue(svc.currentSnapshot().lookup("USD").isPresent());
  }

  // ── Fixtures ──────────────────────────────────────────────────────────────

  private static RefDataRecord<String> activeRecord(
      String domain, String key, String value, long changedAt) {
    return new RefDataRecord<>(
        domain,
        key,
        value,
        1,
        changedAt,
        Long.MAX_VALUE,
        RefDataStatus.ACTIVE,
        "system",
        changedAt,
        "initial");
  }
}
