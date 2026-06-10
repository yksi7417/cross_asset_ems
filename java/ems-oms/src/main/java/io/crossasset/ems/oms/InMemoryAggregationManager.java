/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmPayloads;
import io.crossasset.ems.validator.ValidationRequest;
import io.crossasset.ems.validator.ValidationResult;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe in-memory implementation of {@link AggregationManager}.
 *
 * <p>The freeze registry is manager-local: a child in an active group cannot join another group
 * here, but a direct SOM cancel is not blocked (caught at allocation time when the child FSM
 * rejects the fill event) — see the interface note on freeze enforcement.
 */
public final class InMemoryAggregationManager implements AggregationManager {

  private final StagedOrderManager som;
  private final ValidatorPipeline validatorPipeline;
  private final ConcurrentHashMap<String, GroupRec> groups = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> childToGroup = new ConcurrentHashMap<>();

  public InMemoryAggregationManager(StagedOrderManager som, ValidatorPipeline validatorPipeline) {
    this.som = Objects.requireNonNull(som, "som");
    this.validatorPipeline = Objects.requireNonNull(validatorPipeline, "validatorPipeline");
  }

  @Override
  public AggregateResult aggregate(AggregationRequest request) {
    ValidationResult vr =
        validatorPipeline.validate(
            new ValidationRequest(request.requestId(), request.sessionId(), null, null));
    if (vr instanceof ValidationResult.Reject reject) {
      return new AggregateResult.Rejected(request.requestId(), reject.code(), reject.message());
    }
    if (request.childOrderIds().size() < 2) {
      return ineligible(request, "aggregation requires at least 2 child orders");
    }
    if (request.rule() != AllocationRule.SEQUENCED && request.rounding() == null) {
      return new AggregateResult.Rejected(
          request.requestId(),
          "EMS-ORD-5002",
          "Allocation rule " + request.rule() + " requires an explicit rounding policy.");
    }

    List<StagedOrder> children = new ArrayList<>(request.childOrderIds().size());
    for (String childId : request.childOrderIds()) {
      Optional<StagedOrder> child = som.findOrder(childId);
      if (child.isEmpty()) {
        return new AggregateResult.Rejected(
            request.requestId(), "EMS-ORD-4001", "Child order " + childId + " not found.");
      }
      children.add(child.get());
    }

    StagedOrder first = children.get(0);
    for (StagedOrder child : children) {
      if (childToGroup.containsKey(child.orderId())) {
        return ineligible(
            request, "child " + child.orderId() + " is already in an active aggregation");
      }
      if (!child.isReady()) {
        return ineligible(request, "child " + child.orderId() + " is not READY");
      }
      if (child.sessionId() != request.sessionId()) {
        return new AggregateResult.Rejected(
            request.requestId(),
            "EMS-PRM-1601",
            "Cross-desk aggregation requires tag #cross-desk-aggregator (child "
                + child.orderId()
                + " belongs to another session).");
      }
      if (!child.fsmContext().instrumentId().equals(first.fsmContext().instrumentId())) {
        return ineligible(request, "children must share the same instrument (exact FIGI)");
      }
      if (child.fsmContext().side() != first.fsmContext().side()) {
        return ineligible(request, "children must share the same side");
      }
      if (child.fsmContext().tif() != first.fsmContext().tif()) {
        return ineligible(request, "children must share a compatible TIF");
      }
    }

    long totalQty = children.stream().mapToLong(c -> c.fsmContext().orderQty()).sum();
    Long parentPrice = conservativeLimit(children);

    StageResult sr =
        som.stage(
            new OrderRequest(
                request.requestId() + "-AGG",
                request.sessionId(),
                request.clOrdId(),
                first.fsmContext().instrumentId(),
                first.fsmContext().side(),
                totalQty,
                parentPrice,
                request.account(),
                first.fsmContext().tif()));
    if (sr instanceof StageResult.Rejected rejected) {
      return new AggregateResult.Rejected(
          request.requestId(), rejected.rejectCode(), rejected.message());
    }
    String parentId = ((StageResult.Accepted) sr).order().orderId();
    MarkReadyResult mr = som.markReady(parentId, request.sessionId());
    if (mr instanceof MarkReadyResult.Rejected rejected) {
      return new AggregateResult.Rejected(
          request.requestId(), rejected.rejectCode(), rejected.message());
    }

    GroupRec rec = new GroupRec(parentId, request, children);
    groups.put(parentId, rec);
    for (String childId : request.childOrderIds()) {
      childToGroup.put(childId, parentId);
    }
    StagedOrder parent = ((MarkReadyResult.Ready) mr).order();
    synchronized (rec) {
      return new AggregateResult.Aggregated(view(rec), parent);
    }
  }

