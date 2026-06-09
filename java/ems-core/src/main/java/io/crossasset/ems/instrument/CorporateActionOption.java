/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/**
 * A holder-election option for corporate actions that offer choices (rights issues, tender offers).
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public record CorporateActionOption(String optionType, String description) {}
