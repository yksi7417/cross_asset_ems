// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesession.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import java.util.Map;

/** Sealed effect descriptors for VenueSessionFsmRunner transitions. */
public sealed interface VenueSessionFsmEffect {

  /** Cascade an event to another FSM instance. */
  record EmitEvent(String targetFsm, String event) implements VenueSessionFsmEffect {}

  /** Emit an outbound FIX message. */
  record PublishFixMessage(Map<String, String> args) implements VenueSessionFsmEffect {}

  /** Append an event-log audit record. */
  record PublishEventLog(String event) implements VenueSessionFsmEffect {}

  /** Schedule a timer (arch-time-replay-server). */
  record ScheduleTimer(Map<String, String> args) implements VenueSessionFsmEffect {}

  /** Cancel a pending timer. */
  record CancelTimer(Map<String, String> args) implements VenueSessionFsmEffect {}

  /** Notify subscribers. */
  record Notify(Map<String, String> args) implements VenueSessionFsmEffect {}

  /** Stamp identity chaining trace fields. */
  record ChainIdentityStamp(Map<String, String> args) implements VenueSessionFsmEffect {}
}
