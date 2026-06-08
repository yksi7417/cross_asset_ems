// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/order.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import org.jspecify.annotations.Nullable;

/** Payload record types for events that carry additional data. */
public final class OrderFsmPayloads {

  private OrderFsmPayloads() {}

  /** Payload for ReplaceRequested. */
  public record ReplaceRequestedPayload(
    String newClOrdId,
    long newOrderQty,
    @Nullable Long newPrice
  ) {}

  /** Payload for ReplaceAccepted. */
  public record ReplaceAcceptedPayload(
    String newClOrdId
  ) {}

  /** Payload for ReplaceRejected. */
  public record ReplaceRejectedPayload(
    int cxlRejReason
  ) {}

  /** Payload for CancelRejected. */
  public record CancelRejectedPayload(
    int cxlRejReason
  ) {}

  /** Payload for PartialFill. */
  public record PartialFillPayload(
    long lastQty,
    long lastPx,
    String execId
  ) {}

  /** Payload for FullFill. */
  public record FullFillPayload(
    long lastQty,
    long lastPx,
    String execId
  ) {}

  /** Payload for TradeCorrect. */
  public record TradeCorrectPayload(
    long correctedQty,
    long correctedPx,
    String execId
  ) {}

  /** Payload for TradeCancelBust. */
  public record TradeCancelBustPayload(
    String bustedExecId
  ) {}

}
