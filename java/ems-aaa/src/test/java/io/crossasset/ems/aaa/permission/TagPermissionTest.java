/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.permission;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.Identity;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.aaa.identity.Desk;
import io.crossasset.ems.aaa.identity.Firm;
import io.crossasset.ems.aaa.identity.InMemoryIdentityRepository;
import io.crossasset.ems.aaa.identity.User;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the tag-permission 3-layer AND-gate. Per arch-tag-permissions.md.
 *
 * <p>Task 5.3 — Tag permissions 3-layer AND-gate.
 */
class TagPermissionTest {

  private InMemoryTagPermissionStore store;
  private InMemoryIdentityRepository identityRepo;
  private TagPermissionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    store = new InMemoryTagPermissionStore();
    identityRepo = new InMemoryIdentityRepository();
    identityRepo.addFirm(new Firm("ALPHA", "Alpha Capital", "firm_admin@alpha"));
    identityRepo.addDesk(new Desk("ALPHA", "D1", "Credit Desk 1", "dave.lee@alpha"));
    identityRepo.addDesk(new Desk("ALPHA", "D2", "Credit Desk 2", "carol.k@alpha"));
    identityRepo.addUser(new User("ALPHA", "D1", "anne", "Anne Trader", "dave.lee@alpha"));
    evaluator = new TagPermissionEvaluator(store, identityRepo);
  }

  // Helper: build an Identity for (ALPHA, D1, anne) with user-tag algo-execution
  private Identity anneIdentity(String... tags) {
    return new Identity("ALPHA", "D1", "anne", "tok-anne", Set.of(tags), Set.of(tags));
  }

  // ── Allow — all three layers granted ─────────────────────────────────────

  @Test
  void authorize_allThreeGranted_returnsAllow() {
    store.grantFirmTag("ALPHA", "algo-execution");
    store.grantDeskTag("ALPHA", "D1", "algo-execution");
    Identity anne = anneIdentity("algo-execution");
    assertInstanceOf(AuthorizationResult.Allow.class, evaluator.authorize(anne, "algo-execution"));
  }

  // ── Deny — firm not granted (EMS-PRM-1003) ───────────────────────────────

  @Test
  void authorize_firmNotGranted_returnsPrm1003() {
    // desk and user have the tag, but firm does not
    store.grantDeskTag("ALPHA", "D1", "algo-execution");
    Identity anne = anneIdentity("algo-execution");
    AuthorizationResult.Deny deny =
        assertInstanceOf(
            AuthorizationResult.Deny.class, evaluator.authorize(anne, "algo-execution"));
    assertEquals("EMS-PRM-1003", deny.rejectCode());
    assertEquals(DenialLevel.FIRM, deny.missingLevel());
    assertTrue(deny.message().contains("ALPHA"));
    assertTrue(deny.message().contains("algo-execution"));
    assertEquals("firm_admin@alpha", deny.adminHint());
  }

  // ── Deny — desk not granted (EMS-PRM-1002) — arch-doc worked example ─────

  @Test
  void authorize_deskNotGranted_returnsPrm1002_arcDocExample() {
    // Arch doc example: Firm A + Desk D2 + Anne granted; Anne is at D1 (not D2)
    store.grantFirmTag("ALPHA", "algo-execution"); // firm IS granted
    store.grantDeskTag("ALPHA", "D2", "algo-execution"); // D2 IS granted, D1 is NOT
    // Anne is at D1 with user-level grant
    Identity anne = anneIdentity("algo-execution");
    AuthorizationResult.Deny deny =
        assertInstanceOf(
            AuthorizationResult.Deny.class, evaluator.authorize(anne, "algo-execution"));
    assertEquals("EMS-PRM-1002", deny.rejectCode());
    assertEquals(DenialLevel.DESK, deny.missingLevel());
    assertTrue(deny.message().contains("anne"));
    assertTrue(deny.message().contains("D1"));
    assertTrue(deny.message().contains("algo-execution"));
    assertEquals("dave.lee@alpha", deny.adminHint());
  }

  // ── Deny — user not granted (EMS-PRM-1001) ───────────────────────────────

  @Test
  void authorize_userNotGranted_returnsPrm1001() {
    // firm + desk granted; user does NOT have the tag
    store.grantFirmTag("ALPHA", "algo-execution");
    store.grantDeskTag("ALPHA", "D1", "algo-execution");
    Identity anne = anneIdentity(); // no tags
    AuthorizationResult.Deny deny =
        assertInstanceOf(
            AuthorizationResult.Deny.class, evaluator.authorize(anne, "algo-execution"));
    assertEquals("EMS-PRM-1001", deny.rejectCode());
    assertEquals(DenialLevel.USER, deny.missingLevel());
    assertTrue(deny.message().contains("anne"));
    assertTrue(deny.message().contains("algo-execution"));
  }

  // ── Outermost-first ordering ──────────────────────────────────────────────

  @Test
  void authorize_firmAndDeskBothMissing_reportsFirmFirst() {
    // Neither firm nor desk granted — should report FIRM (EMS-PRM-1003), not DESK
    Identity anne = anneIdentity("algo-execution");
    AuthorizationResult.Deny deny =
        assertInstanceOf(
            AuthorizationResult.Deny.class, evaluator.authorize(anne, "algo-execution"));
    assertEquals("EMS-PRM-1003", deny.rejectCode());
    assertEquals(DenialLevel.FIRM, deny.missingLevel());
  }

  // ── Grant / revoke ────────────────────────────────────────────────────────

  @Test
  void revokeFirmTag_removesGrant() {
    store.grantFirmTag("ALPHA", "algo-execution");
    store.grantDeskTag("ALPHA", "D1", "algo-execution");
    Identity anne = anneIdentity("algo-execution");
    assertInstanceOf(AuthorizationResult.Allow.class, evaluator.authorize(anne, "algo-execution"));
    store.revokeFirmTag("ALPHA", "algo-execution");
    assertInstanceOf(AuthorizationResult.Deny.class, evaluator.authorize(anne, "algo-execution"));
  }

  @Test
  void revokeDeskTag_removesGrant() {
    store.grantFirmTag("ALPHA", "algo-execution");
    store.grantDeskTag("ALPHA", "D1", "algo-execution");
    Identity anne = anneIdentity("algo-execution");
    assertInstanceOf(AuthorizationResult.Allow.class, evaluator.authorize(anne, "algo-execution"));
    store.revokeDeskTag("ALPHA", "D1", "algo-execution");
    assertInstanceOf(AuthorizationResult.Deny.class, evaluator.authorize(anne, "algo-execution"));
  }

  // ── computeEffectiveTags ──────────────────────────────────────────────────

  @Test
  void computeEffectiveTags_intersectsUserTagsWithFirmAndDesk() {
    // Alice has tags: trade-eq and trade-fi
    // Firm grants: trade-eq only
    // Desk grants: trade-eq only
    // effectiveTags should be {trade-eq}
    store.grantFirmTag("ALPHA", "trade-eq");
    store.grantDeskTag("ALPHA", "D1", "trade-eq");
    Identity alice =
        new Identity("ALPHA", "D1", "alice", "tok", Set.of("trade-eq", "trade-fi"), Set.of());
    Set<String> effective = evaluator.computeEffectiveTags(alice);
    assertEquals(Set.of("trade-eq"), effective);
  }

  @Test
  void computeEffectiveTags_noFirmGrants_returnsEmpty() {
    Identity anne = anneIdentity("algo-execution");
    Set<String> effective = evaluator.computeEffectiveTags(anne);
    assertTrue(effective.isEmpty());
  }

  @Test
  void computeEffectiveTags_allGranted_returnsAllUserTags() {
    store.grantFirmTag("ALPHA", "trade-eq");
    store.grantFirmTag("ALPHA", "trade-fi");
    store.grantDeskTag("ALPHA", "D1", "trade-eq");
    store.grantDeskTag("ALPHA", "D1", "trade-fi");
    Identity alice =
        new Identity("ALPHA", "D1", "alice", "tok", Set.of("trade-eq", "trade-fi"), Set.of());
    Set<String> effective = evaluator.computeEffectiveTags(alice);
    assertEquals(Set.of("trade-eq", "trade-fi"), effective);
  }

  // ── Integration: effectiveTags computed at logon ──────────────────────────

  @Test
  void logon_withEvaluator_effectiveTagsAreAndGated() {
    store.grantFirmTag("ALPHA", "trade-eq");
    store.grantDeskTag("ALPHA", "D1", "trade-eq");
    // trade-fi: firm does NOT grant it

    InMemoryAaaEventLog eventLog = new InMemoryAaaEventLog();
    InMemoryAaaService svc = new InMemoryAaaService(eventLog, evaluator);
    svc.registerCredential("tok-anne", "ALPHA", "D1", "anne", Set.of("trade-eq", "trade-fi"));

    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            svc.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-anne")));

    Identity id = accepted.session().identity();
    assertEquals(Set.of("trade-eq", "trade-fi"), id.tags()); // raw grants unchanged
    assertEquals(Set.of("trade-eq"), id.effectiveTags()); // AND-gated
  }
}
