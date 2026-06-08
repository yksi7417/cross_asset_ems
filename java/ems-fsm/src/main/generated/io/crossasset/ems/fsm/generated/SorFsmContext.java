// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sor.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import org.jspecify.annotations.Nullable;

/** Mutable context carried by each {@link SorFsmRunner} instance. */
public final class SorFsmContext {

  private String routeId;
  private String orderId;
  private String clOrdId;
  private @Nullable String origClOrdId;
  private String venueMic;
  private String instrumentId;
  private int side;
  private long routeQty;
  private @Nullable Long price;
  private long cumQty;
  private long leavesQty;
  private long traceId;
  private String initialOrderId;
  private @Nullable String preCancelStatus;
  private String sorStrategyId;

  public SorFsmContext(
      String routeId,
      String orderId,
      String clOrdId,
      @Nullable String origClOrdId,
      String venueMic,
      String instrumentId,
      int side,
      long routeQty,
      @Nullable Long price,
      long cumQty,
      long leavesQty,
      long traceId,
      String initialOrderId,
      @Nullable String preCancelStatus,
      String sorStrategyId
  ) {
    this.routeId = routeId;
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.origClOrdId = origClOrdId;
    this.venueMic = venueMic;
    this.instrumentId = instrumentId;
    this.side = side;
    this.routeQty = routeQty;
    this.price = price;
    this.cumQty = cumQty;
    this.leavesQty = leavesQty;
    this.traceId = traceId;
    this.initialOrderId = initialOrderId;
    this.preCancelStatus = preCancelStatus;
    this.sorStrategyId = sorStrategyId;
  }

  public String routeId() { return routeId; }
  public String orderId() { return orderId; }
  public String clOrdId() { return clOrdId; }
  public @Nullable String origClOrdId() { return origClOrdId; }
  public String venueMic() { return venueMic; }
  public String instrumentId() { return instrumentId; }
  public int side() { return side; }
  public long routeQty() { return routeQty; }
  public @Nullable Long price() { return price; }
  public long cumQty() { return cumQty; }
  public long leavesQty() { return leavesQty; }
  public long traceId() { return traceId; }
  public String initialOrderId() { return initialOrderId; }
  public @Nullable String preCancelStatus() { return preCancelStatus; }
  public String sorStrategyId() { return sorStrategyId; }

  /** Return a copy with the given field updated. */
  public SorFsmContext with(
      String routeId, String orderId, String clOrdId, @Nullable String origClOrdId, String venueMic, String instrumentId, int side, long routeQty, @Nullable Long price, long cumQty, long leavesQty, long traceId, String initialOrderId, @Nullable String preCancelStatus, String sorStrategyId
  ) {
    return new SorFsmContext(routeId, orderId, clOrdId, origClOrdId, venueMic, instrumentId, side, routeQty, price, cumQty, leavesQty, traceId, initialOrderId, preCancelStatus, sorStrategyId);
  }
}
