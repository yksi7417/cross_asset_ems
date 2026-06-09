/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Holiday calendar for a currency or market.
 *
 * <p>Dates are stored as epoch-day values ({@code LocalDate.toEpochDay()}) for efficient set
 * lookup. Weekend days default to Saturday and Sunday but can be overridden per calendar.
 *
 * <p>Task 4.21 — Reference data service.
 */
public record HolidayCalendar(
    String calendarId,
    String description,
    Set<Long> holidayEpochDays,
    Set<Long> halfDayEpochDays,
    Set<DayOfWeek> weekendDays) {

  public HolidayCalendar {
    if (calendarId == null || calendarId.isBlank())
      throw new IllegalArgumentException("calendarId required");
    holidayEpochDays = Set.copyOf(holidayEpochDays);
    halfDayEpochDays = Set.copyOf(halfDayEpochDays);
    weekendDays = Set.copyOf(weekendDays);
  }

  /** Standard Mon-Fri calendar with Sat-Sun weekends. */
  public static HolidayCalendar of(
      String calendarId, String description, Set<Long> holidays, Set<Long> halfDays) {
    return new HolidayCalendar(
        calendarId, description, holidays, halfDays, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
  }

  public boolean isHoliday(long epochDay) {
    return holidayEpochDays.contains(epochDay);
  }

  public boolean isHalfDay(long epochDay) {
    return halfDayEpochDays.contains(epochDay);
  }

  public boolean isWeekend(long epochDay) {
    return weekendDays.contains(DayOfWeek.of(Math.floorMod(epochDay + 3, 7) + 1));
  }

  public boolean isBusinessDay(long epochDay) {
    return !isWeekend(epochDay) && !isHoliday(epochDay);
  }

  /** Returns the next business day at or after {@code epochDay}. */
  public long nextBusinessDay(long epochDay) {
    long d = epochDay;
    while (!isBusinessDay(d)) d++;
    return d;
  }

  /** Returns the previous business day at or before {@code epochDay}. */
  public long previousBusinessDay(long epochDay) {
    long d = epochDay;
    while (!isBusinessDay(d)) d--;
    return d;
  }
}
