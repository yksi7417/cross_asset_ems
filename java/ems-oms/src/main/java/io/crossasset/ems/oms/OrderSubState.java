/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/**
 * EMS-internal sub-states within FIX OrdStatus=0 (New). STAGED and READY are not visible in FIX
 * execution reports; they are staging-layer concepts only. ROUTING is set when the router takes
 * ownership of the order in Phase 7.2.
 */
public enum OrderSubState {
  /** Freshly staged; not yet ready for routing. */
  NEW,
  /** Has pending compliance / approval actions. */
  STAGED,
  /** Validator re-passed; ready for the router to pick up. */
  READY,
  /** Router has taken ownership; order is in flight to venue. */
  ROUTING
}
