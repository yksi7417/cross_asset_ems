/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.MultiLegFsmContext;
import io.crossasset.ems.fsm.generated.MultiLegFsmEffect;
import io.crossasset.ems.fsm.generated.MultiLegFsmEvent;
import io.crossasset.ems.fsm.generated.MultiLegFsmPayloads;
import io.crossasset.ems.fsm.generated.MultiLegFsmRunner;
import io.crossasset.ems.fsm.generated.MultiLegFsmState;
import io.crossasset.ems.fsm.generated.TransitionResult;
import io.crossasset.ems.validator.ValidationRequest;
import io.crossasset.ems.validator.ValidationResult;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe in-memory implementation of {@link MultiLegOrderManager}.
 *
 * <p>Design (v1): each dispatched leg materializes as a child {@link StagedOrder} so its route
 * rides the existing Route FSM unchanged — fills propagate to the child order, cancel/replace
 * mechanics come for free, and route-qty checks bind to the leg. A venue-side package route (one
 * route for N legs on package-capable venues) is a venue-adapter concern (task 11.x); ALL_OR_NONE
 * atomicity is enforced here at the parent FSM via cascade cancel.
 *
 * <p>Orphaned positions from ALL_OR_NONE races (a leg fills before the package voids) are visible
 * as FILLED legs on a REJECTED parent — ops surfacing is task 14.1 (JMX introspection).
 */
public final class InMemoryMultiLegOrderManager implements MultiLegOrderManager {

  private static final Set<String> KNOWN_POLICIES = Set.of("spot_first", "declared_order");
  private static final Set<MultiLegKind> SPOT_FIRST_KINDS =
      Set.of(MultiLegKind.SWAP, MultiLegKind.DELTA_HEDGE, MultiLegKind.CUSTOM);

  private final StagedOrderManager som;
  private final RouteManager routeManager;
  private final ValidatorPipeline validatorPipeline;
  private final ConcurrentHashMap<String, PackageRec> packages = new ConcurrentHashMap<>();
  private final AtomicLong orderIdSeq = new AtomicLong(1);

  public InMemoryMultiLegOrderManager(
      StagedOrderManager som, RouteManager routeManager, ValidatorPipeline validatorPipeline) {
    this.som = Objects.requireNonNull(som, "som");
    this.routeManager = Objects.requireNonNull(routeManager, "routeManager");
    this.validatorPipeline = Objects.requireNonNull(validatorPipeline, "validatorPipeline");
  }

  @Override
  public MultiLegStageResult stage(MultiLegOrderRequest request) {
    String orderId = "EMS-MLO-" + orderIdSeq.getAndIncrement();
    PackageRec rec = new PackageRec(orderId, request);
    packages.put(orderId, rec);

    String[] failure = validatePackage(request);
    synchronized (rec) {
      if (failure != null) {
        applyParent(rec, MultiLegFsmEvent.LegsValidationFailed, null);
        return new MultiLegStageResult.Rejected(
            request.requestId(), orderId, failure[0], failure[1]);
      }
      applyParent(rec, MultiLegFsmEvent.LegsValidated, null);
      return new MultiLegStageResult.Staged(view(rec));
    }
  }

  /** Returns {@code {code, message}} on failure, null when the package validates. */
  private String @Nullable [] validatePackage(MultiLegOrderRequest request) {
    List<LegRequest> legs = request.legs();
    if (legs.size() < 2) {
      return new String[] {"EMS-ORD-4404", "A multi-leg package requires at least 2 legs."};
    }
    for (LegRequest leg : legs) {
      if (leg.qty() <= 0) {
        return new String[] {"EMS-ORD-2001", "Leg qty must be > 0."};
      }
    }
    if (request.executionMode() == ExecutionMode.ALL_OR_NONE) {
      long venues = legs.stream().map(LegRequest::venueMic).distinct().count();
      if (venues > 1) {
        return new String[] {
          "EMS-ORD-4401", "ALL_OR_NONE legs must route to a single venue (heterogeneous legs)."
        };
      }
      if (request.packageId() == null) {
        return new String[] {"EMS-ORD-4402", "package_id required for ALL_OR_NONE."};
      }
    }
    String policy = request.sequencePolicy();
    if (policy != null) {
      if (request.executionMode() != ExecutionMode.SEQUENCED) {
        return new String[] {
          "EMS-ORD-4403", "sequence_policy only valid with SEQUENCED execution mode."
        };
      }
      if (!KNOWN_POLICIES.contains(policy)) {
        return new String[] {"EMS-ORD-4403", "Unknown sequence_policy: " + policy + "."};
      }
      if ("spot_first".equals(policy) && !SPOT_FIRST_KINDS.contains(request.kind())) {
        return new String[] {
          "EMS-ORD-4403", "sequence_policy spot_first invalid for kind " + request.kind() + "."
        };
      }
    }
    for (LegRequest leg : legs) {
      ValidationResult vr =
          validatorPipeline.validate(
              new ValidationRequest(request.requestId(), request.sessionId(), null, leg.figi()));
      if (vr instanceof ValidationResult.Reject reject) {
        return new String[] {reject.code(), reject.message()};
      }
    }
    return null;
  }

