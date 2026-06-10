// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multileg.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM events for {@link MultiLegFsmRunner}. */
public enum MultiLegFsmEvent {
  /** All legs pass the EMS validator; package promoted to READY. */
  LegsValidated,
  /** One or more legs fail validation (EMS-ORD-4401 / EMS-ORD-4402 / EMS-ORD-4403 / EMS-ORD-4404). */
  LegsValidationFailed,
  /** First leg has been sent to routing; execution is now in progress. */
  FirstLegDispatched,
  /** One leg has been fully filled by its venue. */
  LegFilled,
  /** One leg received a partial fill; parent aggregate remains LEGS_WORKING. */
  LegPartiallyFilled,
  /** One leg was rejected by its venue. */
  LegRejected,
  /** One leg was confirmed canceled by its venue. */
  LegCanceled,
  /** Operator or automation requested cancellation of the package. */
  CancelRequested;
}
