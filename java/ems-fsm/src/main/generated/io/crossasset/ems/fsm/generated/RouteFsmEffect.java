// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/route.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.Map;

/** Sealed effect descriptors for RouteFsmRunner transitions. */
public sealed interface RouteFsmEffect {

  /** Cascade an event to another FSM instance. */
  record EmitEvent(String targetFsm, String event) implements RouteFsmEffect {}

  /** Emit an outbound FIX message. */
  record PublishFixMessage(Map<String, String> args) implements RouteFsmEffect {}

  /** Append an event-log audit record. */
  record PublishEventLog(String event) implements RouteFsmEffect {}

  /** Schedule a timer (arch-time-replay-server). */
  record ScheduleTimer(Map<String, String> args) implements RouteFsmEffect {}

  /** Cancel a pending timer. */
  record CancelTimer(Map<String, String> args) implements RouteFsmEffect {}

  /** Notify subscribers. */
  record Notify(Map<String, String> args) implements RouteFsmEffect {}

  /** Stamp identity chaining trace fields. */
  record ChainIdentityStamp(Map<String, String> args) implements RouteFsmEffect {}
}
