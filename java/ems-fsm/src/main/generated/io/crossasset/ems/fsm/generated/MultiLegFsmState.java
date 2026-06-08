// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multileg.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM states for {@link MultiLegFsmRunner}. */
public enum MultiLegFsmState {
  /** initial. */
  STAGED,
  READY,
  LEGS_WORKING,
  /** terminal, OrdStatus=2, ExecType=F. */
  FILLED,
  /** terminal, OrdStatus=1, ExecType=F. */
  PARTIALLY_FILLED,
  /** terminal, OrdStatus=4, ExecType=4. */
  CANCELED,
  /** terminal, OrdStatus=8, ExecType=8. */
  REJECTED;

  public boolean isTerminal() {
    return switch (this) {
      case FILLED, PARTIALLY_FILLED, CANCELED, REJECTED -> true;
      default -> false;
    };
  }

  public boolean isInitial() {
    return switch (this) {
      case STAGED -> true;
      default -> false;
    };
  }
}
