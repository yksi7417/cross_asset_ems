/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.validator;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.aaa.identity.Desk;
import io.crossasset.ems.aaa.identity.Firm;
import io.crossasset.ems.aaa.identity.InMemoryIdentityRepository;
import io.crossasset.ems.aaa.identity.User;
import io.crossasset.ems.aaa.permission.InMemoryTagPermissionStore;
import io.crossasset.ems.aaa.permission.TagPermissionEvaluator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that permission denial messages carry correct admin-hint pointers per arch-validator.md
 * Section "Permission denial wording":
 *
 * <ul>
 *   <li>EMS-PRM-1001 — user missing tag → "Talk to tag admin for #{tag}."
 *   <li>EMS-PRM-1002 — desk not granted → "Talk to {desk.adminRef}."
 *   <li>EMS-PRM-1003 — firm not granted → "Talk to {firm.adminRef}."
 * </ul>
 *
 * <p>Admin-hint content is sourced from {@link InMemoryIdentityRepository} when present, giving
 * actionable contacts rather than generic fallback strings.
 *
 * <p>Task 6.3 — Permission denial messages with admin-hint pointers.
 */
class PermissionDenialMessageTest {

  private static final String FIRM = "FIRM-A";
  private static final String DESK = "DESK-01";
  private static final String USER = "trader";
  private static final String TOKEN = "tok-trader";
  private static final String TAG = "fx-trader";

  private static final String FIRM_ADMIN = "coo@firm-a.example.com";
  private static final String DESK_ADMIN = "desk-head@firm-a.example.com";

  private InMemoryAaaService aaaService;
  private InMemoryTagPermissionStore permStore;
  private InMemoryIdentityRepository identityRepo;
  private TagPermissionEvaluator permEvaluator;
  private LayeredValidatorPipeline pipeline;

  @BeforeEach
  void setUp() {
    aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaaService.registerCredential(TOKEN, FIRM, DESK, USER, Set.of(TAG));

    identityRepo = new InMemoryIdentityRepository();
    identityRepo.addFirm(new Firm(FIRM, "Firm Alpha", FIRM_ADMIN));
    identityRepo.addDesk(new Desk(FIRM, DESK, "Main Desk", DESK_ADMIN));
    identityRepo.addUser(new User(FIRM, DESK, USER, "Trader One", "trader@firm-a.example.com"));

    permStore = new InMemoryTagPermissionStore();
    permEvaluator = new TagPermissionEvaluator(permStore, identityRepo);
    pipeline = new LayeredValidatorPipeline(aaaService, null, permEvaluator);
  }

  // ── EMS-PRM-1001: user missing tag ───────────────────────────────────────

  @Test
  void prm1001_message_mentionsUserAndTag() {
    // All firm/desk grants present, but user has no tags
    permStore.grantFirmTag(FIRM, TAG);
    permStore.grantDeskTag(FIRM, DESK, TAG);
    // Register user without the tag
    aaaService.registerCredential("tok-notag", FIRM, DESK, "notag-user", Set.of());
    identityRepo.addUser(
        new User(FIRM, DESK, "notag-user", "No Tag User", "notag@firm-a.example.com"));
    long sessionId = logon("tok-notag");

    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertEquals("EMS-PRM-1001", reject.code());
    assertEquals(ValidationLayer.PERMISSION, reject.layer());
    assertTrue(
        reject.message().contains("notag-user"),
        "Message should name the user; got: " + reject.message());
    assertTrue(
        reject.message().contains(TAG), "Message should name the tag; got: " + reject.message());
  }

  @Test
  void prm1001_adminHint_mentionsTag() {
    permStore.grantFirmTag(FIRM, TAG);
    permStore.grantDeskTag(FIRM, DESK, TAG);
    aaaService.registerCredential("tok-notag2", FIRM, DESK, "notag2", Set.of());
    identityRepo.addUser(new User(FIRM, DESK, "notag2", "No Tag 2", "notag2@firm-a.example.com"));
    long sessionId = logon("tok-notag2");

    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertNotNull(reject.adminHint(), "adminHint must not be null for PRM-1001");
    assertTrue(
        reject.adminHint().contains(TAG),
        "Admin hint should reference the missing tag; got: " + reject.adminHint());
    assertTrue(
        reject.adminHint().startsWith("Talk to"),
        "Admin hint should start with 'Talk to'; got: " + reject.adminHint());
  }

  // ── EMS-PRM-1002: desk not granted ───────────────────────────────────────

  @Test
  void prm1002_message_mentionsUserDeskAndTag() {
    permStore.grantFirmTag(FIRM, TAG);
    // Desk grant deliberately omitted
    long sessionId = logon(TOKEN);

    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertEquals("EMS-PRM-1002", reject.code());
    assertEquals(ValidationLayer.PERMISSION, reject.layer());
    assertTrue(
        reject.message().contains(TAG), "Message should name the tag; got: " + reject.message());
    assertTrue(
        reject.message().contains(DESK), "Message should name the desk; got: " + reject.message());
  }

