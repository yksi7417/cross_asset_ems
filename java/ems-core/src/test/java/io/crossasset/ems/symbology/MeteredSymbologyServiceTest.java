/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the license-metering decorator.
 *
 * <h3>Discriminating tests</h3>
 *
 * <ul>
 *   <li>{@code unlicensedFirm_isin_deniedWithEmsRef1001} — an unlicensed firm gets the exact error
 *       code {@code EMS-REF-1001} for an ISIN lookup; proves the deny branch fires.
 *   <li>{@code deniedAccess_isRecordedInAuditLog} — denied attempts land in the audit log even
 *       though they are not billed; proves the audit-vs-billing split.
 *   <li>{@code deniedAccess_isNotBilled} — billing meter stays at zero after a denial; proves
 *       dispute-safe billing.
 *   <li>{@code mixedBatch_perItemLicense_partialGrantDeny} — a single batch with two items where
 *       only one is licensed; proves items are evaluated independently.
 * </ul>
 *
 * <p>Task 4.2 — license-metering and audit.
 */
class MeteredSymbologyServiceTest {

  private static final String FIRM_LICENSED = "firm-alpha";
  private static final String FIRM_UNLICENSED = "firm-beta";

  // Apple ISIN and CUSIP seeded in SimpleSymbologyService
  private static final String APPLE_ISIN = "US0378331005";
  private static final String APPLE_CUSIP = "037833100";
  private static final String APPLE_FIGI = "BBG000B9XRX4";

  private InMemoryLicenseRegistry registry;
  private InMemoryAccessAuditLog auditLog;
  private InMemoryBillingMeter billingMeter;
  private MeteredSymbologyService svc;

  @BeforeEach
  void setUp() {
    registry = new InMemoryLicenseRegistry();
    auditLog = new InMemoryAccessAuditLog();
    billingMeter = new InMemoryBillingMeter();
    svc =
        new MeteredSymbologyService(new SimpleSymbologyService(), registry, auditLog, billingMeter);

    registry.grant(FIRM_LICENSED, SymbologyService.IdType.ID_ISIN);
    registry.grant(FIRM_LICENSED, SymbologyService.IdType.ID_CUSIP);
    registry.grant(FIRM_LICENSED, SymbologyService.IdType.ID_SEDOL);
  }

  private SymbologyService.ResolveRequest requestFor(
      String identity, SymbologyService.ResolveItem... items) {
    return new SymbologyService.ResolveRequest(UUID.randomUUID(), 1L, identity, List.of(items));
  }

  private SymbologyService.ResolveItem isinItem(String value) {
    return new SymbologyService.ResolveItem(
        SymbologyService.IdType.ID_ISIN, value, null, null, null);
  }

  private SymbologyService.ResolveItem figiItem(String value) {
    return new SymbologyService.ResolveItem(
        SymbologyService.IdType.ID_BB_GLOBAL, value, null, null, null);
  }

  private SymbologyService.ResolveItem tickerItem(String ticker, String mic) {
    return new SymbologyService.ResolveItem(
        SymbologyService.IdType.ID_TICKER, ticker, mic, null, null);
  }

  private SymbologyService.ResolveItem cusipItem(String value) {
    return new SymbologyService.ResolveItem(
        SymbologyService.IdType.ID_CUSIP, value, null, null, null);
  }

  // ── FIGI: always allowed ──────────────────────────────────────────────────

  @Test
  void figi_alwaysAllowed_noLicenseRequired() {
    var results = svc.resolve(requestFor(FIRM_UNLICENSED, figiItem(APPLE_FIGI)));

    assertEquals(1, results.size());
    assertTrue(results.get(0).errorCode().isEmpty(), "FIGI resolution must not require a license");
    assertEquals("BBG000B9XRX4", results.get(0).figi().orElseThrow());
  }

  // ── TICKER: always allowed ─────────────────────────────────────────────────

  @Test
  void ticker_alwaysAllowed_noLicenseRequired() {
    var results = svc.resolve(requestFor(FIRM_UNLICENSED, tickerItem("AAPL", "XNAS")));

    assertEquals(1, results.size());
    assertTrue(
        results.get(0).errorCode().isEmpty(), "Ticker resolution must not require a license");
    assertEquals("AAPL", results.get(0).ticker().orElseThrow());
  }

  // ── ISIN: licensed firm succeeds ──────────────────────────────────────────

  @Test
  void licensedFirm_isin_resolves() {
    var results = svc.resolve(requestFor(FIRM_LICENSED, isinItem(APPLE_ISIN)));

    assertEquals(1, results.size());
    assertTrue(results.get(0).errorCode().isEmpty(), "Licensed firm must receive ISIN resolution");
    assertEquals(APPLE_FIGI, results.get(0).figi().orElseThrow());
  }

  // ── ISIN: unlicensed firm denied ──────────────────────────────────────────