  @Override
  public AggregationEventResult allocateFill(String aggOrderId, long fillQty, long fillPx) {
    GroupRec rec = groups.get(aggOrderId);
    if (rec == null) {
      return notFound(aggOrderId);
    }
    if (fillQty <= 0) {
      return new AggregationEventResult.Rejected(
          aggOrderId, "EMS-ORD-2001", "Fill qty must be > 0.");
    }
    synchronized (rec) {
      long remaining = rec.totalQty() - rec.totalAllocated;
      if (fillQty > remaining) {
        return new AggregationEventResult.Rejected(
            aggOrderId,
            "EMS-ORD-3003",
            "Fill qty " + fillQty + " exceeds group remainder " + remaining + ".");
      }
      long[] shares = computeShares(rec, fillQty);

      rec.totalAllocated += fillQty;
      rec.totalPxQty += fillPx * fillQty;
      long pxForChildren = rec.rule == AllocationRule.AVG_PRICE ? rec.avgPx() : fillPx;

      List<ChildAllocation> allocations = new ArrayList<>();
      for (int i = 0; i < rec.children.size(); i++) {
        if (shares[i] == 0) {
          continue;
        }
        ChildRec child = rec.children.get(i);
        child.allocated += shares[i];
        allocations.add(new ChildAllocation(child.orderId, shares[i], pxForChildren));
        boolean full = child.allocated == child.qty;
        rec.allocSeq++;
        String execId = rec.aggOrderId + "-A" + rec.allocSeq;
        Object payload =
            full
                ? new OrderFsmPayloads.FullFillPayload(shares[i], pxForChildren, execId)
                : new OrderFsmPayloads.PartialFillPayload(shares[i], pxForChildren, execId);
        som.applyOrderFsmEvent(
            child.orderId, full ? OrderFsmEvent.FullFill : OrderFsmEvent.PartialFill, payload);
      }
      return new AggregationEventResult.Applied(view(rec), allocations);
    }
  }

  @Override
  public AggregationEventResult unaggregate(String aggOrderId, long sessionId) {
    GroupRec rec = groups.get(aggOrderId);
    if (rec == null) {
      return notFound(aggOrderId);
    }
    ValidationResult vr =
        validatorPipeline.validate(new ValidationRequest(aggOrderId, sessionId, null, null));
    if (vr instanceof ValidationResult.Reject reject) {
      return new AggregationEventResult.Rejected(aggOrderId, reject.code(), reject.message());
    }
    synchronized (rec) {
      if (rec.totalAllocated > 0) {
        return new AggregationEventResult.Rejected(
            aggOrderId,
            "EMS-ORD-3003",
            "Cannot unaggregate: " + rec.totalAllocated + " already allocated.");
      }
      CancelResult cr = som.cancel(aggOrderId, sessionId);
      if (cr instanceof CancelResult.Rejected rejected) {
        return new AggregationEventResult.Rejected(
            aggOrderId, rejected.rejectCode(), rejected.message());
      }
      AggregationGroup snapshot = view(rec);
      for (ChildRec child : rec.children) {
        childToGroup.remove(child.orderId);
      }
      groups.remove(aggOrderId);
      return new AggregationEventResult.Applied(snapshot, List.of());
    }
  }

  @Override
  public Optional<AggregationGroup> findGroup(String aggOrderId) {
    GroupRec rec = groups.get(aggOrderId);
    if (rec == null) {
      return Optional.empty();
    }
    synchronized (rec) {
      return Optional.of(view(rec));
    }
  }

  // --- internals ---

