/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.pretrade.borrow.BorrowService;
import io.crossasset.ems.pretrade.compliance.ComplianceDecision;
import io.crossasset.ems.pretrade.compliance.ComplianceGate;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation;
import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import io.crossasset.ems.pretrade.compliance.FatFingerCheck;
import io.crossasset.ems.pretrade.risk.RiskLimits;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 15c3-5 pack tests (task 18.5): the canonical control mapping names every required control with
 * its rule cite; fat-finger attests IMPLEMENTED once the stage-path guard makes it fire end-to-end,
 * while credit/capital stays DEFERRED with rationale + compensating controls (the deferral is part
 * of the attestation, not an omission); the export snapshots LIVE evidence from the running
 * services; and a failing evidence supplier surfaces in the export rather than blanking.
 */
class MarketAccessPackTest {

  private KillSwitchService killSwitch;
  private RiskLimits riskLimits;
  private BorrowService borrow;
  private MarketAccessPack pack;
  private long opsSession;

  @BeforeEach
  void setUp() {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("ops", "firm-a", "desk-ops", "ops-1", Set.of("#kill-switch"));
    opsSession =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "ops")))
            .session()
            .sessionId();
    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(new LayeredValidatorPipeline(aaa, null, null));
    KillSwitchState state = new KillSwitchState();
    killSwitch =
        new KillSwitchService(
            aaa,
            som,
            new InMemoryRouteManager(som),
            state,
            new SubscriptionRegistry(),
            () -> 5_000L);
    riskLimits = new RiskLimits();
    borrow = new BorrowService(60_000L);
    // fatFingerWired=true: the gate is on by default AND the ComplianceStageGuard now presents
    // STAGE ops to a stage-scoped gate carrying FatFingerCheck, so the control fires end-to-end
    // — the attestation carries it IMPLEMENTED. credit-capital stays the only DEFERRED control.
    pack =
        EmsMarketAccessControls.standard(
            "firm-a", killSwitch, riskLimits, borrow, true, true, () -> 9_000L);
  }

  @Test
  void canonicalMapping_namesEveryControl_truthfulStatuses() {
    assertThat(pack.controls())
        .extracting(MarketAccessPack.ControlMapping::controlId)
        .containsExactly(
            "erroneous-orders-fat-finger",
            "duplicate-order-check",
            "credit-capital-limits",
            "regulatory-pre-trade",
            "order-rate-limiter",
            "kill-switch");

    // Fat-finger now fires end-to-end: the ComplianceStageGuard presents STAGE ops to a
    // stage-scoped gate carrying FatFingerCheck, so the attestation carries it IMPLEMENTED.
    MarketAccessPack.ControlMapping fatFinger = pack.controls().get(0);
    assertThat(fatFinger.status()).isEqualTo(MarketAccessPack.ControlStatus.IMPLEMENTED);
    assertThat(fatFinger.implementedBy()).contains("FatFingerCheck").contains("BenchmarkService");
    assertThat(fatFinger.ruleCite()).isEqualTo("15c3-5(c)(1)(i)");

    // Credit/capital limits (RiskEngine) are NOT wired into the live order path — the
    // attestation carries them DEFERRED with rationale + compensating controls, never a
    // false IMPLEMENTED.
    MarketAccessPack.ControlMapping credit = controlById("credit-capital-limits");
    assertThat(credit.status()).isEqualTo(MarketAccessPack.ControlStatus.DEFERRED);
    assertThat(credit.deferralRationale()).contains("not yet wired");
    assertThat(credit.compensatingControls()).contains("kill switch").contains("rate limiter");

    // Only credit/capital (RiskEngine not wired) remains DEFERRED; the other five controls —
    // including fat-finger, now firing end-to-end via the stage guard — attest IMPLEMENTED.
    assertThat(pack.controls())
        .filteredOn(c -> c.status() == MarketAccessPack.ControlStatus.IMPLEMENTED)
        .hasSize(5);
    assertThat(pack.controls())
        .filteredOn(c -> c.status() == MarketAccessPack.ControlStatus.DEFERRED)
        .hasSize(1);
  }

  @Test
  void fatFingerDeferredPendingStageGuard_butImplementedOnceEnforced() {
    // Not end-to-end enforced today (no stage-path guard): DEFERRED with the stage-path rationale.
    MarketAccessPack notEnforced =
        EmsMarketAccessControls.standard(
            "firm-a", killSwitch, riskLimits, borrow, true, false, () -> 9_000L);
    MarketAccessPack.ControlMapping deferred = notEnforced.controls().get(0);
    assertThat(deferred.status()).isEqualTo(MarketAccessPack.ControlStatus.DEFERRED);
    assertThat(deferred.deferralRationale()).contains("stage-path");

    // Once a stage-path guard makes FatFingerCheck fire (fatFingerWired=true), it attests
    // IMPLEMENTED — the follow-up PR flips one flag, no attestation edit needed.
    MarketAccessPack enforced =
        EmsMarketAccessControls.standard(
            "firm-a", killSwitch, riskLimits, borrow, true, true, () -> 9_000L);
    assertThat(enforced.controls().get(0).status())
        .isEqualTo(MarketAccessPack.ControlStatus.IMPLEMENTED);
  }

  @Test
  void complianceGateBackedControls_deferredWhenGateDisabled() {
    MarketAccessPack gateDisabled =
        EmsMarketAccessControls.standard(
            "firm-a", killSwitch, riskLimits, borrow, false, false, () -> 9_000L);

    assertThat(controlById(gateDisabled, "regulatory-pre-trade").status())
        .isEqualTo(MarketAccessPack.ControlStatus.DEFERRED);
    assertThat(controlById(gateDisabled, "order-rate-limiter").status())
        .isEqualTo(MarketAccessPack.ControlStatus.DEFERRED);
  }

  @Test
  void export_snapshotsLiveEvidence() {
    // Run a kill drill and amend a limit so the export has real journals to show.
    killSwitch.engage(
        new KillSwitchState.Scope(KillSwitchState.Kind.FIRM, "firm-a"),
        opsSession,
        "annual attestation drill");
    killSwitch.release(
        new KillSwitchState.Scope(KillSwitchState.Kind.FIRM, "firm-a"),
        opsSession,
        "drill complete");
    riskLimits.set(
        RiskLimits.Scope.DESK,
        "desk-1",
        new RiskLimits.Limits(1_000_000L, 10_000_000L, 100L, 5_000_000L),
        "attestation calibration",
        "risk-officer-1");
    borrow.recordAvailability("BBG000BLNNH6", BorrowService.INTERNAL, 1_000, 25);
    borrow.locate("BBG000BLNNH6", 100, "t1", 0L);

    JsonNode export = pack.attestationExport(42_000L);

    assertThat(export.path("standard").asText()).contains("15c3-5");
    assertThat(export.path("firm").asText()).isEqualTo("firm-a");

    JsonNode killEvidence = controlNode(export, "kill-switch").path("evidence");
    assertThat(killEvidence.path("auditEntries").asInt()).isEqualTo(2);
    assertThat(killEvidence.path("lastAction").path("action").asText()).isEqualTo("RELEASE");
    assertThat(killEvidence.path("engagedScopes")).isEmpty();

    JsonNode limitsEvidence = controlNode(export, "credit-capital-limits").path("evidence");
    assertThat(limitsEvidence.path("amendmentCount").asInt()).isEqualTo(1);
    assertThat(limitsEvidence.path("recentAmendments").get(0).path("signedOffBy").asText())
        .isEqualTo("risk-officer-1");

    JsonNode regEvidence = controlNode(export, "regulatory-pre-trade").path("evidence");
    assertThat(regEvidence.path("regShoLocatesActive").asInt()).isEqualTo(1);
  }

  @Test
  void summary_deferredGapAcknowledged_attestationReadsHonest() {
    JsonNode export = pack.attestationExport(0L);
    JsonNode summary = export.path("summary");
    assertThat(summary.path("controls").asInt()).isEqualTo(6);
    assertThat(summary.path("implemented").asInt()).isEqualTo(5);
    assertThat(summary.path("deferred").asInt()).isEqualTo(1);
    // The remaining gap (credit-capital-limits) must stay visible in the attestation, not
    // papered over.
    assertThat(summary.path("attestationNote").asText()).contains("DEFERRED");

    JsonNode credit = controlNode(export, "credit-capital-limits");
    assertThat(credit.path("status").asText()).isEqualTo("DEFERRED");
    assertThat(credit.path("deferralRationale").asText()).contains("not yet wired");
    assertThat(credit.path("compensatingControls").asText()).contains("kill switch");
  }

  @Test
  void failingEvidenceSupplier_surfacesInExport() {
    MarketAccessPack custom = new MarketAccessPack("firm-x");
    custom.register(
        new MarketAccessPack.ControlMapping(
            "broken",
            "15c3-5(c)",
            "Broken evidence",
            MarketAccessPack.ControlStatus.IMPLEMENTED,
            "x",
            null,
            null,
            () -> {
              throw new IllegalStateException("collector offline");
            }));

    JsonNode export = custom.attestationExport(0L);
    assertThat(controlNode(export, "broken").path("evidence").path("evidenceError").asText())
        .contains("collector offline");
  }

  /**
   * Proves FatFingerCheck itself is correct: an exploded order (an extra few zeros on the quantity)
   * presented to the compliance gate on a STAGE op is BLOCKED. The check works, and the live edge
   * now presents STAGE ops to it via the ComplianceStageGuard, so the attestation reports
   * fat-finger IMPLEMENTED; only the un-wired credit/capital control remains DEFERRED.
   */
  @Test
  void absurdOrderBlockedByGate_andAttestationImplemented() {
    // The gate exactly as TraderDesktopEdgeMain builds it by default (Policy 1e13 ceiling,
    // 5000bp band, 2x netting relief, no block-on-no-reference), with a live $100 mid.
    long mid = 100_0000L; // $100.00, fixed-point 1e4
    FatFingerCheck fatFinger =
        new FatFingerCheck(
            new FatFingerCheck.Policy(10_000_000_000_000L, 5_000, 2, false),
            figi -> OptionalLong.of(mid),
            (account, figi) -> 0L,
            figi -> 1L);
    ComplianceGate gate = new ComplianceGate(List.of(fatFinger));

    // A normal order (2,000 shares @ $100 = $200k) sails through.
    ComplianceDecision normal = gate.evaluate(stageOp(2_000L, mid));
    assertThat(normal.outcome()).isEqualTo(ComplianceOutcome.ALLOW);

    // The same order fat-fingered to 2,000,000,000 shares (notional 2e17 ≫ 1e13 ceiling) BLOCKS.
    ComplianceDecision absurd = gate.evaluate(stageOp(2_000_000_000L, mid));
    assertThat(absurd.outcome()).isEqualTo(ComplianceOutcome.BLOCK);

    // The check blocks, and the shipped attestation now reports it IMPLEMENTED because the
    // stage guard makes it fire end-to-end; only credit/capital remains DEFERRED.
    assertThat(controlById("erroneous-orders-fat-finger").status())
        .isEqualTo(MarketAccessPack.ControlStatus.IMPLEMENTED);
    assertThat(controlById("credit-capital-limits").status())
        .isEqualTo(MarketAccessPack.ControlStatus.DEFERRED);
  }

  private static ComplianceOperation stageOp(long qty, Long price) {
    return new ComplianceOperation(
        ComplianceOperation.Kind.STAGE,
        7L,
        "firm-a",
        "desk-1",
        "trader-1",
        null,
        "BBG000B9XRY4",
        1,
        qty,
        price,
        "ACC-1");
  }

  private MarketAccessPack.ControlMapping controlById(String controlId) {
    return controlById(pack, controlId);
  }

  private static MarketAccessPack.ControlMapping controlById(
      MarketAccessPack sourcePack, String controlId) {
    return sourcePack.controls().stream()
        .filter(c -> controlId.equals(c.controlId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("control not in pack: " + controlId));
  }

  private static JsonNode controlNode(JsonNode export, String controlId) {
    for (JsonNode node : export.path("controls")) {
      if (controlId.equals(node.path("controlId").asText())) {
        return node;
      }
    }
    throw new AssertionError("control not in export: " + controlId);
  }
}
