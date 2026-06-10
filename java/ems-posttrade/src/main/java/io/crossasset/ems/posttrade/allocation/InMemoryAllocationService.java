/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationAnomaly;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationApplied;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationDeferred;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationRequested;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationReversed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AllocationService}. Deterministic: allocation IDs are derived from {@code fillId
 * + ":" + account} and the split is a pure function of the fill, template, and rounding policy — no
 * clock or randomness — so replay reproduces identical events.
 */
public final class InMemoryAllocationService implements AllocationService {

  private final AllocationValidator validator;

  /** Fills parked under a deferred template, keyed by order id. */
  private final Map<String, List<Fill>> parked = new ConcurrentHashMap<>();

  /** Applied allocations per fill, retained so a bust/correct can reverse them. */
  private final Map<String, List<AllocationApplied>> appliedByFill = new ConcurrentHashMap<>();

  public InMemoryAllocationService() {
    this(AllocationValidator.permissive());
  }

  public InMemoryAllocationService(AllocationValidator validator) {
    this.validator = validator;
  }

  @Override
  public List<AllocationEvent> allocate(Fill fill, AllocationTemplate template) {
    if (template.deferred()) {
      parked.computeIfAbsent(fill.orderId(), k -> new ArrayList<>()).add(fill);
      return List.of(new AllocationDeferred(fill.fillId(), "block_now_allocate_later"));
    }
    return applyTemplate(fill, template);
  }

  @Override
  public List<AllocationEvent> setAllocationTemplate(String orderId, AllocationTemplate template) {
    List<Fill> fills = parked.remove(orderId);
    if (fills == null || fills.isEmpty()) {
      return List.of();
    }
    List<AllocationEvent> events = new ArrayList<>();
    for (Fill fill : fills) {
      events.addAll(applyTemplate(fill, template));
    }
    return events;
  }

  @Override
  public List<AllocationEvent> reverse(String fillId, String reason) {
    List<AllocationApplied> applied = appliedByFill.remove(fillId);
    if (applied == null || applied.isEmpty()) {
      return List.of();
    }
    List<AllocationEvent> events = new ArrayList<>(applied.size());
    for (AllocationApplied a : applied) {
      events.add(new AllocationReversed(fillId, a.allocationId(), reason));
    }
    return events;
  }

  @Override
  public List<AllocationEvent> correct(
      Fill correctedFill, AllocationTemplate template, String reason) {
    List<AllocationEvent> events = new ArrayList<>(reverse(correctedFill.fillId(), reason));
    events.addAll(applyTemplate(correctedFill, template));
    return events;
  }

  @Override
  public List<Fill> deferredFills(String orderId) {
    return List.copyOf(parked.getOrDefault(orderId, List.of()));
  }

  @Override
  public List<AllocationApplied> appliedFor(String fillId) {
    return List.copyOf(appliedByFill.getOrDefault(fillId, List.of()));
  }

  // ── internals ────────────────────────────────────────────────────────────────

  private List<AllocationEvent> applyTemplate(Fill fill, AllocationTemplate template) {
    List<AllocationEvent> events = new ArrayList<>();
    events.add(
        new AllocationRequested(
            fill.fillId(),
            fill.orderId(),
            fill.routeId(),
            template.templateId(),
            template.version(),
            template.policy(),
            template.rounding()));

    // Pre-allocation validation: any rejected account fails the whole fill (no partial allocation).
    for (AccountShare share : template.shares()) {
      String reason = validator.rejectionReason(share, template);
      if (reason != null) {
        events.add(
            new AllocationAnomaly(
                fill.fillId(),
                "account " + share.account() + " rejected: " + reason,
                "NeedAllocationReview"));
        return events;
      }
    }

    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(fill.qty(), template.shares(), template.rounding());
    List<AllocationApplied> applied = new ArrayList<>(slices.size());
    for (AllocationSplitter.Slice slice : slices) {
      AccountShare share = slice.share();
      AllocationApplied a =
          new AllocationApplied(
              fill.fillId() + ":" + share.account(),
              fill.fillId(),
              share.account(),
              share.primeBroker(),
              slice.qty(),
              fill.price(),
              share.primeBroker());
      applied.add(a);
      events.add(a);
    }
    appliedByFill.put(fill.fillId(), applied);
    return events;
  }
}
