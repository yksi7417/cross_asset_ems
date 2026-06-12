/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.tca;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Best-execution audit (task 12.10, [[arch-best-execution]] § Per-order best-ex record): for every
 * order, WHO chose the venue/broker, WHAT alternatives were on the table (an RFQ ladder's competing
 * quotes are exactly this), WHY the chosen one won, and how the executions actually scored (the
 * 12.14 TCA results). The adherence check is the committee's first filter:
 *
 * <ul>
 *   <li><b>ALIGNED</b> — the chosen option had the best expected cost among alternatives;
 *   <li><b>EXCEPTION</b> — a better-priced alternative existed but a rationale documents why it
 *       lost (ineligible counterparty, size, settlement risk, last-look history…);
 *   <li><b>DEVIATED</b> — a better-priced alternative existed and nobody wrote down why. These rows
 *       are what the committee reviews.
 * </ul>
 */
public final class BestExAuditor {

  /** One alternative considered at decision time. */
  public record Alternative(String broker, String venue, long expectedCostBp, String whyNot) {}

  /** One routing decision: who chose what, against which alternatives. */
  public record Decision(
      String decisionId,
      String decidedBy, // sor strategy id or the human
      String chosenBroker,
      String chosenVenue,
      long chosenExpectedCostBp,
      List<Alternative> alternatives,
      String rationale,
      long atMillis) {}

  public enum Adherence {
    ALIGNED,
    EXCEPTION,
    DEVIATED
  }

  /** The queryable per-order record. */
  public record BestExRecord(
      String orderId,
      List<Decision> decisions,
      List<TcaService.FillTca> executions,
      Adherence adherence,
      String explanation) {}

  private final Map<String, List<Decision>> decisions = new LinkedHashMap<>();
  private final Map<String, List<TcaService.FillTca>> executions = new LinkedHashMap<>();

  /** Record a routing decision (call at route/RFQ-elect time). */
  public void recordDecision(String orderId, Decision decision) {
    decisions
        .computeIfAbsent(Objects.requireNonNull(orderId), k -> new ArrayList<>())
        .add(decision);
  }

  /** Attach an execution's TCA result (call from the 12.14 pipeline). */
  public void recordExecution(String orderId, TcaService.FillTca tca) {
    executions.computeIfAbsent(Objects.requireNonNull(orderId), k -> new ArrayList<>()).add(tca);
  }

  /** The order's best-ex record with the adherence verdict. */
  public Optional<BestExRecord> record(String orderId) {
    List<Decision> orderDecisions = decisions.get(orderId);
    if (orderDecisions == null) {
      return Optional.empty();
    }
    Adherence worst = Adherence.ALIGNED;
    StringBuilder explanation = new StringBuilder();
    for (Decision decision : orderDecisions) {
      Optional<Alternative> better =
          decision.alternatives().stream()
              .filter(a -> a.expectedCostBp() < decision.chosenExpectedCostBp())
              .min(java.util.Comparator.comparingLong(Alternative::expectedCostBp));
      if (better.isEmpty()) {
        continue; // chose the best on the table
      }
      boolean documented = decision.rationale() != null && !decision.rationale().isBlank();
      Adherence verdict = documented ? Adherence.EXCEPTION : Adherence.DEVIATED;
      if (verdict.ordinal() > worst.ordinal()) {
        worst = verdict;
      }
      explanation
          .append(decision.decisionId())
          .append(": ")
          .append(better.get().broker())
          .append('/')
          .append(better.get().venue())
          .append(" was ")
          .append(decision.chosenExpectedCostBp() - better.get().expectedCostBp())
          .append("bp better")
          .append(documented ? " — rationale: " + decision.rationale() : " — NO RATIONALE")
          .append("; ");
    }
    if (explanation.isEmpty()) {
      explanation.append("chosen option was best on the table for every decision");
    }
    return Optional.of(
        new BestExRecord(
            orderId,
            Collections.unmodifiableList(orderDecisions),
            Collections.unmodifiableList(executions.getOrDefault(orderId, List.of())),
            worst,
            explanation.toString().trim()));
  }

  /** Every DEVIATED order — the committee's review queue. */
  public List<BestExRecord> deviations() {
    List<BestExRecord> out = new ArrayList<>();
    for (String orderId : decisions.keySet()) {
      record(orderId).filter(r -> r.adherence() == Adherence.DEVIATED).ifPresent(out::add);
    }
    return out;
  }
}
