/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.math.BigDecimal;

/**
 * A single leg within an {@link InstrumentPackage}, as defined in arch-security-master.
 *
 * <p>{@code optionalOverrides} is null when no package-specific overrides are present (e.g. no
 * delta-hedge ratio). {@code instrumentVersion} pins the exact version of the instrument at trade
 * time; consumers cross-reference with the security master via FIGI + version.
 *
 * <p>Task 4.18 — Package entity + Leg group schema.
 */
public record Leg(
    int legSeq,
    String instrumentFigi,
    long instrumentVersion,
    Side side,
    BigDecimal ratioOrQuantity,
    byte[] optionalOverrides) {}
