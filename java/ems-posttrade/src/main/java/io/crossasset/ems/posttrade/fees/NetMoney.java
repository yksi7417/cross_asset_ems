/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

/**
 * The money decomposition of one allocation (task 12.13) — what a confirm actually shows. All
 * fixed-point 1e4. Sign convention: {@code netMoney} is what the CLIENT pays (buy, positive) or
 * receives (sell, positive) — buys pay gross + charges + accrued, sells receive gross − charges
 * + accrued (the seller earns the accrued coupon).
 *
 * @param gross qty × price (bond gross uses clean price per 100 face)
 * @param commission broker commission after min/max bounds
 * @param fees regulatory levies + per-unit exchange fees
 * @param accruedInterest bond accrued coupon (0 for non-FI)
 * @param netMoney the all-in settlement amount per the sign convention above
 */
public record NetMoney(long gross, long commission, long fees, long accruedInterest, long netMoney) {}
