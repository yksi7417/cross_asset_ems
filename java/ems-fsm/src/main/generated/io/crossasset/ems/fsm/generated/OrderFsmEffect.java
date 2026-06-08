// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/order.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.Map;

/** Sealed effect descriptors for OrderFsmRunner transitions. */
public sealed interface OrderFsmEffect {

  /** Cascade an event to another FSM instance. */
  record EmitEvent(String targetFsm, String event) implements OrderFsmEffect {}

  /** Emit an outbound FIX message. */
  record PublishFixMessage(Map<String, String> args) implements OrderFsmEffect {}

  /** Append an event-log audit record. */
  record PublishEventLog(String event) implements OrderFsmEffect {}

  /** Schedule a timer (arch-time-replay-server). */
  record ScheduleTimer(Map<String, String> args) implements OrderFsmEffect {}

  /** Cancel a pending timer. */
  record CancelTimer(Map<String, String> args) implements OrderFsmEffect {}

  /** Notify subscribers. */
  record Notify(Map<String, String> args) implements OrderFsmEffect {}

  /** Stamp identity chaining trace fields. */
  record ChainIdentityStamp(Map<String, String> args) implements OrderFsmEffect {}
}
