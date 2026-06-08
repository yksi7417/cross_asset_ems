// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/multileg.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import org.jspecify.annotations.Nullable;

/** Mutable context carried by each {@link MultiLegFsmRunner} instance. */
public final class MultiLegFsmContext {

  private String orderId;
  private String multilegKind;
  private String executionMode;
  private long totalLegs;
  private long legsFilled;
  private long legsRejected;
  private long legsCanceled;
  private @Nullable String packageId;

  public MultiLegFsmContext(
      String orderId,
      String multilegKind,
      String executionMode,
      long totalLegs,
      long legsFilled,
      long legsRejected,
      long legsCanceled,
      @Nullable String packageId
  ) {
    this.orderId = orderId;
    this.multilegKind = multilegKind;
    this.executionMode = executionMode;
    this.totalLegs = totalLegs;
    this.legsFilled = legsFilled;
    this.legsRejected = legsRejected;
    this.legsCanceled = legsCanceled;
    this.packageId = packageId;
  }

  public String orderId() { return orderId; }
  public String multilegKind() { return multilegKind; }
  public String executionMode() { return executionMode; }
  public long totalLegs() { return totalLegs; }
  public long legsFilled() { return legsFilled; }
  public long legsRejected() { return legsRejected; }
  public long legsCanceled() { return legsCanceled; }
  public @Nullable String packageId() { return packageId; }

  /** Return a copy with the given field updated. */
  public MultiLegFsmContext with(
      String orderId, String multilegKind, String executionMode, long totalLegs, long legsFilled, long legsRejected, long legsCanceled, @Nullable String packageId
  ) {
    return new MultiLegFsmContext(orderId, multilegKind, executionMode, totalLegs, legsFilled, legsRejected, legsCanceled, packageId);
  }
}
