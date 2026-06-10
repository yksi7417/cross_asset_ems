/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import java.util.Objects;

/**
 * One typed event on a subscription stream. {@code seq} is monotonic per topic — a subscription is
 * a cursor over this sequence, so a resumed subscriber replays from its last delivered seq and
 * never collapses intermediate states (arch-api-first.md § Resume).
 */
public record ApiEvent(String topic, long seq, String type, String refId, String payload) {
  public ApiEvent {
    Objects.requireNonNull(topic, "topic");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(refId, "refId");
    Objects.requireNonNull(payload, "payload");
  }
}
