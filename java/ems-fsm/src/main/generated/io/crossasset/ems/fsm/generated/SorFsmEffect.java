// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sor.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.Map;

/** Sealed effect descriptors for SorFsmRunner transitions. */
public sealed interface SorFsmEffect {

  /** Cascade an event to another FSM instance. */
  record EmitEvent(String targetFsm, String event) implements SorFsmEffect {}

  /** Emit an outbound FIX message. */
  record PublishFixMessage(Map<String, String> args) implements SorFsmEffect {}

  /** Append an event-log audit record. */
  record PublishEventLog(String event) implements SorFsmEffect {}

  /** Schedule a timer (arch-time-replay-server). */
  record ScheduleTimer(Map<String, String> args) implements SorFsmEffect {}

  /** Cancel a pending timer. */
  record CancelTimer(Map<String, String> args) implements SorFsmEffect {}

  /** Notify subscribers. */
  record Notify(Map<String, String> args) implements SorFsmEffect {}

  /** Stamp identity chaining trace fields. */
  record ChainIdentityStamp(Map<String, String> args) implements SorFsmEffect {}
}
