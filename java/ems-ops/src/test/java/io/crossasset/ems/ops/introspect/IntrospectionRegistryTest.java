/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.introspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.ops.introspect.Introspectable.Health;
import io.crossasset.ems.ops.introspect.Introspectable.Metric;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IntrospectionRegistry}: self-registration/discovery, state dump against the
 * descriptor, worst-of health aggregation with named offenders. Per arch-jmx-introspection.md, task
 * 14.1.
 */
class IntrospectionRegistryTest {

  private final IntrospectionRegistry registry = new IntrospectionRegistry();

  @Test
  void register_listSorted_findById() {
    registry.register(component("oms.router", Health.green()));
    registry.register(component("aaa.sessions", Health.green()));
    assertThat(registry.list()).containsExactly("aaa.sessions", "oms.router");
    assertThat(registry.find("oms.router")).isPresent();
    assertThat(registry.find("nope")).isEmpty();
  }

  @Test
  void dumpState_coversEveryDescribedField() {
    Introspectable component = component("oms.router", Health.green());
    registry.register(component);
    Introspectable found = registry.find("oms.router").orElseThrow();
    assertThat(found.dumpState().keySet()).isEqualTo(found.describeState().keySet());
    assertThat(found.listMetrics()).hasSize(1);
  }

  @Test
  void aggregateHealth_allGreen() {
    registry.register(component("a", Health.green()));
    registry.register(component("b", Health.green()));
    assertThat(registry.aggregateHealth().status()).isEqualTo(Health.Status.GREEN);
  }

  @Test
  void aggregateHealth_yellowDegrades_namedOffender() {
    registry.register(component("a", Health.green()));
    registry.register(component("venue.simx", new Health(Health.Status.YELLOW, "reconnecting")));
    Health aggregate = registry.aggregateHealth();
    assertThat(aggregate.status()).isEqualTo(Health.Status.YELLOW);
    assertThat(aggregate.reason()).contains("venue.simx").contains("reconnecting");
  }

  @Test
  void aggregateHealth_redWinsOverYellow() {
    registry.register(component("a", new Health(Health.Status.YELLOW, "degraded")));
    registry.register(component("b", new Health(Health.Status.RED, "event log lag")));
    Health aggregate = registry.aggregateHealth();
    assertThat(aggregate.status()).isEqualTo(Health.Status.RED);
    assertThat(aggregate.reason()).contains("a:").contains("b:");
  }

  @Test
  void deregister_removesComponent() {
    registry.register(component("a", Health.green()));
    registry.deregister("a");
    assertThat(registry.list()).isEmpty();
  }

  private static Introspectable component(String id, Health health) {
    return new Introspectable() {
      @Override
      public String componentId() {
        return id;
      }

      @Override
      public Map<String, String> describeState() {
        return Map.of("orders_open", "gauge:long", "last_event_id", "string");
      }

      @Override
      public Map<String, String> dumpState() {
        return Map.of("orders_open", "42", "last_event_id", "EVT-7");
      }

      @Override
      public List<Metric> listMetrics() {
        return List.of(new Metric("messages_in_total", Metric.Kind.COUNTER, 1234));
      }

      @Override
      public Health health() {
        return health;
      }
    };
  }
}
