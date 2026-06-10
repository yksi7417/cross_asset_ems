/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Package kind for a multi-leg order. Per arch-multileg.md. */
public enum MultiLegKind {
  /** FX swap: spot leg + forward leg, opposite sides. */
  SWAP,
  /** Options/futures spread: vertical, calendar, butterfly. */
  SPREAD,
  /** Futures roll: sell near + buy far on the same underlying. */
  ROLL,
  /** Delta-hedged option: option leg + underlying hedge leg. */
  DELTA_HEDGE,
  /** Portfolio trade: N securities settled atomically. */
  PT,
  /** Anything else with inter-dependent legs. */
  CUSTOM
}