  /**
   * Splits one fill across children. Conserves exactly: Σ shares == fillQty (guaranteed by the
   * pre-check fillQty <= group remainder plus the clamp-and-redistribute pass).
   */
  private long[] computeShares(GroupRec rec, long fillQty) {
    int n = rec.children.size();
    long[] capacity = new long[n];
    for (int i = 0; i < n; i++) {
      capacity[i] = rec.children.get(i).qty - rec.children.get(i).allocated;
    }
    long[] target = new long[n];

    if (rec.rule == AllocationRule.SEQUENCED) {
      long left = fillQty;
      for (int i = 0; i < n && left > 0; i++) {
        target[i] = Math.min(left, capacity[i]);
        left -= target[i];
      }
      return target;
    }

    long totalWeight = rec.totalQty();
    long[] remainder = new long[n];
    long floorSum = 0;
    for (int i = 0; i < n; i++) {
      long raw = fillQty * rec.children.get(i).qty;
      target[i] = raw / totalWeight;
      remainder[i] = raw % totalWeight;
      floorSum += target[i];
    }
    long residual = fillQty - floorSum;

    RoundingPolicy rounding = Objects.requireNonNull(rec.rounding, "rounding");
    switch (rounding) {
      case ROUND_DOWN -> target[n - 1] += residual;
      case DISTRIBUTE_RESIDUAL -> {
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
          order[i] = i;
        }
        java.util.Arrays.sort(
            order,
            (a, b) ->
                remainder[a] == remainder[b] ? a - b : Long.compare(remainder[b], remainder[a]));
        for (int k = 0; k < residual; k++) {
          target[order[k % n]] += 1;
        }
      }
      case ROUND_HALF_UP -> {
        long roundedSum = 0;
        for (int i = 0; i < n; i++) {
          long raw = fillQty * rec.children.get(i).qty;
          target[i] = (raw + totalWeight / 2) / totalWeight;
          roundedSum += target[i];
        }
        target[n - 1] += fillQty - roundedSum;
        if (target[n - 1] < 0) {
          target[n - 1] = 0;
        }
      }
    }

    // Clamp to capacity and redistribute overflow to children that can still take size.
    long assigned = 0;
    for (int i = 0; i < n; i++) {
      target[i] = Math.min(target[i], capacity[i]);
      assigned += target[i];
    }
    long leftover = fillQty - assigned;
    for (int i = 0; i < n && leftover > 0; i++) {
      long room = capacity[i] - target[i];
      long add = Math.min(room, leftover);
      target[i] += add;
      leftover -= add;
    }
    return target;
  }

  /** BUY blocks take the lowest child limit, SELL the highest; null if any child is unpriced. */
  private static @Nullable Long conservativeLimit(List<StagedOrder> children) {
    Long best = null;
    for (StagedOrder child : children) {
      Long px = child.fsmContext().price();
      if (px == null) {
        return null;
      }
      if (best == null) {
        best = px;
      } else if (child.fsmContext().side() == 1) {
        best = Math.min(best, px);
      } else {
        best = Math.max(best, px);
      }
    }
    return best;
  }

  private AggregationGroup view(GroupRec rec) {
    Map<String, Long> allocations = new LinkedHashMap<>();
    List<String> childIds = new ArrayList<>(rec.children.size());
    for (ChildRec child : rec.children) {
      childIds.add(child.orderId);
      allocations.put(child.orderId, child.allocated);
    }
    return new AggregationGroup(
        rec.aggOrderId,
        childIds,
        rec.rule,
        rec.rounding,
        allocations,
        rec.totalAllocated,
        rec.avgPx());
  }

  private static AggregationEventResult.Rejected notFound(String aggOrderId) {
    return new AggregationEventResult.Rejected(
        aggOrderId, "EMS-ORD-4001", "Aggregation group " + aggOrderId + " not found.");
  }

  private AggregateResult.Rejected ineligible(AggregationRequest request, String reason) {
    return new AggregateResult.Rejected(
        request.requestId(), "EMS-ORD-5001", "Ineligible for aggregation: " + reason + ".");
  }

  /** Mutable group state; mutations run inside {@code synchronized (rec)}. */
  private static final class GroupRec {
    final String aggOrderId;
    final AllocationRule rule;
    final @Nullable RoundingPolicy rounding;
    final List<ChildRec> children;
    long totalAllocated;
    long totalPxQty;
    long allocSeq;

    GroupRec(String aggOrderId, AggregationRequest request, List<StagedOrder> children) {
      this.aggOrderId = aggOrderId;
      this.rule = request.rule();
      this.rounding = request.rounding();
      this.children = new ArrayList<>(children.size());
      for (StagedOrder child : children) {
        this.children.add(new ChildRec(child.orderId(), child.fsmContext().orderQty()));
      }
    }

    long totalQty() {
      long sum = 0;
      for (ChildRec child : children) {
        sum += child.qty;
      }
      return sum;
    }

    long avgPx() {
      return totalAllocated == 0 ? 0 : totalPxQty / totalAllocated;
    }
  }

  /** Mutable per-child allocation state; guarded by the owning {@link GroupRec}'s monitor. */
  private static final class ChildRec {
    final String orderId;
    final long qty;
    long allocated;

    ChildRec(String orderId, long qty) {
      this.orderId = orderId;
      this.qty = qty;
    }
  }
}
