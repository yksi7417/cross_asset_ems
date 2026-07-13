/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryAllocationServiceTest {

  @Test
  void defaultConstructorWorks() {
    InMemoryAllocationService svc = new InMemoryAllocationService();
    assertNotNull(svc);
  }

  @Test
  void deferredFillReturnsDeferredEvent() {
    InMemoryAllocationService svc = new InMemoryAllocationService();
    Fill fill = new Fill("fill-1", "order-1", "route-1", 1000L, 50L);
    AllocationTemplate tmpl = AllocationTemplate.deferred("tmpl-1");
    List<AllocationEvent> events = svc.allocate(fill, tmpl);
    assertEquals(1, events.size());
    assertTrue(events.get(0) instanceof AllocationEvent.AllocationDeferred);
  }

  @Test
  void setAllocationTemplateOnDeferredFillAllocates() {
    InMemoryAllocationService svc = new InMemoryAllocationService();
    Fill fill = new Fill("fill-1", "order-1", "route-1", 1000L, 50L);
    AllocationTemplate deferred = AllocationTemplate.deferred("order-1");
    svc.allocate(fill, deferred);

    List<AccountShare> shares =
        List.of(
            new AccountShare("acct-1", "pb-1", 5000L), new AccountShare("acct-2", "pb-1", 5000L));
    AllocationTemplate real =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, shares);
    List<AllocationEvent> events = svc.setAllocationTemplate("order-1", real);
    assertTrue(events.size() >= 2);
  }

  @Test
  void reverseReturnsReversalEvents() {
    InMemoryAllocationService svc = new InMemoryAllocationService();
    Fill fill = new Fill("fill-1", "order-1", "route-1", 1000L, 50L);
    List<AccountShare> shares = List.of(new AccountShare("acct-1", "pb-1", 10000L));
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, shares);
    List<AllocationEvent> allocEvents = svc.allocate(fill, tmpl);
    assertTrue(allocEvents.stream().anyMatch(e -> e instanceof AllocationEvent.AllocationApplied));

    List<AllocationEvent> reverseEvents = svc.reverse("fill-1", "trade bust");
    assertEquals(1, reverseEvents.size());
    assertTrue(reverseEvents.get(0) instanceof AllocationEvent.AllocationReversed);
  }

  @Test
  void reverseOnUnknownFillReturnsEmpty() {
    InMemoryAllocationService svc = new InMemoryAllocationService();
    List<AllocationEvent> events = svc.reverse("unknown", "reason");
    assertEquals(0, events.size());
  }

  @Test
  void deferredFillsReturnsParked() {
    InMemoryAllocationService svc = new InMemoryAllocationService();
    Fill fill = new Fill("fill-1", "order-1", "route-1", 1000L, 50L);
    AllocationTemplate deferred = AllocationTemplate.deferred("order-1");
    svc.allocate(fill, deferred);
    List<Fill> parked = svc.deferredFills("order-1");
    assertEquals(1, parked.size());
  }

  @Test
  void appliedForReturnsAppliedAllocations() {
    InMemoryAllocationService svc = new InMemoryAllocationService();
    Fill fill = new Fill("fill-1", "order-1", "route-1", 1000L, 50L);
    List<AccountShare> shares = List.of(new AccountShare("acct-1", "pb-1", 10000L));
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, shares);
    svc.allocate(fill, tmpl);
    List<AllocationEvent.AllocationApplied> applied = svc.appliedFor("fill-1");
    assertEquals(1, applied.size());
  }
}
