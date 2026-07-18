/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

/**
 * Single source of truth for trader-desktop default settings.
 *
 * Components import `TRADING_DEFAULTS` instead of inlining magic literals.
 * Values here are safe to override per deployment via build-time or runtime config.
 */

export interface TradingDefaults {
  /** Default click-to-trade order size on an ESP tile. */
  espClickQty: number;
  /** Default max slippage guard in basis points on an ESP tile. */
  espMaxSlippageBp: number;
  /** MIC codes the ticket may route to. */
  routableVenues: readonly string[];
  /** ms without a stream frame before the client treats a socket as stale. */
  streamStaleMs: number;
}

export const TRADING_DEFAULTS: TradingDefaults = {
  espClickQty: 1_000_000,
  espMaxSlippageBp: 5,
  routableVenues: ["XNAS", "XNYS", "ARCX"],
  streamStaleMs: 10_000,
};
