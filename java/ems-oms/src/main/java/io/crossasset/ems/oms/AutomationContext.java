/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;

/**
 * Evaluation context supplied to each {@link AutomationRule}'s condition predicate.
 *
 * <p>Contains the order that triggered the event and the set of routes already open against it.
 * Conditions must not mutate either — they are pure predicates for replay determinism.
 */
public record AutomationContext(StagedOrder order, List<Route> routes) {

  public AutomationContext(StagedOrder order) {
    this(order, List.of());
  }
}
