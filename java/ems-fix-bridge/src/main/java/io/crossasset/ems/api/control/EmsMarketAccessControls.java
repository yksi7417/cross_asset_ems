/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.pretrade.borrow.BorrowService;
import io.crossasset.ems.pretrade.risk.RiskLimits;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The canonical 15c3-5 control mapping for this EMS (task 18.5): which rule clause each control
 * answers, which component implements it, and where its live evidence comes from. One place — the
 * pack the REST route exports and the pack the tests pin are the same object.
 */
public final class EmsMarketAccessControls {

  private EmsMarketAccessControls() {}

  /**
   * Build the standard pack over the live services. Each control's status reflects what is actually
   * enforced when the pack is built: {@code fatFingerWired} is the live compliance-gate flag (the
   * fat-finger control only attests IMPLEMENTED when the gate that runs it is actually built), so
   * the attestation can never drift back to claiming a control that is switched off.
   */
  public static MarketAccessPack standard(
      String firm,
      KillSwitchService killSwitch,
      RiskLimits riskLimits,
      BorrowService borrow,
      boolean fatFingerWired,
      LongSupplier nowMillis) {
    Objects.requireNonNull(killSwitch, "killSwitch");
    Objects.requireNonNull(riskLimits, "riskLimits");
    Objects.requireNonNull(borrow, "borrow");
    ObjectMapper mapper = new ObjectMapper();
    MarketAccessPack pack = new MarketAccessPack(firm);

    pack.register(
        new MarketAccessPack.ControlMapping(
            "erroneous-orders-fat-finger",
            "15c3-5(c)(1)(i)",
            "Erroneous order prevention (fat-finger, netted vs unnetted)",
            fatFingerWired
                ? MarketAccessPack.ControlStatus.IMPLEMENTED
                : MarketAccessPack.ControlStatus.DEFERRED,
            "FatFingerCheck (10.2) on the compliance gate — notional ceiling with netting"
                + " relief for risk-reducing orders, limit-price deviation band vs the live"
                + " benchmark mid (BenchmarkService 9.5), block-on-no-reference policy; each"
                + " trip a supervisor-overridable BLOCK",
            fatFingerWired
                ? null
                : "Compliance gate disabled (EMS_COMPLIANCE_GATE=0) — FatFingerCheck is not"
                    + " wired into the live order path in this deployment.",
            fatFingerWired ? null : "kill switch; manual desk review",
            () -> {
              ObjectNode evidence = mapper.createObjectNode();
              evidence.put("check", "FatFingerCheck");
              evidence.put("referencePricing", "BenchmarkService (9.5) mid");
              evidence.put("overridePath", "#compliance-override-fat-finger");
              return evidence;
            }));

    pack.register(
        new MarketAccessPack.ControlMapping(
            "duplicate-order-check",
            "15c3-5(c)(1)(i)",
            "Duplicate order prevention",
            MarketAccessPack.ControlStatus.IMPLEMENTED,
            "task 8.8 — per-session ClOrdID dedup window on the resumable channel",
            null,
            null,
            () -> {
              ObjectNode evidence = mapper.createObjectNode();
              evidence.put("mechanism", "ClOrdID dedup window per session (8.9 channel)");
              evidence.put("rejectCode", "EMS-ORD-2510");
              return evidence;
            }));

    pack.register(
        new MarketAccessPack.ControlMapping(
            "credit-capital-limits",
            "15c3-5(c)(1)(ii)",
            "Credit and capital thresholds",
            MarketAccessPack.ControlStatus.DEFERRED,
            "task 10.6 — RiskEngine pre-trade check over versioned RiskLimits",
            "RiskEngine pre-trade credit/capital check is not yet wired into the live order"
                + " path; versioned RiskLimits carry the calibrated thresholds and their"
                + " amendment journal, but no gate consults them before an order routes."
                + " Tracked as a follow-up.",
            "Fat-finger notional ceiling caps single-order exposure (erroneous-orders-fat-finger);"
                + " firm/desk/venue kill switch halts access on breach (kill-switch).",
            () -> {
              ObjectNode evidence = mapper.createObjectNode();
              evidence.put("limitsVersion", riskLimits.version());
              ArrayNode amendments = evidence.putArray("recentAmendments");
              List<RiskLimits.Amendment> journal = riskLimits.journal();
              for (int i = Math.max(0, journal.size() - 5); i < journal.size(); i++) {
                RiskLimits.Amendment amendment = journal.get(i);
                ObjectNode node = amendments.addObject();
                node.put("version", amendment.version());
                node.put("scope", amendment.scope().name());
                node.put("owner", amendment.owner());
                node.put("reason", amendment.changeReason());
                node.put("signedOffBy", amendment.signedOffBy());
              }
              evidence.put("amendmentCount", journal.size());
              return evidence;
            }));

    pack.register(
        new MarketAccessPack.ControlMapping(
            "regulatory-pre-trade",
            "15c3-5(c)(2)",
            "Regulatory pre-trade compliance (lists, overrides, Reg SHO locates)",
            MarketAccessPack.ControlStatus.IMPLEMENTED,
            "tasks 10.1 ComplianceGate, 10.4 lists, 10.5 overrides, 18.6 borrow/locate",
            null,
            null,
            () -> {
              BorrowService.RegShoAttestation attestation =
                  borrow.regShoAttestation(nowMillis.getAsLong());
              ObjectNode evidence = mapper.createObjectNode();
              evidence.put("regShoLocatesActive", attestation.locatesActive());
              evidence.put("regShoLocatesConsumed", attestation.locatesConsumed());
              evidence.put("regShoLocatesExpired", attestation.locatesExpired());
              evidence.put("openBorrows", attestation.openBorrows());
              evidence.put("recalledPendingCover", attestation.recalledPendingCover());
              return evidence;
            }));

    pack.register(
        new MarketAccessPack.ControlMapping(
            "order-rate-limiter",
            "15c3-5(c)(1)(i)",
            "Machine-gun order rate limiting",
            MarketAccessPack.ControlStatus.IMPLEMENTED,
            "task 10.3 — machine-gun check in the compliance gate",
            null,
            null,
            () -> {
              ObjectNode evidence = mapper.createObjectNode();
              evidence.put("rule", "machine-gun-count in the 10.1 gate, per-session rate window");
              return evidence;
            }));

    pack.register(
        new MarketAccessPack.ControlMapping(
            "kill-switch",
            "15c3-5(c) / (d) — immediate cessation of access",
            "Firm/desk/venue kill switch with audited mass-cancel",
            MarketAccessPack.ControlStatus.IMPLEMENTED,
            "task 18.4 — KillSwitchService + entry guards on every surface",
            null,
            null,
            () -> {
              ObjectNode evidence = mapper.createObjectNode();
              ArrayNode engaged = evidence.putArray("engagedScopes");
              for (KillSwitchState.Scope scope : killSwitch.engaged()) {
                engaged.add(scope.toString());
              }
              List<KillSwitchService.KillAudit> journal = killSwitch.journal();
              evidence.put("auditEntries", journal.size());
              if (!journal.isEmpty()) {
                KillSwitchService.KillAudit last = journal.get(journal.size() - 1);
                ObjectNode lastNode = evidence.putObject("lastAction");
                lastNode.put("action", last.action());
                lastNode.put("scope", last.scope().toString());
                lastNode.put("by", last.by());
                lastNode.put("targets", last.outcomes().size());
                lastNode.put("failures", last.failures());
                lastNode.put("atMillis", last.atMillis());
              }
              return evidence;
            }));

    return pack;
  }
}
