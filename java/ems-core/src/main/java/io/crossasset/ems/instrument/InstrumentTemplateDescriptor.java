/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/**
 * Descriptor for a registered SBE instrument template.
 *
 * @param templateId SBE {@code MessageHeader.templateId} used for dispatch; matches the {@code id}
 *     attribute on the {@code <sbe:message>} element.
 * @param name Canonical message name; matches the {@code name} attribute on {@code <sbe:message>}.
 * @param assetClass High-level grouping for filtering.
 * @param schemaFile Project-root-relative path to the SBE XML schema file.
 */
public record InstrumentTemplateDescriptor(
    int templateId, String name, InstrumentAssetClass assetClass, String schemaFile) {}
