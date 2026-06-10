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
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe in-memory implementation of {@link NettingEngine}.
 *
 * <p>The freeze registry is engine-local (like {@link InMemoryAggregationManager}): a child in an
 * active group cannot join another net here; hard SOM-level freeze arrives with the event-sourced
 * projection.
 */
public final class InMemoryNettingEngine implements NettingEngine {

  private final StagedOrderManager som;
  private final ValidatorPipeline validatorPipeline;
  private final ConcurrentHashMap<String, GroupRec> groups = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> childToGroup = new ConcurrentHashMap<>();
  private final AtomicLong groupIdSeq = new AtomicLong(1);

  public InMemoryNettingEngine(StagedOrderManager som, ValidatorPipeline validatorPipeline) {
    this.som = Objects.requireNonNull(som, "som");
    this.validatorPipeline = Objects.requireNonNull(validatorPipeline, "validatorPipeline");
  }

  @Override
  public NetResult net(NettingRequest request) {
    ValidationResult vr =
        validatorPipeline.validate(
            new ValidationRequest(request.requestId(), request.sessionId(), null, null));
    if (vr instanceof ValidationResult.Reject reject) {
      return new NetResult.Rejected(request.requestId(), reject.code(), reject.message());
    }

    // Phase 1 — resolve and bucket; no side effects yet.
    List<String> passthrough = new ArrayList<>();
    Map<String, List<ChildRec>> buckets = new LinkedHashMap<>();
    Map<String, NettingCandidate> bucketSample = new LinkedHashMap<>();
    java.util.Set<String> seen = new java.util.HashSet<>();

    for (NettingCandidate candidate : request.candidates()) {
      if (!seen.add(candidate.orderId())) {
        return new NetResult.Rejected(
            request.requestId(),
            "EMS-ORD-3003",
            "Duplicate candidate " + candidate.orderId() + " in request.");
      }
      Optional<StagedOrder> orderOpt = som.findOrder(candidate.orderId());
      if (orderOpt.isEmpty()) {
        return new NetResult.Rejected(
            request.requestId(), "EMS-ORD-4001", "Order " + candidate.orderId() + " not found.");
      }
      StagedOrder order = orderOpt.get();
      if (childToGroup.containsKey(order.orderId())) {
        return new NetResult.Rejected(
            request.requestId(),
            "EMS-ORD-3003",
            "Order " + order.orderId() + " is already in an active netting group.");
      }
      if (!order.isReady()) {
        return new NetResult.Rejected(
            request.requestId(),
            "EMS-ORD-3003",
            "Order " + order.orderId() + " is not READY for netting.");
      }
      if (order.sessionId() != request.sessionId()) {
        return new NetResult.Rejected(
            request.requestId(),
            "EMS-PRM-1601",
            "Cross-desk netting not enabled (order "
                + order.orderId()
                + " belongs to another session).");
      }
      if (candidate.doNotNet()) {
        passthrough.add(order.orderId());
        continue;
      }
      String key =
          String.join(
              "|",
              order.fsmContext().instrumentId(),
              candidate.ccyPair(),
              candidate.valueDate(),
              candidate.accountGroup(),
              candidate.pac() == null ? "" : candidate.pac());
      buckets
          .computeIfAbsent(key, k -> new ArrayList<>())
          .add(
              new ChildRec(
                  order.orderId(), order.fsmContext().side(), order.fsmContext().orderQty()));
      bucketSample.putIfAbsent(key, candidate);
    }

    // Phase 2 — policy checks across all buckets before any side effects.
    List<Map.Entry<String, List<ChildRec>>> collapsible = new ArrayList<>();
    for (Map.Entry<String, List<ChildRec>> bucket : buckets.entrySet()) {
      long buyQty = sideQty(bucket.getValue(), 1);
      long sellQty = sideQty(bucket.getValue(), 2);
      if (buyQty == 0 || sellQty == 0) {
        bucket.getValue().forEach(c -> passthrough.add(c.orderId));
        continue;
      }
      if (buyQty == sellQty && !request.netToZeroAllowed()) {
        return new NetResult.Rejected(
            request.requestId(),
            "EMS-ORD-2203",
            "Bucket " + bucket.getKey() + " nets to zero; policy disallows net-to-zero.");
      }
      collapsible.add(bucket);
    }

    // Phase 3 — collapse; unwind everything formed so far if a parent fails post-net validation.
    List<GroupRec> formed = new ArrayList<>();
    for (Map.Entry<String, List<ChildRec>> bucket : collapsible) {
      NettingCandidate sample = bucketSample.get(bucket.getKey());
      StagedOrder first = som.findOrder(bucket.getValue().get(0).orderId).orElseThrow();
      long buyQty = sideQty(bucket.getValue(), 1);
      long sellQty = sideQty(bucket.getValue(), 2);
      long residual = Math.abs(buyQty - sellQty);
      int residualSide = buyQty > sellQty ? 1 : (sellQty > buyQty ? 2 : 0);

      String groupId = "EMS-NET-" + groupIdSeq.getAndIncrement();
      String parentId = null;
      if (residual > 0) {
        StageResult sr =
            som.stage(
                new OrderRequest(
                    groupId + "-P",
                    request.sessionId(),
                    "NET-" + groupId,
                    first.fsmContext().instrumentId(),
                    residualSide,
                    residual,
                    null,
                    request.account(),
                    first.fsmContext().tif()));
        if (sr instanceof StageResult.Rejected rejected) {
          unwind(formed, request.sessionId());
          return new NetResult.Rejected(
              request.requestId(),
              "EMS-ORD-2201",
              "Post-net validation failed for bucket "
                  + bucket.getKey()
                  + ": "
                  + rejected.rejectCode()
                  + " "
                  + rejected.message());
        }
        parentId = ((StageResult.Accepted) sr).order().orderId();
        MarkReadyResult mr = som.markReady(parentId, request.sessionId());
        if (mr instanceof MarkReadyResult.Rejected rejected) {
          unwind(formed, request.sessionId());
          return new NetResult.Rejected(
              request.requestId(),
              "EMS-ORD-2201",
              "Post-net validation failed: " + rejected.rejectCode() + " " + rejected.message());
        }
      }

      GroupRec rec =
          new GroupRec(
              groupId,
              parentId,
              first.fsmContext().instrumentId(),
              sample,
              bucket.getValue(),
              buyQty,
              sellQty,
              residual,
              residualSide);
      groups.put(groupId, rec);
      bucket.getValue().forEach(c -> childToGroup.put(c.orderId, groupId));
      formed.add(rec);
    }

    List<NetGroup> views = new ArrayList<>(formed.size());
    for (GroupRec rec : formed) {
      synchronized (rec) {
        views.add(view(rec));
      }
    }
    return new NetResult.Netted(views, passthrough);
  }

