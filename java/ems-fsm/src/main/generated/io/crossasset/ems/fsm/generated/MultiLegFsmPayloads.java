// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multileg.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import org.jspecify.annotations.Nullable;

/** Payload record types for events that carry additional data. */
public final class MultiLegFsmPayloads {

  private MultiLegFsmPayloads() {}

  /** Payload for LegFilled. */
  public record LegFilledPayload(
    String legId,
    long lastQty,
    long lastPx
  ) {}

  /** Payload for LegPartiallyFilled. */
  public record LegPartiallyFilledPayload(
    String legId,
    long lastQty,
    long lastPx
  ) {}

  /** Payload for LegRejected. */
  public record LegRejectedPayload(
    String legId
  ) {}

  /** Payload for LegCanceled. */
  public record LegCanceledPayload(
    String legId
  ) {}

}
