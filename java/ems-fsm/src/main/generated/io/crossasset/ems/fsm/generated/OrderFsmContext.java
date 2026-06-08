// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/order.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import org.jspecify.annotations.Nullable;

/** Mutable context carried by each {@link OrderFsmRunner} instance. */
public final class OrderFsmContext {

  private String orderId;
  private String clOrdId;
  private @Nullable String origClOrdId;
  private String instrumentId;
  private int side;
  private long orderQty;
  private @Nullable Long price;
  private long cumQty;
  private long leavesQty;
  private String account;
  private int tif;
  private long traceId;
  private String initialOrderId;
  private @Nullable String preCancelStatus;
  private @Nullable String preReplaceStatus;

  public OrderFsmContext(
      String orderId,
      String clOrdId,
      @Nullable String origClOrdId,
      String instrumentId,
      int side,
      long orderQty,
      @Nullable Long price,
      long cumQty,
      long leavesQty,
      String account,
      int tif,
      long traceId,
      String initialOrderId,
      @Nullable String preCancelStatus,
      @Nullable String preReplaceStatus
  ) {
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.origClOrdId = origClOrdId;
    this.instrumentId = instrumentId;
    this.side = side;
    this.orderQty = orderQty;
    this.price = price;
    this.cumQty = cumQty;
    this.leavesQty = leavesQty;
    this.account = account;
    this.tif = tif;
    this.traceId = traceId;
    this.initialOrderId = initialOrderId;
    this.preCancelStatus = preCancelStatus;
    this.preReplaceStatus = preReplaceStatus;
  }

  public String orderId() { return orderId; }
  public String clOrdId() { return clOrdId; }
  public @Nullable String origClOrdId() { return origClOrdId; }
  public String instrumentId() { return instrumentId; }
  public int side() { return side; }
  public long orderQty() { return orderQty; }
  public @Nullable Long price() { return price; }
  public long cumQty() { return cumQty; }
  public long leavesQty() { return leavesQty; }
  public String account() { return account; }
  public int tif() { return tif; }
  public long traceId() { return traceId; }
  public String initialOrderId() { return initialOrderId; }
  public @Nullable String preCancelStatus() { return preCancelStatus; }
  public @Nullable String preReplaceStatus() { return preReplaceStatus; }

  /** Return a copy with the given field updated. */
  public OrderFsmContext with(
      String orderId, String clOrdId, @Nullable String origClOrdId, String instrumentId, int side, long orderQty, @Nullable Long price, long cumQty, long leavesQty, String account, int tif, long traceId, String initialOrderId, @Nullable String preCancelStatus, @Nullable String preReplaceStatus
  ) {
    return new OrderFsmContext(orderId, clOrdId, origClOrdId, instrumentId, side, orderQty, price, cumQty, leavesQty, account, tif, traceId, initialOrderId, preCancelStatus, preReplaceStatus);
  }
}