  @Override
  public NettingEventResult allocateNetFill(String groupId, long fillQty, long fillPx) {
    GroupRec rec = groups.get(groupId);
    if (rec == null) {
      return notFound(groupId);
    }
    if (fillQty <= 0) {
      return new NettingEventResult.Rejected(groupId, "EMS-ORD-2001", "Fill qty must be > 0.");
    }
    synchronized (rec) {
      if (rec.residualQty == 0) {
        return new NettingEventResult.Rejected(
            groupId, "EMS-ORD-3003", "Group netted to zero; use bookInternalCross.");
      }
      long remaining = rec.residualQty - rec.parentFilled;
      if (fillQty > remaining) {
        return new NettingEventResult.Rejected(
            groupId,
            "EMS-ORD-3003",
            "Fill qty " + fillQty + " exceeds residual remainder " + remaining + ".");
      }
      rec.parentFilled += fillQty;
      boolean finalFill = rec.parentFilled == rec.residualQty;
      List<ChildAllocation> allocations = new ArrayList<>();
      for (ChildRec child : rec.children) {
        long cumTarget = finalFill ? child.qty : child.qty * rec.parentFilled / rec.residualQty;
        long delta = cumTarget - child.allocated;
        if (delta <= 0) {
          continue;
        }
        child.allocated = cumTarget;
        allocations.add(new ChildAllocation(child.orderId, delta, fillPx));
        bookChildFill(rec, child, delta, fillPx);
      }
      return new NettingEventResult.Applied(view(rec), allocations);
    }
  }

  @Override
  public NettingEventResult bookInternalCross(String groupId, long crossPx) {
    GroupRec rec = groups.get(groupId);
    if (rec == null) {
      return notFound(groupId);
    }
    synchronized (rec) {
      if (rec.residualQty != 0) {
        return new NettingEventResult.Rejected(
            groupId,
            "EMS-ORD-3003",
            "Group has a market-facing residual; fills book via allocateNetFill.");
      }
      if (rec.crossBooked) {
        return new NettingEventResult.Rejected(
            groupId, "EMS-ORD-3003", "Internal cross already booked.");
      }
      rec.crossBooked = true;
      List<ChildAllocation> allocations = new ArrayList<>();
      for (ChildRec child : rec.children) {
        long delta = child.qty - child.allocated;
        if (delta <= 0) {
          continue;
        }
        child.allocated = child.qty;
        allocations.add(new ChildAllocation(child.orderId, delta, crossPx));
        bookChildFill(rec, child, delta, crossPx);
      }
      return new NettingEventResult.Applied(view(rec), allocations);
    }
  }

