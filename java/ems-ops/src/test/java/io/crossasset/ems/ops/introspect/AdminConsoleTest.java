/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.introspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.ops.introspect.AdminConsole.AdminRequest;
import io.crossasset.ems.ops.introspect.AdminConsole.AdminResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AdminConsole}: superuser tag gating, mandatory rationale, per-identity rate
 * limiting, full audit of granted and denied attempts. Per arch-jmx-introspection.md, task 14.2.
 */
class AdminConsoleTest {

  private final long[] now = {0L};
  private final List<String> injected = new ArrayList<>();
  private final AdminConsole console =
      new AdminConsole((firm, desk, user, tag) -> user.startsWith("su-"), () -> now[0], 3);

  private void registerTarget() {
    console.register(
        new AdminConsole.InjectionTarget() {
          @Override
          public String componentId() {
            return "oms.router";
          }

          @Override
          public String inject(String action, Map<String, String> args) {
            injected.add(action + ":" + args);
            return "applied " + action;
          }
        });
  }

  @Test
  void unqualifiedIdentity_denied_andJournaled_noDispatch() {
    registerTarget();
    AdminResult result = console.execute(request("trader-1", "inject_event", "stuck route"));
    assertThat(result).isInstanceOf(AdminResult.Denied.class);
    assertThat(((AdminResult.Denied) result).reason()).contains("#superuser-inject");
    assertThat(injected).isEmpty();
    assertThat(console.journal()).hasSize(1);
    assertThat(console.journal().get(0).granted()).isFalse();
  }

  @Test
  void qualifiedWithRationale_dispatchedAndJournaled() {
    registerTarget();
    AdminResult result =
        console.execute(request("su-ops-1", "inject_event", "replay stuck RouteFilled"));
    assertThat(result).isInstanceOf(AdminResult.Granted.class);
    assertThat(((AdminResult.Granted) result).result()).isEqualTo("applied inject_event");
    assertThat(injected).hasSize(1);
    AdminConsole.AdminAction action = console.journal().get(0);
    assertThat(action.granted()).isTrue();
    assertThat(action.user()).isEqualTo("su-ops-1");
    assertThat(action.rationale()).contains("stuck");
  }

  @Test
  void missingRationale_denied() {
    registerTarget();
    AdminResult result = console.execute(request("su-ops-1", "override_state", "  "));
    assertThat(result).isInstanceOf(AdminResult.Denied.class);
    assertThat(((AdminResult.Denied) result).reason()).contains("rationale");
  }

  @Test
  void rateLimit_perIdentityRollingMinute() {
    registerTarget();
    for (int i = 0; i < 3; i++) {
      assertThat(console.execute(request("su-ops-1", "inject_event", "r" + i)))
          .isInstanceOf(AdminResult.Granted.class);
    }
    AdminResult fourth = console.execute(request("su-ops-1", "inject_event", "r3"));
    assertThat(fourth).isInstanceOf(AdminResult.Denied.class);
    assertThat(((AdminResult.Denied) fourth).reason()).contains("Rate limit");

    // Another superuser is unaffected; the window frees after a minute.
    assertThat(console.execute(request("su-ops-2", "inject_event", "other")))
        .isInstanceOf(AdminResult.Granted.class);
    now[0] = 60_000L;
    assertThat(console.execute(request("su-ops-1", "inject_event", "later")))
        .isInstanceOf(AdminResult.Granted.class);
  }

  @Test
  void unknownComponent_denied() {
    AdminResult result = console.execute(request("su-ops-1", "inject_event", "x"));
    assertThat(result).isInstanceOf(AdminResult.Denied.class);
    assertThat(((AdminResult.Denied) result).reason()).contains("Unknown component");
  }

  private static AdminRequest request(String user, String action, String rationale) {
    return new AdminRequest(
        "firm-a", "desk-ops", user, "oms.router", action, Map.of("k", "v"), rationale);
  }
}
