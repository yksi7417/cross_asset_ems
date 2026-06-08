// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/order.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM states for {@link OrderFsmRunner}. */
public enum OrderFsmState {
  /** initial, OrdStatus=A, ExecType=A. */
  PENDING_NEW,
  /** OrdStatus=0, ExecType=0. */
  NEW,
  /** OrdStatus=E, ExecType=E. */
  PENDING_REPLACE,
  /** OrdStatus=5, ExecType=5. */
  REPLACED,
  /** OrdStatus=6, ExecType=6. */
  PENDING_CANCEL,
  /** OrdStatus=1, ExecType=F. */
  PARTIALLY_FILLED,
  /** OrdStatus=2, ExecType=F. */
  FILLED,
  /** terminal, OrdStatus=4, ExecType=4. */
  CANCELED,
  /** terminal, OrdStatus=8, ExecType=8. */
  REJECTED,
  /** terminal, OrdStatus=C, ExecType=C. */
  EXPIRED,
  /** terminal, OrdStatus=3, ExecType=3. */
  DONE_FOR_DAY,
  /** terminal, ExecType=G. */
  TRADE_CORRECTED,
  /** terminal, ExecType=H. */
  TRADE_CANCELED;

  public boolean isTerminal() {
    return switch (this) {
      case CANCELED, REJECTED, EXPIRED, DONE_FOR_DAY, TRADE_CORRECTED, TRADE_CANCELED -> true;
      default -> false;
    };
  }

  public boolean isInitial() {
    return switch (this) {
      case PENDING_NEW -> true;
      default -> false;
    };
  }
}