  @Override
  public MultiLegEventResult dispatch(String orderId) {
    PackageRec rec = packages.get(orderId);
    if (rec == null) {
      return notFound(orderId);
    }
    synchronized (rec) {
      if (rec.state != MultiLegFsmState.READY) {
        return new MultiLegEventResult.Rejected(
            orderId, "EMS-ORD-3003", "Package " + orderId + " is not READY (" + rec.state + ").");
      }
      List<LegRec> toDispatch =
          rec.executionMode == ExecutionMode.SEQUENCED ? List.of(rec.legs.get(0)) : rec.legs;
      for (LegRec leg : toDispatch) {
        MultiLegEventResult.Rejected failure = dispatchLeg(rec, leg);
        if (failure != null) {
          return failure;
        }
      }
      applyParent(rec, MultiLegFsmEvent.FirstLegDispatched, null);
      return new MultiLegEventResult.Applied(view(rec));
    }
  }

  @Override
  public MultiLegEventResult cancel(String orderId, long sessionId) {
    PackageRec rec = packages.get(orderId);
    if (rec == null) {
      return notFound(orderId);
    }
    ValidationResult vr =
        validatorPipeline.validate(new ValidationRequest(orderId, sessionId, null, null));
    if (vr instanceof ValidationResult.Reject reject) {
      return new MultiLegEventResult.Rejected(orderId, reject.code(), reject.message());
    }
    synchronized (rec) {
      if (rec.state.isTerminal()) {
        return new MultiLegEventResult.Rejected(
            orderId,
            "EMS-ORD-3001",
            "Package " + orderId + " is in terminal state " + rec.state + ".");
      }
      applyParent(rec, MultiLegFsmEvent.CancelRequested, null);
      return new MultiLegEventResult.Applied(view(rec));
    }
  }

  @Override
  public MultiLegEventResult legAcknowledged(String orderId, String legId) {
    return onLegEvent(
        orderId, legId, leg -> routeManager.acknowledgeRoute(leg.routeId), null, rec -> {});
  }

  @Override
  public MultiLegEventResult legPartiallyFilled(
      String orderId, String legId, long lastQty, long lastPx, String execId) {
    return onLegEvent(
        orderId,
        legId,
        leg -> routeManager.partialFill(leg.routeId, lastQty, lastPx, execId),
        rec ->
            applyParent(
                rec,
                MultiLegFsmEvent.LegPartiallyFilled,
                new MultiLegFsmPayloads.LegPartiallyFilledPayload(legId, lastQty, lastPx)),
        rec -> {});
  }

  @Override
  public MultiLegEventResult legFilled(
      String orderId, String legId, long lastQty, long lastPx, String execId) {
    return onLegEvent(
        orderId,
        legId,
        leg -> routeManager.fullFill(leg.routeId, lastQty, lastPx, execId),
        rec ->
            applyParent(
                rec,
                MultiLegFsmEvent.LegFilled,
                new MultiLegFsmPayloads.LegFilledPayload(legId, lastQty, lastPx)),
        this::dispatchNextSequencedLeg);
  }

  @Override
  public MultiLegEventResult legRejected(String orderId, String legId) {
    return onLegEvent(
        orderId,
        legId,
        leg -> routeManager.rejectRoute(leg.routeId),
        rec ->
            applyParent(
                rec,
                MultiLegFsmEvent.LegRejected,
                new MultiLegFsmPayloads.LegRejectedPayload(legId)),
        rec -> {});
  }

  @Override
  public MultiLegEventResult legCanceled(String orderId, String legId) {
    return onLegEvent(
        orderId,
        legId,
        leg -> routeManager.canceledByVenue(leg.routeId),
        rec ->
            applyParent(
                rec,
                MultiLegFsmEvent.LegCanceled,
                new MultiLegFsmPayloads.LegCanceledPayload(legId)),
        rec -> {});
  }

