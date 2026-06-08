// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesession.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM states for {@link VenueSessionFsmRunner}. */
public enum VenueSessionFsmState {
  /** initial. */
  DISCONNECTED,
  CONNECTING,
  LOGON_SENT,
  ACTIVE,
  TEST_REQUEST_SENT,
  RESEND_IN_PROGRESS,
  SEQUENCE_RESETTING,
  LOGOUT_IN_PROGRESS;

  public boolean isTerminal() {
    return switch (this) {
      default -> false;
    };
  }

  public boolean isInitial() {
    return switch (this) {
      case DISCONNECTED -> true;
      default -> false;
    };
  }
}
