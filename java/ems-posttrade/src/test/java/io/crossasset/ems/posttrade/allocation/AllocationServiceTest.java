/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationAnomaly;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationApplied;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationDeferred;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationRequested;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationReversed;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link InMemoryAllocationService} orchestration. */
class AllocationServiceTest {

  private static final List<AccountShare> SHARES_60_40 =
      List.of(new AccountShare("ACC_A", "PB1", 6000), new AccountShare("ACC_B", "PB1", 4000));

  private static AllocationTemplate template() {
    return AllocationTemplate.of(
        "TPL-1", 7L, AllocationPolicy.PRO_RATA, RoundingPolicy.DISTRIBUTE_RESIDUAL, SHARES_60_40);
  }

  private static Fill fill(String fillId, long qty) {
    return new Fill(fillId, "ORD-1", "RTE-1", qty, 9950L);
  }

  private static long appliedTotal(List<AllocationEvent> events) {
    return events.stream()
        .filter(e -> e instanceof AllocationApplied)
        .mapToLong(e -> ((AllocationApplied) e).qty())
        .sum();
  }

  private static List<AllocationApplied> applied(List<AllocationEvent> events) {
    return events.stream()
        .filter(e -> e instanceof AllocationApplied)
        .map(e -> (AllocationApplied) e)
        .toList();
  }

  @Test
  void concreteTemplate_emitsRequestedThenApplied() {
    AllocationService svc = new InMemoryAllocationService();
    List<AllocationEvent> events = svc.allocate(fill("F1", 100), template());

    assertThat(events.get(0)).isInstanceOf(AllocationRequested.class);
    List<AllocationApplied> applied = applied(events);
    assertThat(applied).hasSize(2);
    assertThat(applied.stream().map(AllocationApplied::qty).toList()).containsExactly(60L, 40L);
    assertThat(applied).allSatisfy(a -> assertThat(a.price()).isEqualTo(9950L));
    assertThat(appliedTotal(events)).isEqualTo(100);
  }

  @Test
  void deferredTemplate_parksFillAndDefers() {
    AllocationService svc = new InMemoryAllocationService();
    List<AllocationEvent> events =
        svc.allocate(fill("F1", 100), AllocationTemplate.deferred("DEF"));

    assertThat(events).singleElement().isInstanceOf(AllocationDeferred.class);
    assertThat(svc.deferredFills("ORD-1")).extracting(Fill::fillId).containsExactly("F1");
  }

  @Test
  void setAllocationTemplate_backAllocatesParkedFills() {
    AllocationService svc = new InMemoryAllocationService();
    svc.allocate(fill("F1", 100), AllocationTemplate.deferred("DEF"));
    svc.allocate(fill("F2", 50), AllocationTemplate.deferred("DEF"));

    List<AllocationEvent> events = svc.setAllocationTemplate("ORD-1", template());

    assertThat(appliedTotal(events)).isEqualTo(150);
    assertThat(applied(events)).hasSize(4); // 2 fills × 2 accounts
    assertThat(svc.deferredFills("ORD-1")).isEmpty();
  }

  @Test
  void validatorRejection_emitsAnomalyAndDoesNotAllocate() {
    AllocationValidator deny =
        (share, tpl) -> share.account().equals("ACC_B") ? "account disabled" : null;
    AllocationService svc = new InMemoryAllocationService(deny);

    List<AllocationEvent> events = svc.allocate(fill("F1", 100), template());

    assertThat(events).anyMatch(e -> e instanceof AllocationAnomaly);
    assertThat(applied(events)).isEmpty();
    assertThat(svc.appliedFor("F1")).isEmpty();
  }

  @Test
  void reverse_emitsReversedForEachApplied() {
    AllocationService svc = new InMemoryAllocationService();
    svc.allocate(fill("F1", 100), template());

    List<AllocationEvent> reversal = svc.reverse("F1", "trade_bust");

    assertThat(reversal).hasSize(2).allMatch(e -> e instanceof AllocationReversed);
    assertThat(svc.appliedFor("F1")).isEmpty();
  }

  @Test
  void correct_reversesThenReapplies() {
    AllocationService svc = new InMemoryAllocationService();
    svc.allocate(fill("F1", 100), template());

    List<AllocationEvent> events = svc.correct(fill("F1", 80), template(), "trade_correct");

    assertThat(events).filteredOn(e -> e instanceof AllocationReversed).hasSize(2);
    assertThat(appliedTotal(events)).isEqualTo(80);
  }

  @Test
  void allocation_isDeterministic() {
    List<AllocationEvent> a =
        new InMemoryAllocationService().allocate(fill("F1", 17), template17());
    List<AllocationEvent> b =
        new InMemoryAllocationService().allocate(fill("F1", 17), template17());
    assertThat(applied(a).stream().map(AllocationApplied::allocationId).toList())
        .isEqualTo(applied(b).stream().map(AllocationApplied::allocationId).toList());
    assertThat(applied(a).stream().map(AllocationApplied::qty).toList())
        .isEqualTo(applied(b).stream().map(AllocationApplied::qty).toList());
  }

  private static AllocationTemplate template17() {
    return AllocationTemplate.of(
        "TPL-2",
        1L,
        AllocationPolicy.PRO_RATA,
        RoundingPolicy.DISTRIBUTE_RESIDUAL,
        List.of(
            new AccountShare("A", "PB1", 3333),
            new AccountShare("B", "PB1", 3333),
            new AccountShare("C", "PB1", 3334)));
  }
}