  @Override
  public Optional<MultiLegOrder> findOrder(String orderId) {
    PackageRec rec = packages.get(orderId);
    if (rec == null) {
      return Optional.empty();
    }
    synchronized (rec) {
      return Optional.of(view(rec));
    }
  }

  // --- internals ---

  /**
   * Common leg-event shape: resolve the leg, apply the venue event to its route, drive the parent
   * FSM, then run the post-transition hook (SEQUENCED next-leg dispatch).
   */
  private MultiLegEventResult onLegEvent(
      String orderId,
      String legId,
      java.util.function.Function<LegRec, RouteEventResult> routeCall,
      @Nullable java.util.function.Consumer<PackageRec> parentTransition,
      java.util.function.Consumer<PackageRec> postHook) {
    PackageRec rec = packages.get(orderId);
    if (rec == null) {
      return notFound(orderId);
    }
    synchronized (rec) {
      LegRec leg = rec.findLeg(legId);
      if (leg == null) {
        return new MultiLegEventResult.Rejected(
            orderId, "EMS-ORD-4405", "Leg " + legId + " not found on " + orderId + ".");
      }
      if (leg.routeId == null) {
        return new MultiLegEventResult.Rejected(
            orderId, "EMS-ORD-4406", "Leg " + legId + " has not been dispatched.");
      }
      RouteEventResult rr = routeCall.apply(leg);
      if (rr instanceof RouteEventResult.Rejected rejected) {
        return new MultiLegEventResult.Rejected(orderId, rejected.rejectCode(), rejected.message());
      }
      if (parentTransition != null) {
        parentTransition.accept(rec);
      }
      postHook.accept(rec);
      return new MultiLegEventResult.Applied(view(rec));
    }
  }

  /**
   * Runs one parent FSM transition and dispatches its effects. A no-transition is benign (mirrors
   * {@link StagedOrderManager#applyOrderFsmEvent}): late leg events on a terminal parent leave it
   * unchanged.
   */
  private void applyParent(PackageRec rec, MultiLegFsmEvent event, @Nullable Object payload) {
    TransitionResult<MultiLegFsmState, MultiLegFsmContext, MultiLegFsmEffect> tr =
        MultiLegFsmRunner.transition(rec.state, event, rec.ctx, payload);
    if (tr.isNoTransition()) {
      return;
    }
    rec.state = tr.newState();
    rec.ctx = tr.newContext();
    for (MultiLegFsmEffect effect : tr.effects()) {
      if (effect instanceof MultiLegFsmEffect.EmitEvent emit
          && "RouteFsm".equals(emit.targetFsm())
          && "RouteCancelRequested".equals(emit.event())) {
        cascadeCancelLegRoutes(rec);
      }
      // PublishEventLog / PublishFixMessage are handled by the event-sourcing and FIX-bridge
      // layers; at this layer the state change itself is the record.
    }
  }

  /** Dispatches venue cancels on every leg route that is still working. */
  private void cascadeCancelLegRoutes(PackageRec rec) {
    for (LegRec leg : rec.legs) {
      if (leg.routeId == null) {
        continue;
      }
      routeManager
          .findRoute(leg.routeId)
          .filter(r -> !r.isTerminal())
          .ifPresent(r -> routeManager.cancelRoute(leg.routeId));
    }
  }

  /** Under SEQUENCED, a leg fill releases the next pending leg (if the parent is still working). */
  private void dispatchNextSequencedLeg(PackageRec rec) {
    if (rec.executionMode != ExecutionMode.SEQUENCED
        || rec.state != MultiLegFsmState.LEGS_WORKING) {
      return;
    }
    for (LegRec leg : rec.legs) {
      if (leg.routeId == null) {
        dispatchLeg(rec, leg);
        return;
      }
    }
  }

