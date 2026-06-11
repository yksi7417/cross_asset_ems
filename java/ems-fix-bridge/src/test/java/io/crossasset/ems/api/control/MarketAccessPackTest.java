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
import io.crossasset.ems.pretrade.risk.RiskLimits;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 15c3-5 pack tests (task 18.5): the canonical control mapping names every required control with
 * its rule cite; fat-finger appears DEFERRED with rationale + compensating controls (the 10.2
 * deferral is part of the attestation, not an omission); the export snapshots LIVE evidence from
 * the running services; and a failing evidence supplier surfaces in the export rather than
 * blanking.
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
    pack = EmsMarketAccessControls.standard("firm-a", killSwitch, riskLimits, borrow, () -> 9_000L);
  }

  @Test
  void canonicalMapping_namesEveryControl_fatFingerDeferredWithCompensating() {
    assertThat(pack.controls())
        .extracting(MarketAccessPack.ControlMapping::controlId)
        .containsExactly(
            "erroneous-orders-fat-finger",
            "duplicate-order-check",
            "credit-capital-limits",
            "regulatory-pre-trade",
            "order-rate-limiter",
            "kill-switch");

    MarketAccessPack.ControlMapping fatFinger = pack.controls().get(0);
    assertThat(fatFinger.status()).isEqualTo(MarketAccessPack.ControlStatus.DEFERRED);
    assertThat(fatFinger.deferralRationale()).contains("2026-06-10");
    assertThat(fatFinger.compensatingControls()).contains("10.6").contains("10.3");
    assertThat(fatFinger.ruleCite()).isEqualTo("15c3-5(c)(1)(i)");

    assertThat(pack.controls())
        .filteredOn(c -> c.status() == MarketAccessPack.ControlStatus.IMPLEMENTED)
        .hasSize(5);
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
  void summary_countsDeferred_andNeverReadsComplete() {
    JsonNode export = pack.attestationExport(0L);
    JsonNode summary = export.path("summary");
    assertThat(summary.path("controls").asInt()).isEqualTo(6);
    assertThat(summary.path("implemented").asInt()).isEqualTo(5);
    assertThat(summary.path("deferred").asInt()).isEqualTo(1);
    assertThat(summary.path("attestationNote").asText())
        .contains("DEFERRED")
        .doesNotContain("All mapped controls implemented");
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

  private static JsonNode controlNode(JsonNode export, String controlId) {
    for (JsonNode node : export.path("controls")) {
      if (controlId.equals(node.path("controlId").asText())) {
        return node;
      }
    }
    throw new AssertionError("control not in export: " + controlId);
  }
}
