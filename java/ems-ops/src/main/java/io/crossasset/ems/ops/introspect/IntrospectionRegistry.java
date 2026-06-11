/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.introspect;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovery + aggregation over {@link Introspectable} components (task 14.1): components
 * self-register at startup; UIs/CLIs enumerate them and their schemas without hard-coding.
 * Aggregate health is worst-of (any RED → RED, else any YELLOW → YELLOW), with the offending
 * components named — the shape the ops dashboard and the blue/green switchover checks (14.5)
 * consume.
 */
public final class IntrospectionRegistry {

  private final ConcurrentHashMap<String, Introspectable> components = new ConcurrentHashMap<>();

  /** Self-registration at component startup. Replaces any prior registration of the same id. */
  public void register(Introspectable component) {
    components.put(Objects.requireNonNull(component.componentId(), "componentId"), component);
  }

  public void deregister(String componentId) {
    components.remove(componentId);
  }

  /** All registered component ids, sorted (deterministic enumeration). */
  public List<String> list() {
    return components.keySet().stream().sorted().toList();
  }

  public Optional<Introspectable> find(String componentId) {
    return Optional.ofNullable(components.get(componentId));
  }

  /** Worst-of aggregate health across every registered component. */
  public Introspectable.Health aggregateHealth() {
    Introspectable.Health.Status worst = Introspectable.Health.Status.GREEN;
    StringBuilder reasons = new StringBuilder();
    for (String id : list()) {
      Introspectable.Health health = components.get(id).health();
      if (health.status() != Introspectable.Health.Status.GREEN) {
        if (!reasons.isEmpty()) {
          reasons.append("; ");
        }
        reasons
            .append(id)
            .append(": ")
            .append(health.status())
            .append(" (")
            .append(health.reason())
            .append(")");
      }
      if (health.status().ordinal() > worst.ordinal()) {
        worst = health.status();
      }
    }
    return new Introspectable.Health(worst, reasons.toString());
  }
}
