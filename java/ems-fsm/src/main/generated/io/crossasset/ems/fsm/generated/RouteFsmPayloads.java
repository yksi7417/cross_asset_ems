// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/route.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import org.jspecify.annotations.Nullable;

/** Payload record types for events that carry additional data. */
public final class RouteFsmPayloads {

  private RouteFsmPayloads() {}

  /** Payload for RouteReplaceRequested. */
  public record RouteReplaceRequestedPayload(
    String newClOrdId,
    long newRouteQty,
    @Nullable Long newPrice
  ) {}

  /** Payload for RouteReplaced. */
  public record RouteReplacedPayload(
    String newClOrdId
  ) {}

  /** Payload for RouteReplaceRejected. */
  public record RouteReplaceRejectedPayload(
    int cxlRejReason
  ) {}

  /** Payload for RouteCancelRejected. */
  public record RouteCancelRejectedPayload(
    int cxlRejReason
  ) {}

  /** Payload for RoutePartiallyFilled. */
  public record RoutePartiallyFilledPayload(
    long lastQty,
    long lastPx,
    String execId
  ) {}

  /** Payload for RouteFilled. */
  public record RouteFilledPayload(
    long lastQty,
    long lastPx,
    String execId
  ) {}

}