  /**
   * Discriminating test: unlicensed firm gets exactly {@code EMS-REF-1001}.
   *
   * <p>If the deny branch is wired, this returns the error code. If it falls through to the
   * delegate, it returns the data — the test catches both implementation gaps.
   */
  @Test
  void unlicensedFirm_isin_deniedWithEmsRef1001() {
    var results = svc.resolve(requestFor(FIRM_UNLICENSED, isinItem(APPLE_ISIN)));

    assertEquals(1, results.size());
    var result = results.get(0);
    assertEquals("EMS-REF-1001", result.errorCode().orElseThrow());
    assertEquals("License denied", result.errorMessage().orElseThrow());
    assertTrue(result.figi().isEmpty(), "Denied result must not leak FIGI");
    assertTrue(result.name().isEmpty(), "Denied result must not leak instrument name");
  }

  // ── Audit: denied access is recorded ──────────────────────────────────────

  /**
   * Discriminating test: denied access lands in the audit log.
   *
   * <p>The audit log captures licensing probes even when no data is returned. If the decorator
   * skips audit on denial, the log stays empty and the assertion fails.
   */
  @Test
  void deniedAccess_isRecordedInAuditLog() {
    svc.resolve(requestFor(FIRM_UNLICENSED, isinItem(APPLE_ISIN)));

    List<AccessRecord> entries = auditLog.entries(FIRM_UNLICENSED);
    assertEquals(1, entries.size(), "Denied access must produce one audit record");
    AccessRecord record = entries.get(0);
    assertEquals(FIRM_UNLICENSED, record.identity());
    assertEquals(SymbologyService.IdType.ID_ISIN, record.idType());
    assertEquals(APPLE_ISIN, record.value());
    assertEquals(AccessOutcome.DENIED, record.outcome());
  }

  // ── Audit: granted access is also recorded ────────────────────────────────

  @Test
  void grantedAccess_isRecordedInAuditLog() {
    svc.resolve(requestFor(FIRM_LICENSED, isinItem(APPLE_ISIN)));

    List<AccessRecord> entries = auditLog.entries(FIRM_LICENSED);
    assertEquals(1, entries.size());
    assertEquals(AccessOutcome.GRANTED, entries.get(0).outcome());
  }

  // ── Billing: successful access is metered ─────────────────────────────────

  @Test
  void successfulAccess_isBilledToMeter() {
    svc.resolve(requestFor(FIRM_LICENSED, isinItem(APPLE_ISIN)));

    assertEquals(
        1L,
        billingMeter.successCount(FIRM_LICENSED, SymbologyService.IdType.ID_ISIN),
        "Successful ISIN access must increment the billing counter");
  }

  // ── Billing: denied access is NOT billed ──────────────────────────────────

  /**
   * Discriminating test: billing meter is not incremented on denial.
   *
   * <p>Firms must not be invoiced for access they were denied. If the decorator bills before
   * checking the license, this counter is 1 and the test fails.
   */
  @Test
  void deniedAccess_isNotBilled() {
    svc.resolve(requestFor(FIRM_UNLICENSED, isinItem(APPLE_ISIN)));

    assertEquals(
        0L,
        billingMeter.successCount(FIRM_UNLICENSED, SymbologyService.IdType.ID_ISIN),
        "Denied access must not increment the billing counter");
  }

  // ── Mixed batch: per-item evaluation ──────────────────────────────────────

  /**
   * Discriminating test: items in the same batch are evaluated independently.
   *
   * <p>A firm licensed for ISIN but not for CUSIP in the same batch request: ISIN resolves, CUSIP
   * is denied. Proves items are not all-or-nothing.
   */
  @Test
  void mixedBatch_perItemLicense_partialGrantDeny() {
    registry.grant(FIRM_UNLICENSED, SymbologyService.IdType.ID_ISIN); // only ISIN, not CUSIP

    var results =
        svc.resolve(requestFor(FIRM_UNLICENSED, isinItem(APPLE_ISIN), cusipItem(APPLE_CUSIP)));

    assertEquals(2, results.size());
    // First item: ISIN — licensed
    assertTrue(results.get(0).errorCode().isEmpty(), "ISIN item must succeed for licensed firm");
    assertEquals(APPLE_FIGI, results.get(0).figi().orElseThrow());
    // Second item: CUSIP — not licensed
    assertEquals("EMS-REF-1001", results.get(1).errorCode().orElseThrow());

    // Billing: ISIN counted, CUSIP not
    assertEquals(1L, billingMeter.successCount(FIRM_UNLICENSED, SymbologyService.IdType.ID_ISIN));
    assertEquals(0L, billingMeter.successCount(FIRM_UNLICENSED, SymbologyService.IdType.ID_CUSIP));
  }

  // ── Not-found instrument: no billing ──────────────────────────────────────

  @Test
  void licensedFirm_unknownIsin_notFoundNotBilled() {
    var results = svc.resolve(requestFor(FIRM_LICENSED, isinItem("XX0000000000")));

    assertEquals(1, results.size());
    assertEquals("EMS-REF-1001", results.get(0).errorCode().orElseThrow());
    assertEquals(
        0L,
        billingMeter.successCount(FIRM_LICENSED, SymbologyService.IdType.ID_ISIN),
        "Not-found must not be billed");
  }
}
