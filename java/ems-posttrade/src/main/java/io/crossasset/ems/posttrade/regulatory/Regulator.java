/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

/**
 * Regulators / report destinations the service can submit to (per
 * arch-regulatory-reporting-service.md). A single trade can be reportable to several independently;
 * the MVP wires {@link #TRACE} for US IG corp bonds (12.6). The rest are declared for the
 * determination matrix and are filled in post-MVP.
 */
public enum Regulator {
  TRACE,
  MSRB_RTRS,
  CFTC_SDR,
  FINRA_CAT,
  MIFIR_RTS22,
  EMIR_TR,
  UK_EMIR_TR
}
