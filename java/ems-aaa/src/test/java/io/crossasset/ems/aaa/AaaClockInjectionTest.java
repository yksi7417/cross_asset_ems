/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.core.clock.FixedTimeSource;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The service's javadoc claimed the clock was injected for months while the code called {@code
 * System.currentTimeMillis()}. These tests are what make the claim true rather than aspirational;
 * {@code scripts/ci/checks/no_raw_clock.py} keeps it that way.
 */
class AaaClockInjectionTest {

  private static final long FIXED_MILLIS = 1_700_000_000_000L;

  @Test
  void sessionTimestampsComeFromTheInjectedTimeSource() {
    FixedTimeSource time = new FixedTimeSource(FIXED_MILLIS);
    InMemoryAaaService service =
        new InMemoryAaaService(new InMemoryAaaEventLog(), null, null, time);
    service.registerCredential("tok", "FIRM1", "DESK1", "trader1", Set.of("order-entry"));

    LogonOutcome outcome = service.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok"));

    assertThat(outcome).isInstanceOf(LogonOutcome.Accepted.class);
    assertThat(((LogonOutcome.Accepted) outcome).session().establishedAtMicros())
        .isEqualTo(FIXED_MILLIS * 1_000L);
  }

  @Test
  void twoRunsOnTheSameFixedTimeProduceTheSameTimestamp() {
    // This is the property replay and the polyglot conformance gate need: the
    // same inputs and the same clock produce the same bytes.
    assertThat(establishedAt()).isEqualTo(establishedAt());
  }

  private static long establishedAt() {
    InMemoryAaaService service =
        new InMemoryAaaService(
            new InMemoryAaaEventLog(), null, null, new FixedTimeSource(FIXED_MILLIS));
    service.registerCredential("tok", "FIRM1", "DESK1", "trader1", Set.of());
    LogonOutcome outcome = service.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok"));
    return ((LogonOutcome.Accepted) outcome).session().establishedAtMicros();
  }

  @Test
  void theDefaultConstructorStillUsesWallTime() {
    // 47 call sites depend on the existing constructors; injection is additive.
    InMemoryAaaService service = new InMemoryAaaService(new InMemoryAaaEventLog());
    service.registerCredential("tok", "FIRM1", "DESK1", "trader1", Set.of());

    LogonOutcome outcome = service.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok"));

    assertThat(((LogonOutcome.Accepted) outcome).session().establishedAtMicros()).isPositive();
  }
}