  @Override
  public NettingEventResult unnet(String groupId, long sessionId) {
    GroupRec rec = groups.get(groupId);
    if (rec == null) {
      return notFound(groupId);
    }
    ValidationResult vr =
        validatorPipeline.validate(new ValidationRequest(groupId, sessionId, null, null));
    if (vr instanceof ValidationResult.Reject reject) {
      return new NettingEventResult.Rejected(groupId, reject.code(), reject.message());
    }
    synchronized (rec) {
      if (rec.parentFilled > 0 || rec.crossBooked) {
        return new NettingEventResult.Rejected(
            groupId, "EMS-ORD-2210", "Group has bookings; un-netting is no longer possible.");
      }
      if (rec.parentOrderId != null) {
        StagedOrder parent = som.findOrder(rec.parentOrderId).orElse(null);
        if (parent != null && parent.subState() == OrderSubState.ROUTING) {
          return new NettingEventResult.Rejected(
              groupId, "EMS-ORD-2210", "Cannot un-net: parent is routed.");
        }
        CancelResult cr = som.cancel(rec.parentOrderId, sessionId);
        if (cr instanceof CancelResult.Rejected rejected) {
          return new NettingEventResult.Rejected(
              groupId, rejected.rejectCode(), rejected.message());
        }
      }
      NetGroup snapshot = view(rec);
      rec.children.forEach(c -> childToGroup.remove(c.orderId));
      groups.remove(groupId);
      return new NettingEventResult.Applied(snapshot, List.of());
    }
  }

  @Override
  public Optional<NetGroup> findGroup(String groupId) {
    GroupRec rec = groups.get(groupId);
    if (rec == null) {
      return Optional.empty();
    }
    synchronized (rec) {
      return Optional.of(view(rec));
    }
  }

  // --- internals ---

  private void bookChildFill(GroupRec rec, ChildRec child, long qty, long px) {
    boolean full = child.allocated == child.qty;
    rec.allocSeq++;
    String execId = rec.groupId + "-X" + rec.allocSeq;
    Object payload =
        full
            ? new OrderFsmPayloads.FullFillPayload(qty, px, execId)
            : new OrderFsmPayloads.PartialFillPayload(qty, px, execId);
    som.applyOrderFsmEvent(
        child.orderId, full ? OrderFsmEvent.FullFill : OrderFsmEvent.PartialFill, payload);
  }

  private void unwind(List<GroupRec> formed, long sessionId) {
    for (GroupRec rec : formed) {
      if (rec.parentOrderId != null) {
        som.cancel(rec.parentOrderId, sessionId);
      }
      rec.children.forEach(c -> childToGroup.remove(c.orderId));
      groups.remove(rec.groupId);
    }
  }

  private static long sideQty(List<ChildRec> children, int side) {
    long sum = 0;
    for (ChildRec child : children) {
      if (child.side == side) {
        sum += child.qty;
      }
    }
    return sum;
  }

  private NetGroup view(GroupRec rec) {
    List<String> buyIds = new ArrayList<>();
    List<String> sellIds = new ArrayList<>();
    Map<String, Long> allocations = new LinkedHashMap<>();
    for (ChildRec child : rec.children) {
      (child.side == 1 ? buyIds : sellIds).add(child.orderId);
      allocations.put(child.orderId, child.allocated);
    }
    return new NetGroup(
        rec.groupId,
        rec.parentOrderId,
        rec.figi,
        rec.ccyPair,
        rec.valueDate,
        rec.accountGroup,
        rec.pac,
        buyIds,
        sellIds,
        rec.buyQty,
        rec.sellQty,
        rec.residualQty,
        rec.residualSide,
        Math.min(rec.buyQty, rec.sellQty),
        rec.parentFilled,
        allocations);
  }

  private static NettingEventResult.Rejected notFound(String groupId) {
    return new NettingEventResult.Rejected(
        groupId, "EMS-ORD-4001", "Netting group " + groupId + " not found.");
  }

  /** Mutable group state; mutations run inside {@code synchronized (rec)}. */
  private static final class GroupRec {
    final String groupId;
    final @Nullable String parentOrderId;
    final String figi;
    final String ccyPair;
    final String valueDate;
    final String accountGroup;
    final @Nullable String pac;
    final List<ChildRec> children;
    final long buyQty;
    final long sellQty;
    final long residualQty;
    final int residualSide;
    long parentFilled;
    long allocSeq;
    boolean crossBooked;

    GroupRec(
        String groupId,
        @Nullable String parentOrderId,
        String figi,
        NettingCandidate sample,
        List<ChildRec> children,
        long buyQty,
        long sellQty,
        long residualQty,
        int residualSide) {
      this.groupId = groupId;
      this.parentOrderId = parentOrderId;
      this.figi = figi;
      this.ccyPair = sample.ccyPair();
      this.valueDate = sample.valueDate();
      this.accountGroup = sample.accountGroup();
      this.pac = sample.pac();
      this.children = children;
      this.buyQty = buyQty;
      this.sellQty = sellQty;
      this.residualQty = residualQty;
      this.residualSide = residualSide;
    }
  }

  /** Mutable per-child booking state; guarded by the owning {@link GroupRec}'s monitor. */
  private static final class ChildRec {
    final String orderId;
    final int side;
    final long qty;
    long allocated;

    ChildRec(String orderId, int side, long qty) {
      this.orderId = orderId;
      this.side = side;
      this.qty = qty;
    }
  }
}
