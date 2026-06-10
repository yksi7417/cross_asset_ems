/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/**
 * Observable state of a single leg, derived from its route's FSM state (PENDING when the leg has
 * not been dispatched yet). Per arch-multileg.md.
 */
public enum LegState {
  PENDING,
  ROUTING,
  FILLED,
  CANCELED,
  REJECTED
}
