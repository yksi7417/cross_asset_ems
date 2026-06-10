/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Map;

/**
 * A named event that triggers rule evaluation in the {@link AutomationEngine}.
 *
 * <p>Event names match the internal FIX-aligned lifecycle events (e.g. {@code "OrderAccepted"},
 * {@code "OrderReplaced"}) and the optional properties carry context extracted from the event
 * payload (e.g. {@code {"asset_class", "FX"}, {"tif", "DAY"}}).
 */
public record AutomationEvent(String eventName, String orderId, Map<String, String> properties) {

  public AutomationEvent(String eventName, String orderId) {
    this(eventName, orderId, Map.of());
  }
}
