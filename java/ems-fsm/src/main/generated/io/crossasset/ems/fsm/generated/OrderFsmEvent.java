// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/order.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM events for {@link OrderFsmRunner}. */
public enum OrderFsmEvent {
  /** Internal validator accepted the order; move to NEW. */
  ValidationPassed,
  /** Internal validator rejected the order. */
  ValidationFailed,
  /** OrderCancelReplaceRequest received from client. */
  ReplaceRequested,
  /** Venue confirmed replace (35=8 ExecType=5). */
  ReplaceAccepted,
  /** Venue rejected replace via 35=9 OrderCancelReject; order stays in prior state. */
  ReplaceRejected,
  /** OrderCancelRequest received from client or automation. */
  CancelRequested,
  /** Venue confirmed cancel (35=8 ExecType=4). */
  CancelAccepted,
  /** Venue rejected cancel via 35=9; order stays in prior state. */
  CancelRejected,
  /** Partial execution report from venue (OrdStatus=1, ExecType=F). */
  PartialFill,
  /** Final fill — order fully executed (OrdStatus=2, ExecType=F). */
  FullFill,
  /** Post-fill price/qty correction received (ExecType=G). */
  TradeCorrect,
  /** Post-fill bust received (ExecType=H); fill treated as never occurred. */
  TradeCancelBust,
  /** Venue expired the order at TIF boundary (ExecType=C). */
  OrderExpired,
  /** Day order closed at end of day (ExecType=3). */
  DoneForDay;
}
