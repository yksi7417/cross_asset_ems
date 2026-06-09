/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.Objects;

/**
 * Active session record, established at logon.
 *
 * <p>Task 5.1 — AAA service skeleton. TraceContext added in task 5.4.
 */
public record Session(long sessionId, Identity identity, long establishedAtMicros) {

  public Session {
    Objects.requireNonNull(identity, "identity");
  }
}
