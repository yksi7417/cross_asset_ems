// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multileg.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.Map;

/** Sealed effect descriptors for MultiLegFsmRunner transitions. */
public sealed interface MultiLegFsmEffect {

  /** Cascade an event to another FSM instance. */
  record EmitEvent(String targetFsm, String event) implements MultiLegFsmEffect {}

  /** Emit an outbound FIX message. */
  record PublishFixMessage(Map<String, String> args) implements MultiLegFsmEffect {}

  /** Append an event-log audit record. */
  record PublishEventLog(String event) implements MultiLegFsmEffect {}

  /** Schedule a timer (arch-time-replay-server). */
  record ScheduleTimer(Map<String, String> args) implements MultiLegFsmEffect {}

  /** Cancel a pending timer. */
  record CancelTimer(Map<String, String> args) implements MultiLegFsmEffect {}

  /** Notify subscribers. */
  record Notify(Map<String, String> args) implements MultiLegFsmEffect {}

  /** Stamp identity chaining trace fields. */
  record ChainIdentityStamp(Map<String, String> args) implements MultiLegFsmEffect {}
}