  /**
   * Stages the leg as a child order, marks it ready, and routes it. Returns a rejection (leaving
   * the package state unchanged for the caller to cancel) or null on success.
   */
  private MultiLegEventResult.@Nullable Rejected dispatchLeg(PackageRec rec, LegRec leg) {
    String childReqId = rec.orderId + "-L" + leg.index;
    StageResult sr =
        som.stage(
            new OrderRequest(
                childReqId,
                rec.sessionId,
                rec.clOrdId + "-L" + leg.index,
                leg.figi,
                leg.side,
                leg.qty,
                leg.price,
                rec.account,
                rec.tif));
    if (sr instanceof StageResult.Rejected rejected) {
      return new MultiLegEventResult.Rejected(
          rec.orderId, rejected.rejectCode(), rejected.message());
    }
    String childOrderId = ((StageResult.Accepted) sr).order().orderId();
    MarkReadyResult mr = som.markReady(childOrderId, rec.sessionId);
    if (mr instanceof MarkReadyResult.Rejected rejected) {
      return new MultiLegEventResult.Rejected(
          rec.orderId, rejected.rejectCode(), rejected.message());
    }
    RouteResult rr =
        routeManager.route(
            new RouteRequest(
                childReqId + "-R", childOrderId, leg.venueMic, leg.qty, leg.price, null));
    if (rr instanceof RouteResult.Rejected rejected) {
      return new MultiLegEventResult.Rejected(
          rec.orderId, rejected.rejectCode(), rejected.message());
    }
    leg.childOrderId = childOrderId;
    leg.routeId = ((RouteResult.Routed) rr).route().routeId();
    return null;
  }

  private MultiLegOrder view(PackageRec rec) {
    List<OrderLeg> legViews = new ArrayList<>(rec.legs.size());
    for (LegRec leg : rec.legs) {
      legViews.add(
          new OrderLeg(
              leg.legId,
              leg.index,
              leg.ratio,
              leg.figi,
              leg.side,
              leg.qty,
              leg.price,
              leg.venueMic,
              leg.childOrderId,
              leg.routeId,
              legState(leg)));
    }
    return new MultiLegOrder(
        rec.orderId,
        rec.clOrdId,
        rec.sessionId,
        rec.state,
        rec.ctx,
        rec.sequencePolicy,
        legViews,
        rec.stagedAtMicros);
  }

  private LegState legState(LegRec leg) {
    if (leg.routeId == null) {
      return LegState.PENDING;
    }
    return routeManager
        .findRoute(leg.routeId)
        .map(
            route ->
                switch (route.fsmState()) {
                  case FILLED -> LegState.FILLED;
                  case CANCELED -> LegState.CANCELED;
                  case REJECTED -> LegState.REJECTED;
                  default -> LegState.ROUTING;
                })
        .orElse(LegState.ROUTING);
  }

  private static MultiLegEventResult.Rejected notFound(String orderId) {
    return new MultiLegEventResult.Rejected(
        orderId, "EMS-ORD-4001", "Package " + orderId + " not found.");
  }

  /** Mutable package state; all mutations run inside {@code synchronized (rec)}. */
  private static final class PackageRec {
    final String orderId;
    final String clOrdId;
    final long sessionId;
    final ExecutionMode executionMode;
    final @Nullable String sequencePolicy;
    final String account;
    final int tif;
    final long stagedAtMicros;
    final List<LegRec> legs;
    MultiLegFsmState state;
    MultiLegFsmContext ctx;

    PackageRec(String orderId, MultiLegOrderRequest request) {
      this.orderId = orderId;
      this.clOrdId = request.clOrdId();
      this.sessionId = request.sessionId();
      this.executionMode = request.executionMode();
      this.sequencePolicy = request.sequencePolicy();
      this.account = request.account();
      this.tif = request.tif();
      this.stagedAtMicros = System.currentTimeMillis() * 1_000L;
      this.legs = new ArrayList<>(request.legs().size());
      int index = 0;
      for (LegRequest leg : request.legs()) {
        this.legs.add(new LegRec(orderId + "-L" + index, index, leg));
        index++;
      }
      this.state = MultiLegFsmState.STAGED;
      this.ctx =
          new MultiLegFsmContext(
              orderId,
              request.kind().name(),
              request.executionMode().name(),
              request.legs().size(),
              0L,
              0L,
              0L,
              request.packageId());
    }

    @Nullable LegRec findLeg(String legId) {
      for (LegRec leg : legs) {
        if (leg.legId.equals(legId)) {
          return leg;
        }
      }
      return null;
    }
  }

  /** Mutable leg state; guarded by the owning {@link PackageRec}'s monitor. */
  private static final class LegRec {
    final String legId;
    final int index;
    final int ratio;
    final String figi;
    final int side;
    final long qty;
    final @Nullable Long price;
    final String venueMic;
    @Nullable String childOrderId;
    @Nullable String routeId;

    LegRec(String legId, int index, LegRequest request) {
      this.legId = legId;
      this.index = index;
      this.ratio = request.legRatio();
      this.figi = request.figi();
      this.side = request.side();
      this.qty = request.qty();
      this.price = request.price();
      this.venueMic = request.venueMic();
    }
  }
}