  @Test
  void prm1002_adminHint_containsDeskAdminContact() {
    permStore.grantFirmTag(FIRM, TAG);
    long sessionId = logon(TOKEN);

    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertNotNull(reject.adminHint(), "adminHint must not be null for PRM-1002");
    assertTrue(
        reject.adminHint().contains(DESK_ADMIN),
        "Admin hint should contain desk admin contact '"
            + DESK_ADMIN
            + "'; got: "
            + reject.adminHint());
    assertTrue(
        reject.adminHint().startsWith("Talk to"),
        "Admin hint should start with 'Talk to'; got: " + reject.adminHint());
  }

  @Test
  void prm1002_adminHint_fallbackWhenDeskNotInRepo() {
    InMemoryIdentityRepository emptyRepo = new InMemoryIdentityRepository();
    TagPermissionEvaluator evaluatorNoRepo = new TagPermissionEvaluator(permStore, emptyRepo);
    LayeredValidatorPipeline pipelineNoRepo =
        new LayeredValidatorPipeline(aaaService, null, evaluatorNoRepo);

    permStore.grantFirmTag(FIRM, TAG);
    long sessionId = logon(TOKEN);

    ValidationResult.Reject reject = expectReject(pipelineNoRepo.validate(request(sessionId, TAG)));
    assertEquals("EMS-PRM-1002", reject.code());
    assertNotNull(reject.adminHint(), "fallback adminHint must not be null");
    assertTrue(
        reject.adminHint().startsWith("Talk to"),
        "Fallback hint should still start with 'Talk to'; got: " + reject.adminHint());
  }

  // ── EMS-PRM-1003: firm not granted ───────────────────────────────────────

  @Test
  void prm1003_message_mentionsFirmAndTag() {
    // No firm grant
    long sessionId = logon(TOKEN);

    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertEquals("EMS-PRM-1003", reject.code());
    assertEquals(ValidationLayer.PERMISSION, reject.layer());
    assertTrue(
        reject.message().contains(FIRM), "Message should name the firm; got: " + reject.message());
    assertTrue(
        reject.message().contains(TAG), "Message should name the tag; got: " + reject.message());
  }

  @Test
  void prm1003_adminHint_containsFirmAdminContact() {
    long sessionId = logon(TOKEN);

    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertNotNull(reject.adminHint(), "adminHint must not be null for PRM-1003");
    assertTrue(
        reject.adminHint().contains(FIRM_ADMIN),
        "Admin hint should contain firm admin contact '"
            + FIRM_ADMIN
            + "'; got: "
            + reject.adminHint());
    assertTrue(
        reject.adminHint().startsWith("Talk to"),
        "Admin hint should start with 'Talk to'; got: " + reject.adminHint());
  }

  @Test
  void prm1003_adminHint_fallbackWhenFirmNotInRepo() {
    InMemoryIdentityRepository emptyRepo = new InMemoryIdentityRepository();
    TagPermissionEvaluator evaluatorNoRepo = new TagPermissionEvaluator(permStore, emptyRepo);
    LayeredValidatorPipeline pipelineNoRepo =
        new LayeredValidatorPipeline(aaaService, null, evaluatorNoRepo);

    long sessionId = logon(TOKEN);

    ValidationResult.Reject reject = expectReject(pipelineNoRepo.validate(request(sessionId, TAG)));
    assertEquals("EMS-PRM-1003", reject.code());
    assertNotNull(reject.adminHint(), "fallback adminHint must not be null");
    assertTrue(
        reject.adminHint().startsWith("Talk to"),
        "Fallback hint should still start with 'Talk to'; got: " + reject.adminHint());
  }

  // ── Category and layer invariants ─────────────────────────────────────────

  @Test
  void allPrmRejects_categoryIsPrm() {
    long sessionId = logon(TOKEN);
    // Triggers PRM-1003 (firm not granted)
    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertEquals("PRM", reject.category());
  }

  @Test
  void allPrmRejects_layerIsPermission() {
    long sessionId = logon(TOKEN);
    ValidationResult.Reject reject = expectReject(pipeline.validate(request(sessionId, TAG)));
    assertEquals(ValidationLayer.PERMISSION, reject.layer());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private long logon(String token) {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, token)));
    return accepted.session().sessionId();
  }

  private ValidationRequest request(long sessionId, String tag) {
    return new ValidationRequest("req-perm", sessionId, tag, null);
  }

  private ValidationResult.Reject expectReject(ValidationResult result) {
    return assertInstanceOf(
        ValidationResult.Reject.class,
        result,
        "Expected Reject but got: " + result.getClass().getSimpleName());
  }
}
