/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

/**
 * Delivery callback for subscription events — the edge binding (WS push, native SDK callback, test
 * recorder) implements this. Replayed (resumed) and live events arrive through the same method;
 * ordering per subscription is guaranteed by topic seq.
 */
@FunctionalInterface
public interface ApiEventSink {

  void deliver(long sessionId, String subscriptionId, ApiEvent event);
}
