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
import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.Fungibility;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.instrument.InstrumentCore;
import io.crossasset.ems.instrument.InstrumentType;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.instrument.SecurityMasterEvent;
import io.crossasset.ems.instrument.SecurityMasterSnapshot;
import io.crossasset.ems.instrument.SettlementConvention;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LayeredValidatorPipeline}. Covers all eight layers; layers 5–8 are stubs but are
 * exercised by the all-pass golden path. Per arch-validator.md.
 *
 * <p>Task 6.2 — Layered evaluation pipeline.
 */
class LayeredValidatorPipelineTest {

  private static final String FIRM = "ACME";
  private static final String DESK = "EQ";
  private static final String USER = "alice";
  private static final String TOKEN = "tok-alice";
  private static final String TAG = "equity-trader";
  private static final String FIGI = "BBG000BLNQ16";

  private InMemoryAaaService aaaService;
  private InMemorySecurityMasterService secMaster;
  private InMemoryTagPermissionStore permStore;
  private InMemoryIdentityRepository identityRepo;
  private TagPermissionEvaluator permEvaluator;
  private LayeredValidatorPipeline pipeline;

  @BeforeEach
  void setUp() {
    aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaaService.registerCredential(TOKEN, FIRM, DESK, USER, Set.of(TAG));

    secMaster = new InMemorySecurityMasterService();

    identityRepo = new InMemoryIdentityRepository();
    identityRepo.addFirm(new Firm(FIRM, "Acme Corp", "firm-admin@acme.com"));
    identityRepo.addDesk(new Desk(FIRM, DESK, "EQ Desk", "desk-admin@acme.com"));
    identityRepo.addUser(new User(FIRM, DESK, USER, "Alice Smith", "alice@acme.com"));

    permStore = new InMemoryTagPermissionStore();
    permStore.grantFirmTag(FIRM, TAG);
    permStore.grantDeskTag(FIRM, DESK, TAG);

    permEvaluator = new TagPermissionEvaluator(permStore, identityRepo);
    pipeline = new LayeredValidatorPipeline(aaaService, secMaster, permEvaluator);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private long logon() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, TOKEN)));
    return accepted.session().sessionId();
  }

  private void publishActiveInstrument(String figi) {
    InstrumentCore core =
        new InstrumentCore(
            figi,
            "IID-001",
            null,
            null,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            "Test Stock",
            "Test Stock Inc.",
            null,
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.ACTIVE,
            1_000_000L,
            Long.MAX_VALUE,
            1L,
            null,
            1_000_000L,
            1_000_000L);
    SecurityMasterSnapshot snap =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 1L));
    secMaster.publish(snap);
  }

  private void publishInactiveInstrument(String figi) {
    InstrumentCore core =
        new InstrumentCore(
            figi,
            "IID-002",
            null,
            null,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            "Expired Stock",
            "Expired Inc.",
            null,
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.EXPIRED,
            1_000_000L,
            2_000_000L,
            1L,
            null,
            1_000_000L,
            2_000_000L);
    SecurityMasterSnapshot snap =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 1L));
    secMaster.publish(snap);
  }

  private ValidationRequest req(long sessionId, String tag, String figi) {
    return new ValidationRequest("req-001", sessionId, tag, figi);
  }

  // ── Layer 1: SESSION ─────────────────────────────────────────────────────

  @Test
  void session_unknownSessionId_rejectsEmsSes1002() {
    ValidationResult result = pipeline.validate(req(Long.MAX_VALUE, null, null));
    assertInstanceOf(ValidationResult.Reject.class, result);
    ValidationResult.Reject reject = (ValidationResult.Reject) result;
    assertEquals("EMS-SES-1002", reject.code());
    assertEquals("SES", reject.category());
    assertEquals(ValidationLayer.SESSION, reject.layer());
    assertNotNull(reject.message());
    assertNotNull(reject.adminHint());
  }

  @Test
  void session_loggedOutSession_rejectsEmsSes1002() {
    long sessionId = logon();
    aaaService.logout(sessionId, "manual logout");
    ValidationResult result = pipeline.validate(req(sessionId, null, null));
    assertInstanceOf(ValidationResult.Reject.class, result);
    assertEquals("EMS-SES-1002", ((ValidationResult.Reject) result).code());
  }

  @Test
  void session_validSession_passesLayer1() {
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, null, null));
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  @Test
  void session_reject_requestIdPreserved() {
    ValidationRequest request = new ValidationRequest("req-xyz", Long.MAX_VALUE, null, null);
    ValidationResult result = pipeline.validate(request);
    assertEquals("req-xyz", result.requestId());
  }

  // ── Layer 2: IDENTITY (pass-through) ──────────────────────────────────────

  @Test
  void identity_validSession_neverRejectsAtIdentityLayer() {
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, null, null));
    // Identity is pass-through; any accept from session should reach Pass
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  // ── Layer 3: REFERENCE ───────────────────────────────────────────────────

  @Test
  void reference_nullFigi_skipsLayer() {
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, null, null));
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  @Test
  void reference_unknownFigi_rejectsEmsRef2001() {
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, null, "BBG_UNKNOWN"));
    assertInstanceOf(ValidationResult.Reject.class, result);
    ValidationResult.Reject reject = (ValidationResult.Reject) result;
    assertEquals("EMS-REF-2001", reject.code());
    assertEquals("REF", reject.category());
    assertEquals(ValidationLayer.REFERENCE, reject.layer());
    assertTrue(reject.message().contains("BBG_UNKNOWN"));
  }

  @Test
  void reference_activeFigi_passesLayer() {
    publishActiveInstrument(FIGI);
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, null, FIGI));
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  @Test
  void reference_inactiveFigi_rejectsEmsRef2002() {
    publishInactiveInstrument(FIGI);
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, null, FIGI));
    assertInstanceOf(ValidationResult.Reject.class, result);
    ValidationResult.Reject reject = (ValidationResult.Reject) result;
    assertEquals("EMS-REF-2002", reject.code());
    assertEquals("REF", reject.category());
    assertEquals(ValidationLayer.REFERENCE, reject.layer());
    assertTrue(reject.message().contains(FIGI));
  }

  @Test
  void reference_nullSecMaster_skipsLayer() {
    LayeredValidatorPipeline minimalPipeline = new LayeredValidatorPipeline(aaaService);
    long sessionId = logon();
    // FIGI provided but no secMaster wired — should still pass
    ValidationResult result = minimalPipeline.validate(req(sessionId, null, FIGI));
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  // ── Layer 4: PERMISSION ──────────────────────────────────────────────────

  @Test
  void permission_nullTag_skipsLayer() {
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, null, null));
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  @Test
  void permission_userHasTag_firmAndDeskGranted_passes() {
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, TAG, null));
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  @Test
  void permission_userMissingTag_rejectsEmsPrm1001() {
    // Alice's session has TAG; remove the user tag by creating a new user without it
    aaaService.registerCredential("tok-bob", FIRM, DESK, "bob", Set.of());
    identityRepo.addUser(new User(FIRM, DESK, "bob", "Bob Smith", "bob@acme.com"));
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-bob")));
    long bobSession = accepted.session().sessionId();

    ValidationResult result = pipeline.validate(req(bobSession, TAG, null));
    assertInstanceOf(ValidationResult.Reject.class, result);
    ValidationResult.Reject reject = (ValidationResult.Reject) result;
    assertEquals("EMS-PRM-1001", reject.code());
    assertEquals("PRM", reject.category());
    assertEquals(ValidationLayer.PERMISSION, reject.layer());
    assertNotNull(reject.adminHint());
    assertTrue(reject.adminHint().startsWith("Talk to"));
  }

  @Test
  void permission_deskNotGranted_rejectsEmsPrm1002() {
    // User has the tag but desk grant is removed
    permStore.revokeDeskTag(FIRM, DESK, TAG);
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, TAG, null));
    assertInstanceOf(ValidationResult.Reject.class, result);
    ValidationResult.Reject reject = (ValidationResult.Reject) result;
    assertEquals("EMS-PRM-1002", reject.code());
    assertEquals("PRM", reject.category());
    assertEquals(ValidationLayer.PERMISSION, reject.layer());
    assertTrue(reject.adminHint().startsWith("Talk to"));
  }

  @Test
  void permission_firmNotGranted_rejectsEmsPrm1003() {
    permStore.revokeFirmTag(FIRM, TAG);
    long sessionId = logon();
    ValidationResult result = pipeline.validate(req(sessionId, TAG, null));
    assertInstanceOf(ValidationResult.Reject.class, result);
    ValidationResult.Reject reject = (ValidationResult.Reject) result;
    assertEquals("EMS-PRM-1003", reject.code());
    assertEquals("PRM", reject.category());
    assertEquals(ValidationLayer.PERMISSION, reject.layer());
    assertTrue(reject.adminHint().startsWith("Talk to"));
  }

  @Test
  void permission_nullEvaluator_skipsLayer() {
    LayeredValidatorPipeline noPerm = new LayeredValidatorPipeline(aaaService, secMaster, null);
    long sessionId = logon();
    // Tag provided but no evaluator — should still pass
    ValidationResult result = noPerm.validate(req(sessionId, TAG, null));
    assertInstanceOf(ValidationResult.Pass.class, result);
  }

  // ── Layer ordering (short-circuit) ───────────────────────────────────────

  @Test
  void ordering_sessionFails_refAndPermNotEvaluated() {
    // Unknown session with a FIGI that doesn't exist and a missing tag
    // — should report SESSION failure, not REF or PRM
    ValidationResult result =
        pipeline.validate(new ValidationRequest("req-001", Long.MAX_VALUE, TAG, "BBG_UNKNOWN"));
    ValidationResult.Reject reject = assertInstanceOf(ValidationResult.Reject.class, result);
    assertEquals(ValidationLayer.SESSION, reject.layer());
    assertEquals("EMS-SES-1002", reject.code());
  }

  @Test
  void ordering_refFails_permNotEvaluated() {
    // Valid session, unknown FIGI, missing perm — should report REF failure
    long sessionId = logon();
    permStore.revokeFirmTag(FIRM, TAG); // would trigger PRM-1003 if perm layer ran
    ValidationResult result =
        pipeline.validate(new ValidationRequest("req-001", sessionId, TAG, "BBG_UNKNOWN"));
    ValidationResult.Reject reject = assertInstanceOf(ValidationResult.Reject.class, result);
    assertEquals(ValidationLayer.REFERENCE, reject.layer());
    assertEquals("EMS-REF-2001", reject.code());
  }

  // ── Golden path (all layers pass) ────────────────────────────────────────

  @Test
  void goldenPath_allLayersPass_returnsPass() {
    publishActiveInstrument(FIGI);
    long sessionId = logon();
    ValidationResult result =
        pipeline.validate(new ValidationRequest("req-golden", sessionId, TAG, FIGI));
    assertInstanceOf(ValidationResult.Pass.class, result);
    assertEquals("req-golden", result.requestId());
  }

  // ── Pass requestId propagation ────────────────────────────────────────────

  @Test
  void pass_requestIdPropagated() {
    long sessionId = logon();
    ValidationResult result =
        new LayeredValidatorPipeline(aaaService)
            .validate(new ValidationRequest("my-req-id", sessionId, null, null));
    assertEquals("my-req-id", result.requestId());
  }
}
