/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.validator;

/**
 * The eight fixed evaluation layers per arch-validator.md. A reject reports the first failing
 * layer; subsequent layers are not evaluated.
 *
 * <p>Task 6.2 — Layered evaluation pipeline.
 */
public enum ValidationLayer {
  /** Layer 1: sequence number, auth, heartbeat liveness. */
  SESSION,
  /** Layer 2: user/desk/firm membership active. */
  IDENTITY,
  /** Layer 3: FIGI resolves, license covers requested identifier. */
  REFERENCE,
  /** Layer 4: {@code arch-tag-permissions} AND-gate per category. */
  PERMISSION,
  /** Layer 5: typed extension rules (e.g. FX value date). Stubbed until Phase 6.4. */
  ASSET_CLASS,
  /** Layer 6: per-desk/user/counterparty notional and count caps. Stubbed until Phase 10. */
  LIMITS,
  /** Layer 7: limit-vs-last sanity. Stubbed until Phase 9. */
  MARKET,
  /** Layer 8: venue/dialect compatibility, account enablement. Stubbed until Phase 7. */
  ROUTE,
}
