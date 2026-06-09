/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

import java.util.UUID;

/**
 * Immutable audit record for a single licensed identifier access attempt.
 *
 * <p>Captured for every attempt against a licensed identifier type, whether the access is {@link
 * AccessOutcome#GRANTED} or {@link AccessOutcome#DENIED}. Denied attempts represent licensing
 * probes and must be retained.
 *
 * <p>Task 4.2 — license-metering and audit.
 */
public record AccessRecord(
    UUID requestId,
    String identity,
    SymbologyService.IdType idType,
    String value,
    AccessOutcome outcome) {}
