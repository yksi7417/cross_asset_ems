// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/route.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM states for {@link RouteFsmRunner}. */
public enum RouteFsmState {
  /** initial. */
  PENDING,
  SENT,
  /** OrdStatus=A, ExecType=A. */
  PENDING_NEW_AT_VENUE,
  /** OrdStatus=0, ExecType=0. */
  WORKING,
  /** OrdStatus=E, ExecType=E. */
  PENDING_REPLACE_AT_VENUE,
  /** OrdStatus=6, ExecType=6. */
  PENDING_CANCEL_AT_VENUE,
  /** OrdStatus=1, ExecType=F. */
  PARTIALLY_FILLED,
  /** terminal, OrdStatus=2, ExecType=F. */
  FILLED,
  /** terminal, OrdStatus=4, ExecType=4. */
  CANCELED,
  /** terminal, OrdStatus=8, ExecType=8. */
  REJECTED,
  /** terminal, OrdStatus=C, ExecType=C. */
  EXPIRED,
  /** terminal. */
  SUPERSEDED,
  /** terminal. */
  ANOMALY;

  public boolean isTerminal() {
    return switch (this) {
      case FILLED, CANCELED, REJECTED, EXPIRED, SUPERSEDED, ANOMALY -> true;
      default -> false;
    };
  }

  public boolean isInitial() {
    return switch (this) {
      case PENDING -> true;
      default -> false;
    };
  }
}
